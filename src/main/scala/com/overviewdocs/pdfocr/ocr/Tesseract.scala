package com.overviewdocs.pdfocr.ocr

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream,IOException,InputStream,OutputStream}
import java.util.Locale
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.pdfocr.exceptions._

class Tesseract(val options: TesseractOptions) {
  private val BufferSize = 8192 // inspired by OpenJDK's Files.copy()
  private val MissingLanguagePattern = Pattern.compile("^Failed loading language '(\\w+)'$", Pattern.MULTILINE)

  /** Runs Tesseract on the given image, via stdin/stdout, producing hOCR data.
    *
    * The retval's `standardOutput` will contain the hOCR data.
    *
    * The Future may be a Failure, too, in the following cases:
    *
    * * `TesseractMissingException` if `options.tesseractPath` does not point to an executable
    * * `TesseractLanguageMissingException` if Tesseract responded that a language could not be loaded
    * * `TesseractUnknownException` if retval != 0 (for no predictable reason) -- `message` will contain stderr output
    */
  def ocr(image: BufferedImage, languages: Seq[Locale])(implicit ec: ExecutionContext): Future[TesseractResult] = {
    val imageBMP: Array[Byte] = imageToBMP(image) // What we'll give Tesseract

    for {
      process <- startTesseract(languages)                           // or TesseractMissingException
      (stdout, stderr) <- sendInputAndReadOutputs(process, imageBMP)
      retval <- Future(blocking(process.waitFor))
    } yield {
      val stderrString = new String(stderr, "utf-8")

      // Even if Tesseract succeeded, it may have output a warning about the
      // dummy "osd" language. We *need* this data, so throw an error.
      val matcher = MissingLanguagePattern.matcher(stderrString)
      if (matcher.find) {
        throw new TesseractLanguageMissingException(matcher.group(1))
      }

      if (retval != 0) {
        throw new TesseractFailedException(retval, stderrString)
      }

      TesseractResult(stdout, stderr)
    }
  }

  /** Blocking operation: read all of InputStream and write it to OutputStream.
    */
  private def drainStream(source: InputStream, sink: OutputStream): Unit = {
    val buffer = new Array[Byte](BufferSize)
    var n: Int = 0
    while (true) {
      val n = source.read(buffer)
      if (n > 0) {
        sink.write(buffer, 0, n)
      } else {
        sink.close
        return
      }
    }
  }

  /** Converts a BufferedImage to a BMP byte array.
    */
  private def imageToBMP(image: BufferedImage): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream
    val imageOutputStream = new MemoryCacheImageOutputStream(outputStream)
    ImageIO.write(image, "bmp", imageOutputStream)
    outputStream.toByteArray
  }

  /** Starts Tesseract and returns a Future[Process].
    *
    * May return a failed Future with a TesseractMissingException.
    */
  private def startTesseract(languages: Seq[Locale])(implicit ec: ExecutionContext): Future[Process] = {
    Future {
      // Wrap the whole thing in a Future, so exceptions become Failures
      try {
        val processBuilder = new ProcessBuilder(
          options.tesseractPath,
          "-", "-",
          "-l", languages.map(_.getISO3Language).mkString("+"), // Languages
          "-psm", "1",                                          // Page segmentation + orientation/script detection
          "hocr"
        )
        processBuilder.start
      } catch {
        case e: IOException => throw new TesseractMissingException(options.tesseractPath, e)
      }
    }
  }

  /** Starts communicating with the process.
    *
    * Returns (stdout, stderr) pair.
    */
  private def sendInputAndReadOutputs(process: Process, input: Array[Byte])(implicit ec: ExecutionContext)
  : Future[(Array[Byte],Array[Byte])] = {
    val stdout = new ByteArrayOutputStream
    val stderr = new ByteArrayOutputStream

    // Send data to stdin
    val stdinFuture = Future(blocking {
      process.getOutputStream.write(input)
      process.getOutputStream.close
    })

    // Read into stdout and stderr, in separate threads
    val stdoutFuture = Future(blocking { drainStream(process.getInputStream, stdout) })
    val stderrFuture = Future(blocking { drainStream(process.getErrorStream, stderr) })

    for {
      _ <- stdinFuture
      _ <- stderrFuture
      _ <- stdoutFuture
    } yield (stdout.toByteArray, stderr.toByteArray)
  }
}
