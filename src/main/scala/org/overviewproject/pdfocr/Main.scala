package org.overviewproject.pdfocr

import java.nio.file.Paths
import java.util.Locale
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Main {
  def progress(nPages: Int, nPagesTotal: Int): Boolean = {
    if (nPages < nPagesTotal) {
      System.err.println(s"Processing page ${nPages + 1} of $nPagesTotal...")
    }
    true
  }

  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("Usage: pdfocr in.pdf out.pdf")
      System.exit(1)
    }

    val done = PdfOcr.makeSearchablePdf(
      Paths.get(args(0)),
      Paths.get(args(1)),
      Seq(new Locale("en")),
      progress
    )

    scala.concurrent.Await.result(done, scala.concurrent.duration.Duration.Inf)
  }
}
