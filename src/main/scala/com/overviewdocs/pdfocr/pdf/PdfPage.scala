package com.overviewdocs.pdfocr.pdf

import java.awt.{Dimension,Rectangle}
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.{IOException,StringReader}
import java.util.regex.Pattern
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage,PDPageContentStream}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.rendering.{ImageType,PDFRenderer}
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.util.Matrix
import org.xml.sax.{Attributes,ContentHandler,InputSource,SAXException,SAXParseException}
import org.xml.sax.ext.DefaultHandler2
import org.xml.sax.helpers.XMLReaderFactory
import scala.concurrent.{ExecutionContext,Future}
import scala.collection.mutable.Buffer

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
    val hocrString = new String(hocr, "utf-8")
    val hocrReader = new StringReader(hocrString)
    val inputSource = new InputSource(hocrReader)
    val handler = new HocrHandler(this)
    try {
      val xmlReader = XMLReaderFactory.createXMLReader()
      xmlReader.setContentHandler(handler)
      xmlReader.setErrorHandler(handler)
      xmlReader.setEntityResolver(handler)
      xmlReader.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false)
      xmlReader.setFeature("http://xml.org/sax/features/validation", false)
      xmlReader.parse(inputSource)
    } catch {
      case ex: Exception => {
        ex.printStackTrace
        throw ex
      }
    }
  }

  private class HocrHandler(pdfPage: PdfPage) extends DefaultHandler2 {
    implicit class AugmentedPDRectangle(pdRectangle: PDRectangle) {
      def toRectangle: Rectangle = new Rectangle(
        pdRectangle.getLowerLeftX.toInt,
        pdRectangle.getLowerLeftY.toInt,
        pdRectangle.getWidth.toInt,
        pdRectangle.getHeight.toInt
      )
    }

    implicit class AugmentedAttributes(attributes: Attributes) {
      def className: String = Option(attributes.getValue("class")).getOrElse("")
      def boundingBox: Rectangle = Option(attributes.getValue("title")).map(titleToBoundingBox).getOrElse(new Rectangle())

      /** Converts `"bbox 296 2122 928 2155; x_wconf 95"` to `new Rectangle(296, 2122, 632, 33)` */
      private def titleToBoundingBox(title: String): Rectangle = {
        val matcher = BoundingBoxRegex.matcher(title)
        matcher.find match {
          case false => new Rectangle()
          case true => {
            val x0 = matcher.group(1).toInt
            val y0 = matcher.group(2).toInt
            val x1 = matcher.group(3).toInt
            val y1 = matcher.group(4).toInt
            new Rectangle(x0, y0, x1 - x0, y1 - y0)
          }
        }
      }
    }

    private case class Word(bbox: Rectangle, text: String)

    private val buf: StringBuilder = new StringBuilder
    private var lineBoundingBox: Rectangle = new Rectangle()
    private var wordBoundingBox: Rectangle = new Rectangle()
    private val words: Buffer[Word] = Buffer()
    private var parsingWord: Boolean = false

    private val cropBox: Rectangle = pdfPage.pdPage.getCropBox.toRectangle
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

    // If we don't implement this, Xerces will download from the Web....
    override def resolveEntity(name: String, publicId: String, baseURI: String, systemId: String): InputSource = {
      new InputSource(new StringReader(""))
    }

    override def characters(ch: Array[Char], start: Int, length: Int): Unit = {
      buf.append(String.valueOf(ch, start, length))
    }

    override def startElement(uri: String, localName: String, qName: String, attributes: Attributes): Unit = {
      qName match {
        case "span" if attributes.className == "ocr_line" => {
          lineBoundingBox = attributes.boundingBox
        }
        case "span" if attributes.className == "ocrx_word" => {
          parsingWord = true
          wordBoundingBox = attributes.boundingBox
        }
        case _ =>
      }
    }

    override def endElement(uri: String, localName: String, qName: String): Unit = {
      qName match {
        case "span" if parsingWord => {
          parsingWord = false
          val text = buf.toString.trim
          if (text.nonEmpty) {
            words.append(Word(wordBoundingBox, text))
          }
          buf.delete(0, buf.length)
        }
        case "span" if words.nonEmpty => {
          renderLine
          words.clear
        }
        case _ => {}
      }
    }

    override def endDocument: Unit = {
      if (mustCloseStream) {
        stream.endText
        stream.close
      }
    }

    override def error(ex: SAXParseException): Unit = fatalError(ex)
    override def warning(ex: SAXParseException): Unit = error(ex)

    private val BoundingBoxRegex: Pattern = Pattern.compile("\\bbbox (\\d+) (\\d+) (\\d+) (\\d+)\\b")

    private def renderLine: Unit = {
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
      val maxWordHeight: Double = words.map(_.bbox.height).max // in hOCR coordinates
      val scaleY: Double = maxWordHeight / FontSize * dpiScale

      val lineTop: Double = lineBoundingBox.y + (lineBoundingBox.height - maxWordHeight) * 0.5 // hOCR coordinates
      val baseline: Double = cropBox.height - lineTop * dpiScale - fontAscent * scaleY - cropBox.y // in PDF coordinates

      words.foreach { word =>
        val bbox = word.bbox

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
