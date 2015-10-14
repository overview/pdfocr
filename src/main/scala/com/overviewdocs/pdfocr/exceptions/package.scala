package com.overviewdocs.pdfocr

package object exceptions {
  sealed abstract class PdfOcrException(message: String, cause: Exception) extends Exception(message, cause)

  class TesseractMissingException(val path: String, cause: Exception)
    extends PdfOcrException(s"Could not invoke tesseract command `$path`", cause)

  class TesseractLanguageMissingException(val language: String)
    extends PdfOcrException(s"Missing Tesseract language data files for language `$language`", null)

  class TesseractFailedException(val retval: Int, val stderr: String)
    extends PdfOcrException(s"Tesseract returned status code `$retval`. stderr: `$stderr`", null)
}
