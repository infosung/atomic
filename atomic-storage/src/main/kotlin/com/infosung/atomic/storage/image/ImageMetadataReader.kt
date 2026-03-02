package com.infosung.atomic.storage.image

import java.io.File
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants

/** Reads image metadata from local files. */
fun interface ImageMetadataReader {
  /**
   * Reads width/height/file-size information from [file].
   *
   * @throws IllegalArgumentException or runtime exceptions from underlying image libraries when the
   *   file is not readable as an image.
   */
  fun read(file: File): ImageMetadata
}

/**
 * [ImageMetadataReader] implementation backed by Apache Commons Imaging.
 *
 * Width and height are normalized using EXIF orientation when available.
 */
class CommonsImagingMetadataReader : ImageMetadataReader {
  override fun read(file: File): ImageMetadata {
    val info = Imaging.getImageInfo(file)
    val orientation = readExifOrientation(file)
    val shouldSwapDimensions =
        orientation == TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_270_CW ||
            orientation == TiffTagConstants.ORIENTATION_VALUE_ROTATE_90_CW ||
            orientation == TiffTagConstants.ORIENTATION_VALUE_MIRROR_HORIZONTAL_AND_ROTATE_90_CW ||
            orientation == TiffTagConstants.ORIENTATION_VALUE_ROTATE_270_CW
    val width = if (shouldSwapDimensions) info.height else info.width
    val height = if (shouldSwapDimensions) info.width else info.height
    return ImageMetadata(
        width = width,
        height = height,
        size = file.length(),
    )
  }

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
}
