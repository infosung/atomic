package com.infosung.atomic.storage.image

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.sqrt
import net.coobird.thumbnailator.Thumbnails
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants

/**
 * Generated thumbnail artifact.
 *
 * @property objectKey Thumbnail object key.
 * @property file Generated local file to upload.
 * @property metadata Metadata of generated thumbnail file.
 */
data class GeneratedThumbnail(
    val objectKey: String,
    val file: File,
    val metadata: ImageMetadata,
)

/** Generates thumbnails for uploaded images. */
fun interface ImageThumbnailGenerator {
  /**
   * Generates thumbnail output from [sourceFile].
   *
   * @param sourceFile Source image file.
   * @param sourceFilename Source filename used for extension decisions.
   * @param sourceObjectKey Original object key used to derive thumbnail key.
   * @param quality Requested scale/quality value in range `0.1..1.0`.
   * @return Generated thumbnail file and metadata.
   * @throws IllegalArgumentException If quality is out of range.
   * @throws Exception If image decoding/resizing/encoding fails.
   */
  fun generate(
      sourceFile: File,
      sourceFilename: String,
      sourceObjectKey: String,
      quality: Double,
  ): GeneratedThumbnail
}

/**
 * Thumbnail generator for image uploads.
 *
 * Operational note for integrators:
 * - This module assumes upload limits are enforced by the application layer (for example, Spring
 *   multipart settings in each service).
 * - File-size limits alone may not be enough for image safety. Highly compressed images can still
 *   have very large pixel dimensions, which can increase memory usage during rasterization.
 * - If your service accepts untrusted uploads, configure strict upload policies (size, timeout,
 *   concurrency) and consider adding dimension/pixel-count validation before calling this module.
 */
