package org.overviewproject.pdfocr.ocr

import org.overviewproject.pdfocr.exceptions.PdfOcrException

/** The successful result of invoking Tesseract and piping an image to stdin.
  *
  * This class is the result of a _successful_ invocation of Tesseract. If
  * there was a failure, the caller will receive an exception instead.
  *
  * Expect stdout to contain hOCR text. Expect stderr to contain nothing of
  * import.
  *
  * Note: Tesseract invocations can't be cancelled.
  */
case class TesseractResult(
  standardOutput: Array[Byte],
  standardError: Array[Byte]
)
