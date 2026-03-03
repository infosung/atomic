package com.infosung.atomic.spring.idempotency

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

/**
 * Response wrapper that mirrors output to client and captures at most [maxCaptureBytes] bytes.
 *
 * This avoids buffering arbitrarily large payloads in memory when idempotency replay capture is
 * enabled.
 */
internal class BoundedBodyCaptureResponseWrapper(
    response: HttpServletResponse,
    private val maxCaptureBytes: Int,
) : HttpServletResponseWrapper(response) {
  private val captured = ByteArrayOutputStream(maxCaptureBytes.coerceAtLeast(0))
  private var omittedForSizeLimit = false
  private var servletOutputStream: ServletOutputStream? = null
  private var writer: PrintWriter? = null
  private var outputStreamUsed = false
  private var writerUsed = false

  override fun getOutputStream(): ServletOutputStream {
    check(!writerUsed || outputStreamUsed) {
      "getWriter() has already been called for this response."
    }
    outputStreamUsed = true
    if (servletOutputStream == null) {
      val delegate = response.outputStream
      servletOutputStream =
          object : ServletOutputStream() {
            override fun write(b: Int) {
              delegate.write(b)
              capture(byteArrayOf(b.toByte()), 0, 1)
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
              delegate.write(b, off, len)
              capture(b, off, len)
            }

            override fun isReady(): Boolean = delegate.isReady

            override fun setWriteListener(listener: WriteListener?) {
              delegate.setWriteListener(listener)
            }

            override fun flush() {
              delegate.flush()
            }

            override fun close() {
              delegate.close()
            }
          }
    }
    return servletOutputStream!!
  }

  override fun getWriter(): PrintWriter {
    check(!outputStreamUsed || writerUsed) {
      "getOutputStream() has already been called for this response."
    }
    if (writer == null) {
      val charsetName =
          characterEncoding?.takeIf { it.isNotBlank() } ?: StandardCharsets.UTF_8.name()
      writer = PrintWriter(OutputStreamWriter(outputStream, charsetName), true)
    }
    writerUsed = true
    return writer!!
  }

  override fun flushBuffer() {
    writer?.flush()
    servletOutputStream?.flush()
    super.flushBuffer()
  }

  fun capturedBody(): ByteArray {
    writer?.flush()
    return captured.toByteArray()
  }

  fun capturedBodyBytes(): Int = captured.size()

  fun isBodyOmittedForSizeLimit(): Boolean = omittedForSizeLimit

  private fun capture(
      bytes: ByteArray,
      off: Int,
      len: Int,
  ) {
    if (len <= 0) {
      return
    }
    if (maxCaptureBytes <= 0) {
      omittedForSizeLimit = true
      return
    }

    val remaining = maxCaptureBytes - captured.size()
    if (remaining <= 0) {
      omittedForSizeLimit = true
      return
    }

    val copyLength = minOf(remaining, len)
    captured.write(bytes, off, copyLength)
    if (copyLength < len) {
      omittedForSizeLimit = true
    }
  }
}