class DefaultImageThumbnailGenerator(
    private val metadataReader: ImageMetadataReader = CommonsImagingMetadataReader(),
    private val largeImageThresholdBytes: Long = 1024 * 1024,
    private val chunkSizeBytes: Long = 1024 * 512,
    private val maxOutputPixels: Long = 16_777_216L,
    private val maxOutputEdge: Int = 4096,
) : ImageThumbnailGenerator {
  override fun generate(
      sourceFile: File,
      sourceFilename: String,
      sourceObjectKey: String,
      quality: Double,
  ): GeneratedThumbnail {
    require(quality in 0.1..1.0) { "quality must be in range 0.1..1.0" }

    val sourceExtension =
        extensionOf(sourceFilename).ifBlank { extensionOf(sourceFile.name).ifBlank { "jpg" } }
    val thumbnailObjectKey = "${removeExtension(sourceObjectKey)}_thumb.webp"
    val sourceMetadata = metadataReader.read(sourceFile)
    val effectiveScale = computeEffectiveScale(sourceMetadata, quality)
    val thumbnailFile = File.createTempFile("atomic-storage-thumb-", ".webp")
    val rasterFile = File.createTempFile("atomic-storage-raster-", ".png")

    try {
      if (shouldUseScrimageResize(sourceExtension) || needsExifOrientationCorrection(sourceFile)) {
        resizeImageWithScrimage(sourceFile, rasterFile, effectiveScale)
      } else if (sourceFile.length() >= largeImageThresholdBytes) {
        resizeImageWithChunk(sourceFile, rasterFile, sourceMetadata, effectiveScale)
      } else {
        resizeImageDirect(sourceFile, rasterFile, effectiveScale)
      }
      ImmutableImage.loader().fromFile(rasterFile).output(WebpWriter.DEFAULT, thumbnailFile)

      return GeneratedThumbnail(
          objectKey = thumbnailObjectKey,
          file = thumbnailFile,
          metadata = metadataReader.read(thumbnailFile),
      )
    } catch (e: Exception) {
      thumbnailFile.delete()
      throw e
    } finally {
      rasterFile.delete()
    }
  }

  private fun resizeImageDirect(
      sourceFile: File,
      rasterFile: File,
      scale: Double,
  ) {
    Thumbnails.of(sourceFile)
        .scale(scale)
        .outputQuality(scale)
        .outputFormat("png")
        .toFile(rasterFile)
  }

  private fun resizeImageWithScrimage(
      file: File,
      resizedFile: File,
      scale: Double,
  ) {
    val resized = ImmutableImage.loader().fromFile(file).scale(scale)
    resized.output(PngWriter.NoCompression, resizedFile)
  }

  private fun resizeImageWithChunk(
      file: File,
      resizedFile: File,
      metadata: ImageMetadata,
      scale: Double,
  ) {
    val width = metadata.width
    val height = metadata.height
    val outputWidth = maxOf(1, (width * scale).toInt())
    val outputHeight = maxOf(1, (height * scale).toInt())

    val estimatedChunkCount =
        maxOf(1, ((file.length() + chunkSizeBytes - 1) / chunkSizeBytes).toInt())
    val chunkCount = minOf(estimatedChunkCount, height)
    val chunkHeight = maxOf(1, ((height + chunkCount - 1) / chunkCount))
    var startHeight = 0

    val mergedImage =
        BufferedImage(
            outputWidth,
            outputHeight,
            bufferedImageType("png"),
        )
    val graphics = mergedImage.createGraphics()

    try {
      val imageInput = ImageIO.createImageInputStream(file)
      if (imageInput == null) {
        resizeImageDirect(file, resizedFile, scale)
        return
      }
      imageInput.use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) {
          resizeImageDirect(file, resizedFile, scale)
          return
        }
        val reader = readers.next()
        try {
          reader.input = input
          while (startHeight < height) {
            val currentChunkHeight = minOf(chunkHeight, height - startHeight)
            val param = reader.defaultReadParam
            param.sourceRegion = Rectangle(0, startHeight, width, currentChunkHeight)
            val chunkImage =
                reader.read(0, param)
                    ?: throw IOException("Failed to read source image chunk at y=$startHeight.")
            val outputStartY = maxOf(0, (startHeight * scale).toInt())
            val outputEndY =
                minOf(outputHeight, ((startHeight + currentChunkHeight) * scale).toInt())
            val targetChunkHeight = maxOf(1, outputEndY - outputStartY)
            graphics.drawImage(
                chunkImage,
                0,
                outputStartY,
                outputWidth,
                outputStartY + targetChunkHeight,
                0,
                0,
                chunkImage.width,
                chunkImage.height,
                null,
            )
            startHeight += currentChunkHeight
          }
        } finally {
          reader.dispose()
        }
      }
    } finally {
      graphics.dispose()
    }
    ImageIO.write(mergedImage, "png", resizedFile)
  }

  private fun bufferedImageType(fileExtension: String): Int {
    return when (fileExtension.lowercase()) {
      "png",
      "gif" -> BufferedImage.TYPE_INT_ARGB
      "bmp" -> BufferedImage.TYPE_3BYTE_BGR
      else -> BufferedImage.TYPE_INT_RGB
    }
  }

  private fun extensionOf(name: String): String = name.substringAfterLast('.', "")

  private fun shouldUseScrimageResize(sourceExtension: String): Boolean =
      sourceExtension.equals("webp", ignoreCase = true)

  private fun needsExifOrientationCorrection(file: File): Boolean =
      readExifOrientation(file) != TiffTagConstants.ORIENTATION_VALUE_HORIZONTAL_NORMAL

  private fun readExifOrientation(file: File): Int {
    val orientation =
        runCatching {
              when (val metadata = Imaging.getMetadata(file)) {
                is JpegImageMetadata -> {
                  metadata.findExifValue(TiffTagConstants.TIFF_TAG_ORIENTATION)?.intValue
                }

                is TiffImageMetadata -> {
                  metadata.findField(TiffTagConstants.TIFF_TAG_ORIENTATION)?.intValue
                }

                else -> null
              }
            }
            .getOrNull()
    return if (orientation != null && orientation in 1..8) orientation else 1
  }

  private fun removeExtension(name: String): String {
    val separatorIndex = maxOf(name.lastIndexOf('/'), name.lastIndexOf('\\'))
    val index = name.lastIndexOf('.')
    return if (index <= separatorIndex) name else name.take(index)
  }

  private fun computeEffectiveScale(
      sourceMetadata: ImageMetadata,
      requestedScale: Double,
  ): Double {
    // This method computes a conservative scale from target quality and soft output caps.
    // Upstream services are still expected to enforce upload constraints (for example, Spring
    // multipart limits) before handing files to this module.
    //
    // Important: this calculation is based on dimensions, not encoded file bytes. A small uploaded
    // file can still decode into a very large bitmap and consume significant memory.
    var scale = requestedScale
    var targetWidth = maxOf(1.0, sourceMetadata.width * scale)
    var targetHeight = maxOf(1.0, sourceMetadata.height * scale)

    val targetPixels = targetWidth * targetHeight
    if (targetPixels > maxOutputPixels) {
      val ratio = sqrt(maxOutputPixels / targetPixels)
      scale *= ratio
      targetWidth = maxOf(1.0, sourceMetadata.width * scale)
      targetHeight = maxOf(1.0, sourceMetadata.height * scale)
    }

    val maxEdge = maxOf(targetWidth, targetHeight)
    if (maxEdge > maxOutputEdge) {
      val ratio = maxOutputEdge / maxEdge
      scale *= ratio
    }

    return scale.coerceIn(0.1, 1.0)
  }
}
