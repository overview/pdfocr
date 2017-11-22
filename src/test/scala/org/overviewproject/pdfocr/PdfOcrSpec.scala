package org.overviewproject.pdfocr

import java.awt.image.BufferedImage
import java.io.FileNotFoundException
import java.nio.file.{Files,Path,Paths}
import java.util.Locale
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never,when,verify}
import org.scalatest.BeforeAndAfter
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.pdfocr.exceptions._
import org.overviewproject.pdfocr.ocr.{Tesseract,TesseractResult}
import org.overviewproject.pdfocr.pdf.{PdfDocument,PdfPage}
import org.overviewproject.pdfocr.test.UnitSpec

class PdfOcrSpec extends UnitSpec with BeforeAndAfter {
  /** Returns a tuple with three useful values:
    *
    * 1. a PdfOcr
    * 2. a mock[Tesseract]
    * 3. a mock[PdfDocument]
    *
    * The PdfOcr's loadPdfDocument method will return failures when given
    * special paths. Similarly, the pdfDocument.write() method will return
    * failures when given special paths.
    */
  private def init = {
    val mockTesseract = mock[Tesseract]

    val mockPdfDocument = mock[PdfDocument]

    when(mockPdfDocument.write(Paths.get("file-not-found.pdf"))).thenReturn(Future.failed(new FileNotFoundException))
    when(mockPdfDocument.write(Paths.get("security.pdf"))).thenReturn(Future.failed(new SecurityException))
    when(mockPdfDocument.write(Paths.get("out.pdf"))).thenReturn(Future.successful(()))

    val pdfOcr = new PdfOcr {
      override protected val tesseract = mockTesseract
      override def loadPdfDocument(path: Path)(implicit ec: ExecutionContext) = path.toString match {
        case "file-not-found.pdf" => Future.failed(new FileNotFoundException)
        case "security.pdf" => Future.failed(new SecurityException)
        case "pdf-encrypted.pdf" => Future.failed(new PdfEncryptedException(new Exception("foo")))
        case "pdf-invalid.pdf" => Future.failed(new PdfInvalidException(new Exception("foo")))
        case "1-page.pdf" => {
          when(mockPdfDocument.nPages).thenReturn(1)
          Future.successful(mockPdfDocument)
        }
        case "2-page.pdf" => {
          when(mockPdfDocument.nPages).thenReturn(2)
          Future.successful(mockPdfDocument)
        }
        case _ => Future.successful(mockPdfDocument)
      }
    }
    (pdfOcr, mockTesseract, mockPdfDocument)
  }

