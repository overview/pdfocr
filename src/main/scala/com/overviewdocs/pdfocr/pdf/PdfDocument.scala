package com.overviewdocs.pdfocr.pdf

import java.io.IOException
import java.nio.file.{Files,Path}
import org.apache.pdfbox.exceptions.CryptographyException
import org.apache.pdfbox.io.RandomAccessFile
import org.apache.pdfbox.pdfparser.NonSequentialPDFParser
import org.apache.pdfbox.pdmodel.{PDDocument,PDPage}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.pdfocr.exceptions._

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
  private val jPages: java.lang.Iterable[_] = {
    pdDocument.getDocumentCatalog.getAllPages
  }

  /** Releases resources associated with this document.
    *
    * You must call this method when you're done with the object.
    */
  def close: Unit = pdDocument.close

  /** The number of pages in the document.
    *
    * This method will <em>not</em> block or throw an exception.
    */
  def nPages: Int = pdDocument.getNumberOfPages

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
  def pages: Iterator[Future[PdfPage]] = {
    val jIterator = jPages.iterator
    val parent = this // self-contained inner object
    val executionContext = ec

    new Iterator[Future[PdfPage]] {
      var nextPageNumber: Int = 0

      override def hasNext: Boolean = jIterator.hasNext
      override def next: Future[PdfPage] = {
        Future {
          val pdPage: PDPage = try {
            jIterator.next.asInstanceOf[PDPage]
          } catch {
            case ex: NullPointerException => {
              throw new PdfInvalidException(parent.path.toString, ex)
            }
            case ex: Exception => {
              ex.printStackTrace
              System.out.println("HERE HERE HERE")
              throw ex
            }
          }
          val pageNumber = nextPageNumber
          nextPageNumber += 1
          new PdfPage(parent, pdPage, pageNumber)
        }(ec)
      }
    }
  }
}

object PdfDocument {
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
    * Performance note: PDFBox 1.8.10 parses the entire document here, which
    * can take a long time and eat a lot of memory. PDFBox 2.0.0 will solve
    * these issues; once we upgrade, the page iterator will become slower and
    * this method will become faster. (We'll avoid most memory issues, too.)
    */
  def load(path: Path)(implicit ec: ExecutionContext): Future[PdfDocument] = Future(blocking {
    val tempfile = Files.createTempFile("pdfocr", "parser-RandomAccess.tmp")
    val randomAccess = new RandomAccessFile(tempfile.toFile, "rw")
    Files.delete(tempfile)

    val parser = new NonSequentialPDFParser(path.toFile, randomAccess)

    // Read enough of the document to produce an error if it isn't a PDF
    try {
      parser.parse
    } catch {
      case ex: IOException if ex.getMessage.substring(0, 30) == "Error (CryptographyException) " => {
        throw new PdfEncryptedException(path.toString, ex)
      }
      case ex: IOException => {
        throw new PdfInvalidException(path.toString, ex)
      }
    }

    new PdfDocument(path, parser.getPDDocument)
  })
}
