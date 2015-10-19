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
class PdfPage(pdfDocument: PdfDocument, pdPage: PDPage, pageNumber: Int) {
  //def toImage: BufferedImage
  //def toPdf: Array[Byte]
  def toText: String = {
    val stripper = new PDFTextStripper
    stripper.setStartPage(pageNumber + 1)
    stripper.setEndPage(pageNumber + 1)
    stripper.getText(pdfDocument.pdDocument)
  }
}
