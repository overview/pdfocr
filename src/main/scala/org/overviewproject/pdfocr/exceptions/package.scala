package org.overviewproject.pdfocr

package object exceptions {
  sealed abstract class PdfOcrException(message: String, cause: Exception) extends Exception(message, cause)

  class PdfInvalidException(cause: Exception)
    extends PdfOcrException(s"Error in PDF file", cause)

  class PdfEncryptedException(cause: Exception)
    extends PdfOcrException(s"PDF file is password-protected", cause)

  class TesseractMissingException(cause: Exception)
    extends PdfOcrException(s"Could not find `tesseract` executable", cause)

  class TesseractLanguageMissingException(val language: String)
    extends PdfOcrException(s"Missing Tesseract language data files for language `$language`", null)

  class TesseractFailedException(val retval: Int, val stderr: String)
    extends PdfOcrException(s"Tesseract returned status code `$retval`. stderr: `$stderr`", null)
}
