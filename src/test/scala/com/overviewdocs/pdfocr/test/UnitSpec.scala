package com.overviewdocs.pdfocr.test

import org.scalatest.{BeforeAndAfter,FunSpec,MustMatchers}
import org.scalatest.concurrent.ScalaFutures

abstract class UnitSpec extends FunSpec with MustMatchers with ScalaFutures
