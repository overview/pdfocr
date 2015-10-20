package com.overviewdocs.pdfocr

import java.nio.file.Paths
import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.pdfocr.pdf.{PdfDocument,PdfPage}
import com.overviewdocs.pdfocr.ocr.{Tesseract,TesseractOptions}

object Main {
  lazy val tesseract = new Tesseract(TesseractOptions())

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("Usage: pdfocr in.pdf out.pdf")
      System.exit(1)
    }

    val done: Future[_] = PdfDocument.load(Paths.get(args(0)))
      .flatMap { pdfDocument =>
        val pageIterator = pdfDocument.pages

        def step: Future[Unit] = if (pageIterator.hasNext) {
          pageIterator.next.flatMap { page =>
            handlePage(page).flatMap { _ =>
              step
            }
          }
        } else {
          pdfDocument.pdDocument.save(args(1))

          pdfDocument.close
          Future.successful(())
        }

        step
      }
      .recover {
        case ex: Exception => ex.printStackTrace; throw ex
      }

    scala.concurrent.Await.result(done, scala.concurrent.duration.Duration.Inf)
  }

  def handlePage(page: PdfPage): Future[Unit] = {
    System.out.println("Handle page")
    if (page.toText.length > 100) {
      System.out.println(s"Found ${page.toText.length} characters of text data")
      Future.successful(())
    } else {
      val image = page.toImage
      javax.imageio.ImageIO.write(image, "png", Paths.get("page.png").toFile)
      for {
        result <- tesseract.ocr(image, Seq(new Locale("en")))
      } yield {
        System.out.println(s"Found ${result.standardOutput.length} bytes of hOCR data")
        page.addHocr(result.standardOutput)
        ()
      }
    }
  }
}
