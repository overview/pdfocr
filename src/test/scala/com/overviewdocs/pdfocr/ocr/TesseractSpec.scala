package com.overviewdocs.pdfocr.ocr

import java.awt.image.BufferedImage
import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.pdfocr.test.UnitSpec
import com.overviewdocs.pdfocr.exceptions._

class TesseractSpec extends UnitSpec {
  val tesseractPath: String = getClass.getResource("/fake-tesseract").toString.replace("file:", "")
  val tesseractOptions: TesseractOptions = TesseractOptions(tesseractPath)
  val tesseract: Tesseract = new Tesseract(tesseractOptions)
  val image: BufferedImage = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY)

  it("shells to Tesseract and collects stderr") {
    val result = tesseract.ocr(image, Seq(new Locale("en"))).futureValue
    new String(result.standardError, "utf-8") must equal("- - -l eng -psm 1 hocr\n")
  }

  it("concatenates languages using +") {
    val result = tesseract.ocr(image, Seq(new Locale("en"), new Locale("fr"))).futureValue
    new String(result.standardError, "utf-8") must equal("- - -l eng+fra -psm 1 hocr\n")
  }

  it("sends Tesseract the image as BMP") {
    val result = tesseract.ocr(image, Seq(new Locale("en"))).futureValue
    // We won't test every pixel, but we can test it's a BMP. (Our test script
    // just outputs its input.)
    val bytes = result.standardOutput
    bytes(0) must equal(0x42)
    bytes(1) must equal(0x4d)

    // BMP: file size is in the header, as 4-byte integer
    bytes.length must equal((bytes(5) & 0xff) << 24 | (bytes(4) & 0xff) << 16 | (bytes(3) & 0xff) << 8 | bytes(2) & 0xff)
  }

  it("throws TesseractMissingException") {
    val tesseract2 = new Tesseract(TesseractOptions("/invalid-tesseract-path"))
    tesseract2.ocr(image, Seq(new Locale("en"))).failed.futureValue mustBe a[TesseractMissingException]
  }

  it("throws TesseractLanguageMissingException") {
    val exception = tesseract.ocr(image, Seq(new Locale("zxx"))).failed.futureValue
    exception mustBe a[TesseractLanguageMissingException]
    exception.asInstanceOf[TesseractLanguageMissingException].language must equal("zxx")
  }

  it("throws TesseractLanguageMissingException when the missing language is `osd`") {
    val exception = tesseract.ocr(image, Seq(new Locale("osd"))).failed.futureValue
    exception mustBe a[TesseractLanguageMissingException]
    exception.asInstanceOf[TesseractLanguageMissingException].language must equal("osd")
  }

  it("throws TesseractUnknownException") {
    val exception = tesseract.ocr(image, Seq(new Locale("und"))).failed.futureValue
    exception mustBe a[TesseractFailedException]
    exception.asInstanceOf[TesseractFailedException].retval must equal(1)
    exception.asInstanceOf[TesseractFailedException].stderr must equal("An error message\n")
  }
}
