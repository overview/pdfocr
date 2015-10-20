package com.overviewdocs.pdfocr.pdf

import java.awt.{Dimension,Rectangle}
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.{IOException,StringReader}
import java.util.regex.Pattern
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.util.PDFTextStripper
import org.xml.sax.{Attributes,ContentHandler,InputSource,SAXException,SAXParseException}
import org.xml.sax.ext.DefaultHandler2
import org.xml.sax.helpers.XMLReaderFactory
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.pdfocr.exceptions.PdfInvalidException

/** A page of a PDF document.
  *
  * A PDF document can throw a PdfInvalidException at any time during reading.
  *
  * A PdfPage will only be valid so long as its parent PdfDocument's `close`
  * method has not been called.
  */
class PdfPage(val pdfDocument: PdfDocument, val pdPage: PDPage, val pageNumber: Int) {
  private val OcrTextSize: Int = 12 // always
  private val PdfDpi: Int = 72 // always. Part of the PDF spec.
  private val ImageDpi: Int = 300
  private val MaxResolution: Int = 4000

  /** Returns all the text we can read from the document. */
  def toText: String = {
    val stripper = new PDFTextStripper
    stripper.setStartPage(pageNumber + 1)
    stripper.setEndPage(pageNumber + 1)
    stripper.getText(pdfDocument.pdDocument)
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

    Option(pdPage.findMediaBox) match {
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
  def toImage: BufferedImage = {
    pdPage.convertToImage(BufferedImage.TYPE_BYTE_GRAY, bestDpi)
  }

  /** Uses hOCR data to add invisible text to the page.
    *
    * This will only work with Tesseract 3.03's hOCR output. It assumes the
    * hOCR output uses the same resolution as returned by `bestDpi` -- that is,
    * the resolution of the `toImage` output.
    */
  def addHocr(hocr: Array[Byte]): Unit = {
    implicit class AugmentedPDRectangle(pdRectangle: PDRectangle) {
      def toRectangle: Rectangle = new Rectangle(
        pdRectangle.getLowerLeftX.toInt,
        pdRectangle.getLowerLeftY.toInt,
        pdRectangle.getWidth.toInt,
        pdRectangle.getHeight.toInt
      )
    }

    val hocrString = new String(hocr, "utf-8")
    val hocrReader = new StringReader(hocrString)
    val inputSource = new InputSource(hocrReader)
    val pageContentStream = new PDPageContentStream(pdfDocument.pdDocument, pdPage, true, true, true)
    val handler = new HocrHandler(pageContentStream, pdPage.findCropBox.toRectangle, bestDpi)
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

    pageContentStream.close
  }

  private class HocrHandler(stream: PDPageContentStream, cropBox: Rectangle, dpi: Int) extends DefaultHandler2 {
    val buf: StringBuilder = new StringBuilder
    var boundingBox: Option[Rectangle] = None

    // If we don't implement this, Xerces will download from the Web....
    override def resolveEntity(name: String, publicId: String, baseURI: String, systemId: String): InputSource = {
      new InputSource(new StringReader(""))
    }

    override def characters(ch: Array[Char], start: Int, length: Int): Unit = {
      buf.append(String.valueOf(ch, start, length))
    }

    override def ignorableWhitespace(ch: Array[Char], start: Int, length: Int): Unit = characters(ch, start, length)

    override def startElement(uri: String, localName: String, qName: String, attributes: Attributes): Unit = {
      qName match {
        case "span" => {
          boundingBox = Option(attributes.getValue("title")).flatMap(titleToBoundingBox)
        }
        case _ => {}
      }
    }

    override def endElement(uri: String, localName: String, qName: String): Unit = {
      qName match {
        case "span" => {
          val text = buf.toString.trim
          if (text.nonEmpty) {
            renderText(text)
          }
          buf.delete(0, buf.length)
        }
        case _ => {}
      }
    }

    override def error(ex: SAXParseException): Unit = fatalError(ex)
    override def warning(ex: SAXParseException): Unit = error(ex)

    private val BoundingBoxRegex: Pattern = Pattern.compile("\\bbbox (\\d+) (\\d+) (\\d+) (\\d+)\\b")

    /** Converts `"bbox 296 2122 928 2155; x_wconf 95"` to `new Rectangle(296, 2122, 632, 33)` */
    private def titleToBoundingBox(title: String): Option[Rectangle] = {
      val matcher = BoundingBoxRegex.matcher(title)
      matcher.find match {
        case false => None
        case true => {
          val x0 = matcher.group(1).toInt
          val y0 = matcher.group(2).toInt
          val x1 = matcher.group(3).toInt
          val y1 = matcher.group(4).toInt
          Some(new Rectangle(x0, y0, x1 - x0, y1 - y0))
        }
      }
    }

    private def renderText(text: String): Unit = {
      boundingBox match {
        case Some(bbox) if bbox.height > 0 && bbox.width > 0 => {
          stream.beginText

          val font = PDType1Font.HELVETICA
          val fontSize: Double = 12 // height without scaling
          val fontWidth: Double = font.getStringWidth(text) * fontSize / 1000 // width without scaling

          val dpiScale: Double = PdfDpi.toDouble / dpi

          val scaleY: Double = bbox.height / fontSize * dpiScale 
          val scaleX: Double = bbox.width / fontWidth * dpiScale 

          val topLeftX: Double = bbox.x * dpiScale - cropBox.x
          val topLeftY: Double = cropBox.height - bbox.y * dpiScale - cropBox.y
          val baselineY: Double = topLeftY - fontSize * scaleY

          val transform = new AffineTransform
          transform.scale(scaleX, scaleY)
          transform.translate(topLeftX / scaleX, baselineY / scaleY)
          stream.setTextMatrix(transform)
          System.out.println(s"Print `$text` at `$transform`")

          stream.setFont(PDType1Font.HELVETICA, fontSize.toFloat)
          stream.drawString(text)
          stream.endText
        }
        case _ => throw new Exception(s"pdfocr error: no bbox for text `$text`")
      }
    }
  }
}
