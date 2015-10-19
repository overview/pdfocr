package com.overviewdocs.pdfocr.pdf

import java.nio.file.Paths
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.pdfocr.exceptions.PdfInvalidException
import com.overviewdocs.pdfocr.test.UnitSpec

class PdfPageSpec extends UnitSpec {
  private def load(resourceName: String): Future[PdfDocument] = {
    val pathString: String = try {
      getClass.getResource(s"/trivial-pdfs/$resourceName").toString.replace("file:", "")
    } catch {
      case ex: NullPointerException => {
        throw new Exception(s"Missing test file /trivial-pdfs/$resourceName")
      }
    }
    val path = Paths.get(pathString)
    PdfDocument.load(path)
  }

  describe("toText") {
    it("should return text") {
      val pdDocument = new PDDocument()
      val pdfDocument = new PdfDocument(Paths.get(""), pdDocument)
      val pdPage = new PDPage()
      pdDocument.addPage(pdPage)
      val stream = new PDPageContentStream(pdDocument, pdPage)
      stream.beginText
      stream.setFont(PDType1Font.HELVETICA, 18)
      stream.moveTextPositionByAmount(1, 1)
      stream.drawString("Hello, world!")
      stream.endText
      stream.close

      try {
        val page = new PdfPage(pdfDocument, pdPage, 0)
        page.toText must equal("Hello, world!\n")
      } finally {
        pdDocument.close
      }
    }

    it("should return an empty string when a content stream is invalid") {
      val pdf = load("2nd-page-invalid.pdf").futureValue

      val pageIt = pdf.pages
      pageIt.next.futureValue // skip page 1
      val page = pageIt.next.futureValue

      page.toText must equal("\n")
      pdf.close
    }
  }
}
