package com.infosung.atomic.storage.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet

class CommonsImagingMetadataReaderTest {
  private val metadataReader = CommonsImagingMetadataReader()

  @Test
  fun `read should return image width height and file size`() {
    val file = createPng(width = 123, height = 45)

    try {
      val metadata = metadataReader.read(file)
      assertEquals(123, metadata.width)
      assertEquals(45, metadata.height)
      assertTrue(metadata.size > 0)
      assertEquals(file.length(), metadata.size)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `read should apply exif orientation to width and height`() {
    val file = createJpegWithExifOrientation(width = 120, height = 80, orientation = 6)

    try {
      val metadata = metadataReader.read(file)
      assertEquals(80, metadata.width)
      assertEquals(120, metadata.height)
      assertTrue(metadata.size > 0)
    } finally {
      file.delete()
    }
  }

  private fun createPng(
      width: Int,
      height: Int,
  ): File {
    val file = File.createTempFile("atomic-storage-meta-", ".png")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(100, 120, 140)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    ImageIO.write(image, "png", file)
    return file
  }

  private fun createJpegWithExifOrientation(
      width: Int,
      height: Int,
      orientation: Int,
  ): File {
    val sourceJpeg = File.createTempFile("atomic-storage-meta-", ".jpg")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(120, 100, 80)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    ImageIO.write(image, "jpg", sourceJpeg)

    val orientedJpeg = File.createTempFile("atomic-storage-meta-oriented-", ".jpg")
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
}
