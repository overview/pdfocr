Use Tesseract to make a PDF searchable.

Installation
------------

Install [Tesseract](https://github.com/tesseract-ocr/tesseract) v3.0.5. This
library shells out to it.

Then install this package. Maven-style:

```xml
<dependency>
  <groupId>org.overviewproject</groupId>
  <artifactId>pdfocr_2.12</artifactId>
  <version>0.0.10</version>
</dependency>
```

Sbt-style:

```scala
dependencies += "org.overviewproject" %% "pdfocr" % "0.0.10"
```

Usage
-----

You've got to use Scala. Code something like this:

```scala
import java.nio.file.Path
import java.util.Locale
import org.overviewproject.pdfocr.{PdfOcr,PdfOcrProgress,PdfOcrResult}
import org.overviewproject.pdfocr.exceptions._
import scala.concurrent.Future

val pdfOcr = new PdfOcr()                          // default settings: finds tesseract in your $PATH
val inPdf = new Path("/path/to/needs-ocr.pdf")     // exists
val outPdf = new Path("/path/to/ocr-finished.pdf") // doesn't exist; will be deleted if it does
val process = PdfOcr.makePdfSearchable(inPdf, outPdf, Seq(Locale("en")))

process.progress // Future[PdfOcrProgress]
  .map { progress =>
    // It's a Future because we don't know how many pages there are until
    // we begin parsing the PDF, which takes time.

    progress.value       // 0.0 ... 1.0
    progress.currentPage // 1 .. nPages
    progress.nPages      // n
  }

process.result // Future[PdfTextResult]
  .map { result =>
    // do something with outPdf now...

    // Also, since the data is handy and would otherwise take a long time
    // to compute, PdfOcr returns the text, in pages.
    val text = result.pages.map(_.text).mkString("\n")
  }
  .recover {
    // outPdf is guaranteed not to exist

    case TesseractMissingException => throw
    case TesseractLanguageMissingException => throw
    case EncryptedPdfException => throw
    case InvalidPdfException => throw
    // Other errors may happen -- PDFBox bugs, Tesseract bugs,
    // out-of-memory.... You shouldn't catch those.
  }

// Or if you got impatient, you could:
process.cancel // Future[Unit]
```

How PdfOcr behaves
------------------

* PdfOcr processes one page at a time.
* PdfOcr sends Tesseract any page that's missing fonts or missing 100 characters of text.
* PdfOcr's progress reports are page-by-page. If one page needs OCR and nine don't, the progress report will be unintuitive.
* PdfOcr communicates with Tesseract via stdin and stdout.
* For any method that will block on I/O, PdfOcr returns a Future. In other words: blocking methods are asynchronous.
* PdfOcr does heavy computations (especially in `PdfPage`) which are slow. These are non-blocking and synchronous.

Developing
----------

First, [Install sbt](http://www.scala-sbt.org/download.html).

After that, 

1. Run `sbt ~test` to run unit tests in the background.
2. Edit files in `src/test` until a test fails.
3. Edit files in `src/main` until the test passes.
4. Return to step 2.
5. Commit to a git branch, push it to GitHub, and submit a pull request.

Publishing
----------

We use [sbt-sonatype](https://github.com/xerial/sbt-sonatype for more details) for all this.

Setup: using the sbt-sonatype instructions, ensure you've done these things:

* Created an account at https://oss.sonatype.org and get access to this project.
* Created `~/.sbt/1.0/sonatype.sbt` with your credentials.

Then, every new version:

1. `sbt publishSigned` to deploy to staging
2. `sbt sonatypeRelease` to close and promote it

If the version ends in `-SNAPSHOT`, you won't be able to release it.

License
-------

This software is Copyright 2011-2018 The Associated Press, and distributed under the
terms of the GNU Affero General Public License. See the LICENSE file for details.
