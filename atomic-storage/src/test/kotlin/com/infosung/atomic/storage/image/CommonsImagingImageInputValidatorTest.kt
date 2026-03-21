package com.infosung.atomic.storage.image

import com.infosung.atomic.storage.image.spi.CommonsImagingImageInputValidator
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommonsImagingImageInputValidatorTest {
  private val validator = CommonsImagingImageInputValidator()

  @Test
  fun `validate should accept png image with png extension`() {
    val file = createPngFile()
    try {
      val result = validator.validate(file, "sample.png")
      assertEquals("png", result.extension)
      assertEquals("image/png", result.contentType)
      assertEquals("PNG", result.detectedFormat)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should reject unsupported extension`() {
    val file = createPngFile()
    try {
      assertFailsWith<IllegalArgumentException> { validator.validate(file, "sample.heic") }
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should reject extension format mismatch`() {
    val file = createPngFile()
    try {
      assertFailsWith<IllegalArgumentException> { validator.validate(file, "sample.jpg") }
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should accept uppercase extension`() {
    val file = createPngFile()
    try {
      val result = validator.validate(file, "SAMPLE.PNG")
      assertEquals("png", result.extension)
      assertEquals("image/png", result.contentType)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should reject filename without extension`() {
    val file = createPngFile()
    try {
      assertFailsWith<IllegalArgumentException> { validator.validate(file, "sample") }
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should reject non image binary even with allowed extension`() {
    val file = File.createTempFile("atomic-storage-validator-", ".bin")
    file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
    try {
      assertFailsWith<IllegalArgumentException> { validator.validate(file, "sample.png") }
    } finally {
      file.delete()
    }
  }

  @Test
  fun `validate should apply custom allowlist`() {
    val customValidator = CommonsImagingImageInputValidator(allowedExtensions = setOf("png"))
    val file = createPngFile()
    try {
      assertEquals("png", customValidator.validate(file, "sample.png").extension)
      assertFailsWith<IllegalArgumentException> { customValidator.validate(file, "sample.jpg") }
    } finally {
      file.delete()
    }
  }

  private fun createPngFile(): File {
    val file = File.createTempFile("atomic-storage-validator-", ".png")
    val image = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.color = Color(10, 40, 70)
    graphics.fillRect(0, 0, 40, 20)
    graphics.dispose()
    ImageIO.write(image, "png", file)
    return file
  }
}
