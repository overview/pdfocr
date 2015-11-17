package org.overviewproject.pdfocr.pdf

import java.io.{FileNotFoundException,IOException}
import java.nio.file.{Files,Path}
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdfparser.PDFParser
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import scala.concurrent.{ExecutionContext,Future,blocking}

import org.overviewproject.pdfocr.exceptions._

/** A PDF document.
  *
  * This class is intended to be created via `PdfDocument.load(...)`, and its
  * asynchronous methods use the ExecutionContext passed to
  * `PdfDocument.load(...)`.
  *
  * You must call PdfDocument.close when finished.
  */
class PdfDocument(
  val path: Path,
  val pdDocument: PDDocument
)(implicit ec: ExecutionContext) {
  /** Releases resources associated with this document.
    *
    * You must call this method when you're done with the object.
    */
  def close: Unit = pdDocument.close

  /** The number of pages in the document.
    *
    * This method will <em>not</em> block or throw an exception.
    */
  val nPages: Int = pdDocument.getNumberOfPages

  /** Iterates over the pages of the document.
    *
    * Each returned element has yet to be parsed; that's why `iterator.next`
    * returns a `Future`. Here's an example usage:
    *
    *     val it = pdfDocument.pages
    *     def step(result: Seq[String])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    *       if (it.hasNext) {
    *         it.next // May be a Future.failed[PdfInvalidException]
    *           .flatMap { page =&gt;
    *             step(result :+ page.toText) // May throw PdfInvalidException
    *           }
    *       } else {
    *         Future.successful(result)
    *       }
    *     }
    *     val pageTexts: Seq[String] = step(Seq())
    *       .recover { case ex: PdfInvalidException =&gt; ... }
    */
  def pages: Iterator[Future[PdfPage]] = new PdfDocument.PdfPageIterator(this)(ec)

  /** Writes the document to a file.
    *
    * The intended usage is:
    *
    * 1. Load a PdfDocument.
    * 2. Mutate its pages.
    * 3. Write it to a new location (this method).
    */
  def write(path: Path): Future[Unit] = Future(blocking(pdDocument.save(path.toFile)))
}

object PdfDocument {
  private val PdfParserMainMemoryBytes = 50 * 1024 * 1024;

  private class PdfPageIterator(pdfDocument: PdfDocument)(implicit ec: ExecutionContext)
  extends Iterator[Future[PdfPage]] {
    var nextPageNumber = 0

    override def hasNext: Boolean = nextPageNumber < pdfDocument.nPages

    override def next: Future[PdfPage] = {
      Future(blocking {
        val pageNumber = nextPageNumber
        nextPageNumber += 1

        val pdPage: PDPage = try {
          pdfDocument.pdDocument.getPage(pageNumber)
        } catch {
          case ex: NullPointerException => {
            throw new PdfInvalidException(ex)
          }
        }

        new PdfPage(pdfDocument, pdPage, pageNumber)
      })(ec)
    }
  }

  /** Opens and returns a PDF document.
    *
    * The return value may be a failed Future:
    *
    * * PdfInvalidException: the main dictionary could not be found.
    * * PdfEncryptedException: the PDF is encrypted and so can't be loaded.
    * * IOException: a low-level file error occurred.
    *
    * Otherwise, the return value will be valid ... but any page in the `pages`
    * iterator may still return a failed Future.
    *
    * This will only parse enough of the document to figure out how many pages
    * there are. The pages method will parse the rest.
    * 
    * Be sure close the returned PdfDocument.
    */
  def load(path: Path)(implicit ec: ExecutionContext): Future[PdfDocument] = Future(blocking {
    val memoryUsageSetting = MemoryUsageSetting.setupMixed(PdfParserMainMemoryBytes)

    // Read enough of the document to produce an error if it isn't a PDF
    try {
      val pdDocument = PDDocument.load(path.toFile, memoryUsageSetting) // Only reads trailer+xref
      pdDocument.setAllSecurityToBeRemoved(true)

      try {
        new PdfDocument(path, pdDocument)
      } catch {
        case ex: Throwable => {
          pdDocument.close
          throw ex
        }
      }
    } catch {
      case ex: InvalidPasswordException => throw new PdfEncryptedException(ex)
      case ex: FileNotFoundException => throw ex
      case ex: SecurityException => throw ex
      case ex: IllegalArgumentException => throw new PdfInvalidException(ex)
      case ex: IOException => throw new PdfInvalidException(ex)
    }
  })
}
