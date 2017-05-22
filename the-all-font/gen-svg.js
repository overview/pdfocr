#!/usr/bin/env node
'use strict'

const fs = require('fs')

function generate_svg() {
  const parts = [
    '<?xml version="1.0" standalone="yes"?>',
    '<svg version="1.1" xmlns="http://www.w3.org/2000/svg">',
    '  <defs>',
    '    <font id="pdfocr-stub-font" horiz-adv-x="1000">',
    '      <font-face font-family="pdfocr-stub-font" units-per-em="1000" ascent="1000" descent="0">',
    '        <font-face-src><font-face-name name="pdfocr-stub-font"/></font-face-src>',
    '      </font-face>',
    '      <missing-glyph d="M0,0"/>',
  ]

  for (let codePoint in require('unicode-9.0.0/Binary_Property/Assigned/code-points')) {
    if (codePoint < 0x20) continue
    if (codePoint > 0xd7ff && codePoint < 0xe000) continue // invalid XML
    if (codePoint > 0xfffd) continue // TTF CMAP seems to only allow 2-byte codepoints
    parts.push(`      <glyph unicode="&#${codePoint};" d="M0,0"/>`)
  }

  parts.push('    </font>')
  parts.push('  </defs>')
  parts.push('</svg>')

  return parts.join('\n')
}

function main() {
  const svg = generate_svg()
  fs.writeFileSync('./pdfocr-stub-font.svg', svg)
}

main()
