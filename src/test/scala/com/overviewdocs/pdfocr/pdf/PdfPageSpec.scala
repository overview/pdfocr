package com.overviewdocs.pdfocr.pdf

import java.nio.file.Paths
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.pdfocr.exceptions.PdfInvalidException
import com.overviewdocs.pdfocr.test.UnitSpec

class PdfPageSpec extends UnitSpec {
  /** Loads a PDF document from the "trivial-pdfs" folder. */
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

  /** Creates a "Hello, world!" document. */
  private def helloWorld: (PdfDocument,PdfPage) = {
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
    (pdfDocument, new PdfPage(pdfDocument, pdPage, 0))
  }

  /** Creates a page that has an invalid stream. */
  private def loadInvalidStreamPage: (PdfDocument,PdfPage) = {
    val pdf = load("2nd-page-invalid.pdf").futureValue

    val pageIt = pdf.pages
    pageIt.next.futureValue // skip page 1
    val page = pageIt.next.futureValue

    (pdf, page)
  }

  describe("toText") {
    it("should return text") {
      val (document, page) = helloWorld
      try {
        page.toText must equal("Hello, world!\n")
      } finally {
        document.close
      }
    }

    it("should return an empty string when a content stream is invalid") {
      val (document, page) = loadInvalidStreamPage

      try {
        page.toText must equal("\n")
      } finally {
        document.close
      }
    }
  }

  describe("toImage") {
    it("should return an image at 300dpi") {
      val (document, page) = helloWorld
      page.pdPage.setMediaBox(new PDRectangle(72, 144))

      try {
        val image = page.toImage
        image.getWidth must equal(300)
        image.getHeight must equal(600)
      } finally {
        document.close
      }
    }

    it("should max out at 4000px horizontally") {
      val (document, page) = helloWorld
      page.pdPage.setMediaBox(new PDRectangle(7200, 144))

      try {
        val image = page.toImage
        image.getWidth must equal(4000)
        image.getHeight must equal(80) // 4000 * 144 / 7200
      } finally {
        document.close
      }
    }

    it("should max out at 4000px vertically") {
      val (document, page) = helloWorld
      page.pdPage.setMediaBox(new PDRectangle(144, 7200))

      try {
        val image = page.toImage
        image.getWidth must equal(80) // 4000 * 144 / 7200
        image.getHeight must equal(4000)
      } finally {
        document.close
      }
    }

    it("should return a blank image when a content stream is invalid") {
      val (document, page) = loadInvalidStreamPage

      try {
        val image = page.toImage
        image.getWidth must equal(1)
        image.getHeight must equal(1)
      } finally {
        document.close
      }
    }
  }
}
