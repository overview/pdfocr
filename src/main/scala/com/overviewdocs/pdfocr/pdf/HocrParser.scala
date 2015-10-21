package com.overviewdocs.pdfocr.pdf

import java.awt.Rectangle
import java.io.{InputStream,StringReader}
import java.util.regex.Pattern
import org.xml.sax.{Attributes,ContentHandler,InputSource,SAXException,SAXParseException}
import org.xml.sax.ext.DefaultHandler2
import org.xml.sax.helpers.XMLReaderFactory
import scala.collection.mutable.Buffer

/** Reads hOCR output from Tesseract.
  *
  * This doesn't handle hOCR in general. It only handles Tesseract's hOCR
  * output.
  *
  * Usage:
  *
  *     val parser = new HocrParser(input)
  *     parser.foreach { line =&gt;
  *       line.foreach { word =&gt;
  *         System.out.println(s"Word ${word.text} at (${word.boundingBox.x}, ${word.boundingBox.y})")
  *       }
  *     }
  */
class HocrParser(val input: InputStream) extends Traversable[HocrLine] {
  override def foreach[U](f: HocrLine => U): Unit = {
    val inputSource = new InputSource(input)
    val handler = new HocrParser.XmlHandler(f)
    val xmlReader = XMLReaderFactory.createXMLReader
    xmlReader.setContentHandler(handler)
    xmlReader.setErrorHandler(handler)
    xmlReader.setEntityResolver(handler)
    xmlReader.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false)
    xmlReader.setFeature("http://xml.org/sax/features/validation", false)
    xmlReader.parse(inputSource)
  }
}

object HocrParser {
  implicit private class AugmentedAttributes(attributes: Attributes) {
    def className: String = Option(attributes.getValue("class")).getOrElse("")
    def boundingBox: Rectangle = Option(attributes.getValue("title")).map(titleToBoundingBox).getOrElse(new Rectangle())

    private val BoundingBoxRegex: Pattern = Pattern.compile("\\bbbox (\\d+) (\\d+) (\\d+) (\\d+)\\b")

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

  private class XmlHandler[U](callback: HocrLine => U) extends DefaultHandler2 {
    private val buf: StringBuilder = new StringBuilder
    private var lineBoundingBox: Rectangle = new Rectangle()
    private var wordBoundingBox: Rectangle = new Rectangle()
    private val words: Buffer[HocrWord] = Buffer()
    private var parsingWord: Boolean = false

    override def error(ex: SAXParseException): Unit = fatalError(ex)
    override def warning(ex: SAXParseException): Unit = error(ex)

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
            words.append(HocrWord(wordBoundingBox, text))
          }
          buf.delete(0, buf.length)
        }
        case "span" /* ocr_line */ if words.nonEmpty => {
          callback(HocrLine(lineBoundingBox, Seq(words: _*)))
          words.clear
        }
        case _ => {}
      }
    }
  }
}
