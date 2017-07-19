package org.overviewproject.pdfocr.pdf

import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream,ByteArrayOutputStream}
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage,PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.{PDMetadata,PDRectangle}
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.interactive.action.PDPageAdditionalActions
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.rendering.{ImageType,PageDrawer,PageDrawerParameters,PDFRenderer}
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.util.{Matrix,Vector}
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import org.overviewproject.pdfocr.exceptions.PdfInvalidException

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
  private val ImageDpi: Int = 300       // What we send to Tesseract. Arbitrary.
  private val MaxResolution: Int = 4000 // To ensure Tesseract finishes promptly. Picked by trying a few.
  private val pdDocument: PDDocument = pdfDocument.pdDocument

  /** Returns all the text we can read from the document.
    *
    * After you addHocr() and before you write the PDFDocument, toText() will
    * have undefined behavior.
    */
  @throws(classOf[PdfInvalidException]) // Dunno if it can even throw this
  def toText: String = {
    val stripper = new PDFTextStripper
    stripper.setStartPage(pageNumber + 1)
    stripper.setEndPage(pageNumber + 1)

    try {
      stripper.getText(pdDocument)
    } catch {
      case ex: NullPointerException => throw new PdfInvalidException(ex)
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

        if (rect.getWidth * dpi / PdfPage.PdfDpi > MaxResolution) {
          dpi = MaxResolution * PdfPage.PdfDpi / rect.getWidth.toInt
        }
        if (rect.getHeight * dpi / PdfPage.PdfDpi > MaxResolution) {
          dpi = MaxResolution * PdfPage.PdfDpi / rect.getHeight.toInt
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
      case ex: NullPointerException => throw new PdfInvalidException(ex)
    }
  }

  /** Renders the page to an image, omitting all text.
    *
    * In other words, if a PDF has a stream with a rectangle, some text and an
    * image, this method will return an imaage with a rectangle and an image.
    */
  @throws(classOf[PdfInvalidException])
  def toImageWithoutText: BufferedImage = {
    val renderer = new PdfPage.PDFRendererWithoutText(pdDocument)

    try {
      renderer.renderImageWithDPI(pageNumber, bestDpi, ImageType.GRAY)
    } catch {
      case ex: NullPointerException => throw new PdfInvalidException(ex)
    }
  }

  /** Uses hOCR data to add invisible text to the page.
    *
    * This will only work with Tesseract 3.03's hOCR output. It assumes the
    * hOCR output uses the same resolution as returned by `bestDpi` -- that is,
    * the resolution of the `toImage` output.
    *
    * After you addHocr() and before you write the PDFDocument, toText() will
    * have undefined behavior.
    */
  def addHocr(hocr: Array[Byte]): Unit = {
    val input = new ByteArrayInputStream(hocr)
    val parser = new HocrParser(input)

    val handler = new PdfPage.HocrHandler(this)

    parser.foreach(handler.renderLine)

    handler.close
  }

  /** Returns true iff some of this text has a "pdfocr-ocr-text" font.
    *
    * Does not throw an exception on an invalid PDF.
    */
  def isFromOcr: Boolean = Option(pdPage.getResources) match {
    case Some(resources) => {
      def getFontName(cosName: COSName): String = {
        val option = for {
          font <- Option(resources.getFont(cosName))
          descriptor <- Option(font.getFontDescriptor)
          name <- Option(descriptor.getFontName)
        } yield name

        option.getOrElse("")
      }

      val pdFontNames = iterableAsScalaIterableConverter(resources.getFontNames).asScala // "/F1", "/F2", etc
      pdFontNames.exists(cosName => getFontName(cosName) == "pdfocr-ocr-text")
    }
    case None => false
  }

  /** Returns a one-page PDF, as a byte array.
    *
    * Even if the original `pdDocument` is a single page, this method will
    * return a whole new page. (That's so we can avoid blocking re-reading the
    * original input file.)
    *
    * The purpose of this output PDF is *display*. (Another image format would
    * be more ideal; we output PDF because we always have, not because we
    * should.)
    */
  def toPdf: Array[Byte] = {
    // Mostly copied from pdfbox/.../multipdf/Splitter.java, but without the
    // horrendous API.
    val newDocument = new PDDocument(MemoryUsageSetting.setupMainMemoryOnly)

    try {
      newDocument.getDocument.setVersion(pdDocument.getVersion)
      newDocument.setDocumentInformation(pdDocument.getDocumentInformation)
      newDocument.getDocumentCatalog.setViewerPreferences(pdDocument.getDocumentCatalog.getViewerPreferences)
      val newPage = newDocument.importPage(pdPage)
      newPage.setCropBox(pdPage.getCropBox)
      newPage.setMediaBox(pdPage.getMediaBox)
      newPage.setResources(pdPage.getResources) // only the resources of the page will be copied
      newPage.setRotation(pdPage.getRotation)

      // Remove PDF features we don't "like". (We'd prefer PNG to PDF, so
      // we should nix anything "dynamic", such as links, to save space and
      // time.)
      newPage.setAnnotations(null)
      newPage.setActions(null)
      newPage.setMetadata(null)

      val outputStream = new ByteArrayOutputStream
      newDocument.save(outputStream)
      outputStream.toByteArray
    } finally {
      newDocument.close
    }
  }
}

object PdfPage {
  private val PdfDpi: Int = 72          // always. Part of the PDF spec.

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

    private def font = pdfPage.pdfDocument.hocrFont

    private lazy val fontAscent = font.getFontDescriptor.getAscent * FontSize / 1000
    private var mustCloseStream = false
    private lazy val stream: PDPageContentStream = {
      mustCloseStream = true
      val ret = new PDPageContentStream(
        pdfPage.pdDocument,
        pdfPage.pdPage,
        PDPageContentStream.AppendMode.APPEND,
        true,
        true
      )
      ret.beginText
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

  private class PDFRendererWithoutText(document: PDDocument) extends PDFRenderer(document) {
    override protected def createPageDrawer(parameters: PageDrawerParameters): PageDrawer = {
      new PageDrawerWithoutText(parameters)
    }
  }

  private class PageDrawerWithoutText(parameters: PageDrawerParameters) extends PageDrawer(parameters) {
    override def beginText: Unit = ()
    override def endText: Unit = ()
    override protected def showFontGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, unicode: String, displacement: Vector): Unit = ()
  }
}