  private val emptyImage: BufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)

  private def dummyProgress(nPagesDone: Int, nPagesTotal: Int): Boolean = true

  private val dummyLocales: Seq[Locale] = Seq(new Locale("en"))

  private def mockPdfPage(text: String): PdfPage = {
    val ret = mock[PdfPage]
    when(ret.toImageWithoutText).thenReturn(emptyImage)
    when(ret.toText).thenReturn(text)
    ret
  }

  private def mockPdfPages(texts: String*): Seq[PdfPage] = {
    texts.map(mockPdfPage _)
  }

  private val dummyTesseractResult: TesseractResult = {
    new TesseractResult("stdout".getBytes("utf-8"), "stderr".getBytes("utf-8"))
  }

  describe("makeSearchablePdf") {
    it("should pass languages to tesseract") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 99).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)
      when(tesseract.ocr(any[BufferedImage], any[Seq[Locale]])(any[ExecutionContext])).thenReturn(Future.successful(dummyTesseractResult))

      pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).futureValue

      verify(tesseract).ocr(emptyImage, dummyLocales)
    }

    it("should add hOCR data to the page") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pdfPage = mockPdfPage("x" * 99)
      val pages = Seq(Future.successful(pdfPage)).iterator
      when(pdfDocument.pages).thenReturn(pages)

      val hOcr = "hOcr".getBytes("utf-8")
      val tesseractResult = TesseractResult(hOcr, "".getBytes)

      when(tesseract.ocr(any[BufferedImage], any[Seq[Locale]])(any[ExecutionContext])).thenReturn(Future.successful(tesseractResult))

      pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).futureValue

      verify(pdfPage).addHocr(hOcr)
    }

    it("should not OCR any page that has >=100 characters of text") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 100).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)

      pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).futureValue

      verify(tesseract, never).ocr(emptyImage, dummyLocales)
    }

    it("should write out") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 100).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)

      val out = Paths.get("out.pdf")

      pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), out, dummyLocales, dummyProgress).futureValue

      verify(pdfDocument).write(out)
    }

    it("should close the document") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 100).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)

      pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).futureValue

      verify(pdfDocument).close
    }

    it("should throw FileNotFoundException if the input is not found") {
      val (pdfOcr, tesseract, pdfDocument) = init

      val ex = pdfOcr.makeSearchablePdf(Paths.get("file-not-found.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[FileNotFoundException]
    }

    it("should throw FileNotFoundException if the output directory is not found") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 100).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)

      val ex = pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("file-not-found.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[FileNotFoundException]
      verify(pdfDocument).close
    }

    it("should throw SecurityException if the input is restricted") {
      val (pdfOcr, tesseract, pdfDocument) = init

      val ex = pdfOcr.makeSearchablePdf(Paths.get("security.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[SecurityException]
    }

    it("should throw SecurityException if the output is restricted") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pages = mockPdfPages("x" * 100).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pages)

      val ex = pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("security.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[SecurityException]
      verify(pdfDocument).close
    }

    it("should throw PdfEncryptedException") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val ex = pdfOcr.makeSearchablePdf(Paths.get("pdf-encrypted.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[PdfEncryptedException]
    }

    it("should throw PdfInvalidException if the PDF is completely invalid") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val ex = pdfOcr.makeSearchablePdf(Paths.get("pdf-invalid.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[PdfInvalidException]
    }

    it("should throw PdfInvalidException if the PDF is invalid after the first page") {
      val (pdfOcr, tesseract, pdfDocument) = init

      val pdfPage = mock[PdfPage]
      when(pdfPage.toText).thenThrow(new PdfInvalidException(null))

      val pages = Seq(Future.successful(pdfPage)).iterator
      when(pdfDocument.pages).thenReturn(pages)

      val ex = pdfOcr.makeSearchablePdf(Paths.get("1-page.pdf"), Paths.get("out.pdf"), dummyLocales, dummyProgress).failed.futureValue
      ex mustBe a[PdfInvalidException]
      verify(pdfDocument).close
    }

    it("should invoke progress between each page") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pdfPages = mockPdfPages("p1" * 50, "p2" * 50).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pdfPages)

      val calls = scala.collection.mutable.Buffer[(Int,Int)]()
      def progress(nPages: Int, nPagesTotal: Int): Boolean = {
        calls.+=((nPages, nPagesTotal))
        true
      }

      pdfOcr.makeSearchablePdf(Paths.get("2-page.pdf"), Paths.get("out.pdf"), dummyLocales, progress).futureValue

      calls must equal(Seq((0, 2), (1, 2), (2, 2)))
    }

    it("should cancel if progress returns false") {
      val (pdfOcr, tesseract, pdfDocument) = init
      val pdfPages = mockPdfPages("p1" * 50, "p2" * 50).map(Future.successful _).iterator
      when(pdfDocument.pages).thenReturn(pdfPages)

      def progress(nPages: Int, nPagesTotal: Int): Boolean = nPages == 1

      pdfOcr.makeSearchablePdf(Paths.get("2-page.pdf"), Paths.get("out.pdf"), dummyLocales, progress).futureValue
      verify(pdfDocument, never).write(Paths.get("out.pdf"))
      verify(pdfDocument).close
    }
  }
}
