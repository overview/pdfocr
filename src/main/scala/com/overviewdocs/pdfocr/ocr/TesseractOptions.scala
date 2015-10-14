package com.overviewdocs.pdfocr.ocr

case class TesseractOptions(
  tesseractPath: String = "tesseract"
)

object TesseractOptions {
  val Default = TesseractOptions()
}
