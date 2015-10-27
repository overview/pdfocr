package org.overviewproject.pdfocr.pdf

import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Paths
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.pdfocr.exceptions._
import org.overviewproject.pdfocr.test.UnitSpec

class PdfDocumentSpec extends UnitSpec {
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

  describe("load") {
    it("loads a valid PDF") {
      val pdfDocument = load("empty-page.pdf").futureValue
      pdfDocument.path.getFileName.toString must equal("empty-page.pdf")
      pdfDocument.close
    }

    it("throws PdfEncryptedException") {
      load("empty-page-encrypted.pdf").failed.futureValue mustBe a[PdfEncryptedException]
    }

    it("throws PdfInvalidException when the file is not a PDF") {
      val ex = load("not-a-pdf.pdf").failed.futureValue
      ex mustBe a[PdfInvalidException]
    }

    it("throws IOException when the file does not exist") {
      PdfDocument.load(Paths.get("/this/path/is/very/unlikely/to/exist.pdf")).failed.futureValue mustBe a[IOException]
    }
  }

  describe("nPages") {
    it("returns the number of pages") {
      val pdf = load("empty-page.pdf").futureValue
      pdf.nPages must equal(1)
      pdf.close
    }
  }

  describe("pages") {
    it("iterates over each page") {
      val pdf = load("2-pages.pdf").futureValue
      val it = pdf.pages
      try {
        it.hasNext must equal(true)
        it.next.futureValue.toText must equal("Page 1\n")
        it.hasNext must equal(true)
        it.next.futureValue.toText must equal("Page 2\n")
        it.hasNext must equal(false)
      } finally {
        pdf.close
      }
    }

    it("return a page even if it contains an invalid stream") {
      val pdf = load("2nd-page-invalid.pdf").futureValue
      try {
        val it = pdf.pages
        it.next.futureValue
        it.next.futureValue
        it.hasNext must equal(false)
      } finally {
        pdf.close
      }
    }
  }
}
