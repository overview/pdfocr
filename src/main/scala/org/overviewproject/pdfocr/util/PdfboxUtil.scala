//package org.overviewproject.pdfocr.util
//
//import org.apache.pdfbox.io.RandomAccessFile
//import org.apache.pdfbox.model.PDDocument
//import scala.util.Either
//
//object PdfboxUtil {
//  case class InvalidInputPdf(inPdf: Path, cause: Throwable)
//
//  def withPDDocumentSync[A](inPdf: Path)(block: PDDocument => A): Either[InvalidInputPdf,A] = {
//    withTempfile { tempPath =>
//      val scratchFile = new RandomAccessFile(tempPath.toFile, "rw")
//      loadPDDocumentSync(inPdf, scratchFile)
//        .right.map { pdDocument =>
//          try {
//            block(pdDocument)
//          } finally {
//            pdDocument.close
//          }
//        }
//    }
//  }
//
//  def withAllPDPagesSync[A](inPdf: Path)(block: (PDDocument, Iterable[PDPage]) => A): Either[InvalidInputPdf,A] = {
//    withPDDocumentSync(inPdf) { pdDocument: PDDocument =>
//      try {
//        val catalog = pdDocument.getDocumentCatalog
//        val pdPages = iterableAsScalaIterableConverter(catalog.getAllPages).map(_.asInstanceOf[PDPage])
//        block(pdDocument, pdPages)
//      } catch {
//        case ex: COSVisitorException => Left(InvalidInputPdf(inPdf, ex))
//      }
//    }
//  }
//
//  private def withTempfile[A](block: Path => A): A = {
//    val tempfile: Path = Files.createTempFile("pdfocr-", null)
//    try {
//      block(tempfile)
//    } finally {
//      Files.delete(tempfile)
//    }
//  }
//
//  private def loadPDDocumentSync(inPdf: Path, scratchFile: RandomAccessFile): Either[InvalidInputPdf,PDDocument] = {
//    try {
//      Right(PDDocument.loadNonSeq(inPdf, scratchFile))
//    } catch {
//      case ex: COSVisitorException => Left(InvalidInputPdf(inPdf, ex))
//    }
//  }
//}
