package com.overviewdocs.pdfocr.pdf

import java.awt.image.BufferedImage
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.util.PDFTextStripper
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
  private val PdfDpi: Int = 72 // always. Part of the PDF spec.
  private val ImageDpi: Int = 300
  private val MaxResolution: Int = 4000
  //def toImage: BufferedImage
  //def toPdf: Array[Byte]

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
  def toImage: BufferedImage = {
    pdPage.convertToImage(BufferedImage.TYPE_BYTE_GRAY, bestDpi)
  }
}
