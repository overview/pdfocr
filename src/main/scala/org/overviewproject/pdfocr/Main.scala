package org.overviewproject.pdfocr

import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.{Executors,ThreadFactory}
import scala.concurrent.{Await,ExecutionContext,Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure,Success}

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

    val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val ret = new Thread(r, "pdfocr executor")
        ret.setDaemon(false)
        ret.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
          override def uncaughtException(t: Thread, ex: Throwable): Unit = {
            ex.printStackTrace()
            Runtime.getRuntime.halt(1)
          }
        })
        ret
      }
    })
    implicit val executionContext = ExecutionContext.fromExecutor(executor)

    val exitCodeFuture: Future[Int] = PdfOcr.makeSearchablePdf(
      Paths.get(args(0)),
      Paths.get(args(1)),
      Seq(new Locale("en")),
      progress
    )
      .map(_ => 0)
      .recover { case ex =>
        ex.printStackTrace()
        1
      }

    val exitCode = Await.result(exitCodeFuture, Duration.Inf)
    System.exit(exitCode)
  }
}
