This code generates ../src/main/resources/ocr-font.ttf.

When we OCR the document, we'll draw text on top of existing PDF. Users don't
need to _see_ anything: they just need to select Unicode codepoints.

Here are the goals of this font:

* It's invisible
* It loads as quickly as possible
* It's a reasonably small file
* It supports all Unicode codepoints
* PDFBox can embed it into PDF documents
* Common PDF readers let users copy/paste text from it

# To Generate

1. Install [NodeJs](https://nodejs.org) 7.0+ and [FontForge](http://fontforge.github.io/en-US/)
2. `npm install`
2. Run `./gen-svg.js`
3. Open `pdfocr-stub-font.svg` in FontForge and export it as a TTF
