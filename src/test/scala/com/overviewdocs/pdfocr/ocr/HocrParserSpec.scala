package com.overviewdocs.pdfocr.pdf

import java.awt.Rectangle
import java.io.{ByteArrayInputStream,InputStream}

import com.overviewdocs.pdfocr.test.UnitSpec

class HocrParserSpec extends UnitSpec {
  // Simple HTML-generating functions. They don't escape anything, so be nice.

  def hocr(contents: String*): String = s"""<?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
      "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
    <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
    <head>
      <title>
        </title>
        <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
        <meta name='ocr-system' content='tesseract 3.04.00' />
        <meta name='ocr-capabilities' content='ocr_page ocr_carea ocr_par ocr_line ocrx_word'/>
      </head>
      <body>
        <div class='ocr_page' id='page_1' title='image "page.png"; bbox 0 0 100 200; ppageno 0'>
          ${contents.mkString}
        </div>
      </body>
    </html>"""

  def area(contents: String*): String = s"""
    <div class='ocr_carea' id='block_1_4' title="bbox 0 0 100 200">
      <p class='ocr_par' dir='ltr' id='par_1_4' title="bbox 0 0 100 200">
        ${contents.mkString}
      </p>
    </div>"""

  def line(title: String, contents: String*): String = s"""
    <span class='ocr_line' id='line_1_24' title="$title">${contents.mkString(" ")}
    </span>"""

  def word(title: String, text: String): String = {
    s"""<span class='ocrx_word' id='word_1_326' title='$title' lang='eng' dir='ltr'>$text</span>"""
  }

  def input(string: String): InputStream = new ByteArrayInputStream(string.getBytes("utf-8"))

  it("should parse a word") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4", word("bbox 5 6 7 8", "aWord"))
    ))))
    val result = parser.toSeq
    result must equal(Seq(HocrLine(new Rectangle(1, 2, 2, 2), Seq(HocrWord(new Rectangle(5, 6, 2, 2), "aWord")))))
  }

  it("should allow multiple words per line") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4",
        word("bbox 5 6 7 8", "word1"),
        word("bbox 6 7 8 9", "word2")
      )
    ))))
    val result = parser.toSeq
    result must equal(Seq(HocrLine(new Rectangle(1, 2, 2, 2), Seq(
      HocrWord(new Rectangle(5, 6, 2, 2), "word1"),
      HocrWord(new Rectangle(6, 7, 2, 2), "word2")
    ))))
  }

  it("should split by line") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4", word("bbox 5 6 7 8", "word1")),
      line("bbox 2 3 4 5", word("bbox 6 7 8 9", "word2"))
    ))))
    val result = parser.toSeq
    result must equal(Seq(
      HocrLine(new Rectangle(1, 2, 2, 2), Seq(HocrWord(new Rectangle(5, 6, 2, 2), "word1"))),
      HocrLine(new Rectangle(2, 3, 2, 2), Seq(HocrWord(new Rectangle(6, 7, 2, 2), "word2")))
    ))
  }

  it("should handle multiple areas") {
    val parser = new HocrParser(input(hocr(
      area(line("bbox 1 2 3 4", word("bbox 5 6 7 8", "word1"))),
      area(line("bbox 2 3 4 5", word("bbox 6 7 8 9", "word2")))
    )))
    val result = parser.toSeq
    result must equal(Seq(
      HocrLine(new Rectangle(1, 2, 2, 2), Seq(HocrWord(new Rectangle(5, 6, 2, 2), "word1"))),
      HocrLine(new Rectangle(2, 3, 2, 2), Seq(HocrWord(new Rectangle(6, 7, 2, 2), "word2")))
    ))
  }

  it("should parse as UTF-8") {
    val utf8Stuff = "ᚠ б ვ ಇ म ខ្ ញុំ ཤེ 我"
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4", word("bbox 5 6 7 8", utf8Stuff))
    ))))
    val result = parser.toSeq
    result.headOption.flatMap(_.words.headOption).map(_.text) must equal(Some(utf8Stuff))
  }

  it("should trim whitespace at the edge of a word") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4", word("bbox 5 6 7 8", "  whee\n"))
    ))))
    val result = parser.toSeq
    result.headOption.flatMap(_.words.headOption).map(_.text) must equal(Some("whee"))
  }

  it("should ignore words that only contain whitespace") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4",
        word("bbox 3 4 5 6", " "),
        word("bbox 5 6 7 8", "whee"),
        word("bbox 6 7 8 9", "\n")
      )
    ))))
    val result = parser.toSeq
    result.headOption.flatMap(_.words.headOption).map(_.text) must equal(Some("whee"))
  }

  it("should ignore lines that only contain whitespace") {
    val parser = new HocrParser(input(hocr(area(
      line("bbox 1 2 3 4",
        word("bbox 3 4 5 6", " "),
        word("bbox 6 7 8 9", "\n")
      )
    ))))
    val result = parser.toSeq
    result must equal(Seq())
  }
}
