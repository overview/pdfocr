package com.overviewdocs.pdfocr.pdf

import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage,PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.rendering.{ImageType,PDFRenderer}
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.util.Matrix

import com.overviewdocs.pdfocr.exceptions.PdfInvalidException

/** A page of a PDF document.
  *
  * A PDF document can throw a PdfInvalidException at any time during reading.
  *
  * A PdfPage will only be valid so long as its parent PdfDocument's `close`
  * method has not been called.
  *
  * @param pdfDocument A PdfDocument
  * @param pdPage A PDPage (PDFBox internal representation)
  * @param pageNumber 0-based page index
  */
class PdfPage(val pdfDocument: PdfDocument, val pdPage: PDPage, val pageNumber: Int) {
  private val OcrTextSize: Int = 12     // always. Arbitrary.
  private val PdfDpi: Int = 72          // always. Part of the PDF spec.
  private val ImageDpi: Int = 300       // What we send to Tesseract. Arbitrary.
  private val MaxResolution: Int = 4000 // To ensure Tesseract finishes promptly. Picked by trying a few.
  private val pdDocument: PDDocument = pdfDocument.pdDocument

  /** Returns all the text we can read from the document. */
  @throws(classOf[PdfInvalidException])
  def toText: String = {
    val stripper = new PDFTextStripper
    stripper.setStartPage(pageNumber + 1)
    stripper.setEndPage(pageNumber + 1)

    try {
      stripper.getText(pdDocument)
    } catch {
      case ex: NullPointerException => throw new PdfInvalidException(pdfDocument.path.toString, ex)
    }
  }

  /** Returns how many dots-per-inch we should render an image.
    *
    * The result will be 300, unless the PDF is large. If the PDF is large, the
    * DPI will max out at the largest integer that makes the output image
    * smaller than 4000x4000.
    *
    * If the page is missing a media box, the DPI will be 1.
    */
  private def bestDpi: Int = {
    var dpi = ImageDpi

    Option(pdPage.getMediaBox) match {
      case Some(rect) => {
        var dpi = ImageDpi

        if (rect.getWidth * dpi / PdfDpi > MaxResolution) {
          dpi = MaxResolution * PdfDpi / rect.getWidth.toInt
        }
        if (rect.getHeight * dpi / PdfDpi > MaxResolution) {
          dpi = MaxResolution * PdfDpi / rect.getHeight.toInt
        }

        dpi
      }
      case None => 1
    }
  }

  /** Renders the page to an image. */
  @throws(classOf[PdfInvalidException])
  def toImage: BufferedImage = {
    val renderer = new PDFRenderer(pdDocument)

    try {
      renderer.renderImageWithDPI(pageNumber, bestDpi, ImageType.GRAY)
    } catch {
      case ex: NullPointerException => throw new PdfInvalidException(pdfDocument.path.toString, ex)
    }
  }

  /** Uses hOCR data to add invisible text to the page.
    *
    * This will only work with Tesseract 3.03's hOCR output. It assumes the
    * hOCR output uses the same resolution as returned by `bestDpi` -- that is,
    * the resolution of the `toImage` output.
    */
  def addHocr(hocr: Array[Byte]): Unit = {
    val input = new ByteArrayInputStream(hocr)
    val parser = new HocrParser(input)

    val handler = new HocrHandler(this)

    parser.foreach(handler.renderLine)

    handler.close
  }

  private class HocrHandler(pdfPage: PdfPage) {
    private def pdRectangleToRectangle(pdRectangle: PDRectangle) = new Rectangle(
      pdRectangle.getLowerLeftX.toInt,
      pdRectangle.getLowerLeftY.toInt,
      pdRectangle.getWidth.toInt,
      pdRectangle.getHeight.toInt
    )

    private val cropBox: Rectangle = pdRectangleToRectangle(pdfPage.pdPage.getCropBox)
    private val dpiScale: Double = PdfDpi.toDouble / pdfPage.bestDpi
    private val FontSize: Double = 12 // It's always 12; then we scale it

    private lazy val font = PDType0Font.load(pdfPage.pdDocument, getClass.getResourceAsStream("/unifont-8.0.01.ttf"))
    private lazy val fontAscent = font.getFontDescriptor.getAscent * FontSize / 1000
    private var mustCloseStream = false
    private lazy val stream: PDPageContentStream = {
      mustCloseStream = true
      val ret = new PDPageContentStream(pdfPage.pdDocument, pdfPage.pdPage, true, true, true)
      ret.beginText
      ret.appendRawCommands("3 Tr ") // Set text invisible. No alternative; no way to silence deprecation warning
                                        // TODO file bug for        ^^^        ^^ is https://issues.scala-lang.org/browse/SI-7934
      ret
    }

    def close: Unit = {
      if (mustCloseStream) {
        stream.endText
        stream.close
      }
    }

    def renderLine(line: HocrLine): Unit = {
      val words = line.words

      /*
       * When Tesseract finds a "line", it gives the line's dimensions as a
       * bbox. However, the line might be slightly crooked, in which case the
       * height of the bbox will be far greater than the desired font size. So
       * we can't use the line's height to determine font size.
       *
       * When Tesseract finds a "word", it gives a bounding box that won't
       * include a font's descent or ascent if the word doesn't contain them.
       * (The word "no" is smaller, vertially, than the word "yes".) So we
       * can't use the word's height to determine font size.
       *
       * A good strategy: take the maximum word height in the line. Assume
       * Tesseract's notion of a "line" means "all the same font size". (I have
       * no idea whether that's correct.)
       *
       * This forces us to put all words at the same `y`. Tesseract's bboxes
       * don't tell us where the baseline is, and PDF spec needs a baseline. We
       * know `baseline = top - ascent`. We'll calculate `top` by centering the
       * `middle` at the `lineBbox` middle.
       */
      val maxWordHeight: Double = words.map(_.boundingBox.height).max // in hOCR coordinates
      val scaleY: Double = maxWordHeight / FontSize * dpiScale

      val lineTop: Double = line.boundingBox.y + (line.boundingBox.height - maxWordHeight) * 0.5 // hOCR coordinates
      val baseline: Double = cropBox.height - lineTop * dpiScale - fontAscent * scaleY - cropBox.y // in PDF coordinates

      words.foreach { word =>
        val bbox = word.boundingBox

        stream.setFont(font, FontSize.toFloat)

        val fontWidth: Double = font.getStringWidth(word.text) * FontSize / 1000 // width without scaling
        val scaleX: Double = bbox.width / fontWidth * dpiScale
        val leftX: Double = bbox.x * dpiScale - cropBox.x // in PDF coordinates

        val transform = new AffineTransform
        transform.scale(scaleX, scaleY)
        transform.translate(leftX / scaleX, baseline / scaleY)
        stream.setTextMatrix(new Matrix(transform))

        stream.showText(word.text)
      }
    }
  }
}
