package com.infosung.atomic.storage.image

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet

class DefaultImageThumbnailGeneratorTest {
  @Test
  fun `generate should create webp thumbnail with same key suffix`() {
    val source = createPng(width = 200, height = 100)
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.png",
            sourceObjectKey = "images/2026/02/25/sample.png",
            quality = 1.0,
        )

    try {
      assertEquals("images/2026/02/25/sample_thumb.webp", generated.objectKey)
      assertTrue(generated.file.exists())
      assertTrue(generated.metadata.width > 0)
      assertTrue(generated.metadata.height > 0)
      assertTrue(generated.metadata.size > 0)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should limit output size when requested scale is too large`() {
    val source = createPng(width = 8000, height = 4000)
    val generator =
        DefaultImageThumbnailGenerator(
            largeImageThresholdBytes = 1,
            maxOutputPixels = 1_000_000,
            maxOutputEdge = 2000,
        )

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.png",
            sourceObjectKey = "images/sample.png",
            quality = 1.0,
        )

    try {
      assertTrue(generated.metadata.width <= 2000)
      assertTrue(generated.metadata.height <= 2000)
      assertTrue(
          generated.metadata.width.toLong() * generated.metadata.height.toLong() <= 1_000_000)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should reject invalid quality`() {
    val source = createPng(width = 200, height = 100)
    val generator = DefaultImageThumbnailGenerator()

    try {
      assertFailsWith<IllegalArgumentException> {
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.png",
            sourceObjectKey = "images/sample.png",
            quality = 0.0,
        )
      }
      assertFailsWith<IllegalArgumentException> {
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.png",
            sourceObjectKey = "images/sample.png",
            quality = 1.5,
        )
      }
    } finally {
      source.delete()
    }
  }

  @Test
  fun `generate should keep key when source object key has no extension`() {
    val source = createPng(width = 200, height = 100)
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample",
            sourceObjectKey = "images/no-ext-key",
            quality = 0.8,
        )

    try {
      assertEquals("images/no-ext-key_thumb.webp", generated.objectKey)
      assertTrue(generated.file.exists())
      assertTrue(generated.metadata.width > 0)
      assertTrue(generated.metadata.height > 0)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should fallback to file extension when source filename has none`() {
    val source = createPng(width = 120, height = 80)
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "source",
            sourceObjectKey = "images/source.png",
            quality = 0.7,
        )

    try {
      assertEquals("images/source_thumb.webp", generated.objectKey)
      assertTrue(generated.file.exists())
      assertTrue(generated.metadata.size > 0)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should support webp source input`() {
    val source = createWebp(width = 160, height = 120)
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.webp",
            sourceObjectKey = "images/sample.webp",
            quality = 0.8,
        )

    try {
      assertEquals("images/sample_thumb.webp", generated.objectKey)
      assertTrue(generated.file.exists())
      assertTrue(generated.metadata.width > 0)
      assertTrue(generated.metadata.height > 0)
      assertTrue(generated.metadata.size > 0)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should keep full path when source key contains dots in directories`() {
    val source = createPng(width = 100, height = 50)
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.png",
            sourceObjectKey = "images/releases/v1.0.0/build",
            quality = 1.0,
        )

    try {
      assertEquals("images/releases/v1.0.0/build_thumb.webp", generated.objectKey)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  @Test
  fun `generate should apply exif orientation before thumbnail conversion`() {
    val source = createJpegWithExifOrientation(width = 120, height = 80, orientation = 6)
    assertEquals(6, readExifOrientation(source))
    val generator = DefaultImageThumbnailGenerator()

    val generated =
        generator.generate(
            sourceFile = source,
            sourceFilename = "sample.jpg",
            sourceObjectKey = "images/sample.jpg",
            quality = 1.0,
        )

    try {
      assertEquals("images/sample_thumb.webp", generated.objectKey)
      assertEquals(80, generated.metadata.width)
      assertEquals(120, generated.metadata.height)
    } finally {
      source.delete()
      generated.file.delete()
    }
  }

  private fun createPng(
      width: Int,
      height: Int,
  ): File {
    val file = File.createTempFile("atomic-storage-source-", ".png")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(10, 20, 30)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    ImageIO.write(image, "png", file)
    return file
  }

  private fun createWebp(
      width: Int,
      height: Int,
  ): File {
    val pngSource = createPng(width = width, height = height)
    val webpFile = File.createTempFile("atomic-storage-source-", ".webp")
    try {
      ImmutableImage.loader().fromFile(pngSource).output(WebpWriter.DEFAULT, webpFile)
      return webpFile
    } finally {
      pngSource.delete()
    }
  }

  private fun createJpegWithExifOrientation(
      width: Int,
      height: Int,
      orientation: Int,
  ): File {
    val sourceJpeg = File.createTempFile("atomic-storage-source-", ".jpg")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(255, 0, 0)
    graphics.fillRect(0, 0, width / 2, height / 2)
    graphics.color = Color(0, 255, 0)
    graphics.fillRect(width / 2, 0, width / 2, height / 2)
    graphics.color = Color(0, 0, 255)
    graphics.fillRect(0, height / 2, width / 2, height / 2)
    graphics.dispose()
    ImageIO.write(image, "jpg", sourceJpeg)

    val orientedJpeg = File.createTempFile("atomic-storage-source-oriented-", ".jpg")
    val metadata = Imaging.getMetadata(sourceJpeg) as? JpegImageMetadata
    val outputSet = metadata?.exif?.outputSet ?: TiffOutputSet()
    val rootDirectory = outputSet.getOrCreateRootDirectory()
    rootDirectory.removeField(TiffTagConstants.TIFF_TAG_ORIENTATION)
    rootDirectory.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.toShort())
    FileOutputStream(orientedJpeg).use { output ->
      if (metadata?.exif != null) {
        ExifRewriter().updateExifMetadataLossless(sourceJpeg, output, outputSet)
      } else {
        ExifRewriter().updateExifMetadataLossy(sourceJpeg, output, outputSet)
      }
    }
    sourceJpeg.delete()
    return orientedJpeg
  }

  private fun readExifOrientation(file: File): Int? {
    val metadata = Imaging.getMetadata(file) as? JpegImageMetadata ?: return null
    return metadata.findExifValue(TiffTagConstants.TIFF_TAG_ORIENTATION)?.intValue
  }
}
