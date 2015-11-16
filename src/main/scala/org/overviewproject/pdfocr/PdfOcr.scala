package org.overviewproject.pdfocr

import java.nio.file.Path
import java.util.Locale
import scala.concurrent.{ExecutionContext,Future}

import org.overviewproject.pdfocr.ocr.{Tesseract,TesseractOptions}
import org.overviewproject.pdfocr.pdf.{PdfDocument,PdfPage}

/** Utility methods for dealing with PDFs. */
trait PdfOcr {
  protected val tesseract: Tesseract
  protected def loadPdfDocument(path: Path)(implicit ec: ExecutionContext): Future[PdfDocument]

  /** Runs OCR on each page of the input that has fewer than 100 characters of
    * text, and outputs a valid, searchable PDF.
    *
    * This method can throw some exceptions that are entirely natural:
    *
    * * `PdfEncryptedException`: the input PDF needs a password.
    * * `PdfInvalidException`: the input PDF contains unrecoverable errors.
    * * `TesseractMissingException`: Tesseract cannot be run.
    * * `TesseractLanguageMissingException`: Tesseract needs a language file.
    *
    * It may also throw exceptions you should probably never see:
    *
    * * `FileNotFoundException`: the input file or output directory is missing.
    * * `SecurityException`: you cannot read the input or write the output.
    * * `TesseractFailedException`: Tesseract did not run properly.
    * * `OutOfMemoryException`: PDFBox has an evil bug.
    *
    * If this method returns a failure, or if `progress()` returns `false`,
    * `out` will not be written.
    *
    * @param in Path to input, which must be a valid PDF file.
    * @param out Path to output, which will be overwritten or deleted.
    * @param languages Languages to use for OCR.
    * @param progress Method to call with (nPagesCompleted, nPagesTotal) every
    *                 page. The first call will be (0, nPagesTotal) and the
    *                 last call will be (nPagesTotal, nPagesTotal). If the
    *                 method ever returns `false`, the future will resolve and
    *                 `out` will not be written. (This is how callers can
    *                 cancel a lengthy OCR Process.)
    */
  def makeSearchablePdf(in: Path, out: Path, languages: Seq[Locale], progress: (Int, Int) => Boolean)(implicit ec: ExecutionContext): Future[Unit] = {
    loadPdfDocument(in).flatMap { pdfDocument =>
      val pageIterator = pdfDocument.pages

      var cancelled: Boolean = false
      var curPage: Int = 0
      val nPages: Int = pdfDocument.nPages

      def step: Future[Unit] = {
        cancelled = !progress(curPage, nPages)

        if (!cancelled && pageIterator.hasNext) {
          curPage += 1 // for next time
          pageIterator.next
            .flatMap { pdfPage => makePdfPageSearchable(pdfPage, languages) }
            .flatMap { _ => step }
        } else {
          Future.successful(())
        }
      }

      step
        .flatMap { _ =>
          if (cancelled) {
            Future.successful(())
          } else {
            pdfDocument.write(out)
          }
        }
        .andThen { case _ => pdfDocument.close }
    }
  }

  /** Modifies a PdfPage such that it's searchable.
    *
    * Invokes Tesseract to if there are 100 characters of text. Otherwise, this
    * is a no-op.
    */
  private def makePdfPageSearchable(pdfPage: PdfPage, languages: Seq[Locale])(implicit ec: ExecutionContext): Future[Unit] = {
    if (pdfPage.toText.length < 100) {
      tesseract.ocr(pdfPage.toImageWithoutText, languages)
        .map { result => pdfPage.addHocr(result.standardOutput) }
        .map { _ => () }
    } else {
      Future.successful(())
    }
  }
}

/** Singleton instance of PdfOcr. */
object PdfOcr extends PdfOcr {
  override protected val tesseract = new Tesseract(TesseractOptions())

  override protected def loadPdfDocument(path: Path)(implicit ec: ExecutionContext): Future[PdfDocument] = {
    PdfDocument.load(path)(ec)
  }
}
