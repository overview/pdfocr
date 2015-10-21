package org.overviewproject.pdfocr.test

import org.scalatest.{BeforeAndAfter,FunSpec,MustMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Span,Millis}

abstract class UnitSpec extends FunSpec with MustMatchers with ScalaFutures {
  implicit override def patienceConfig = PatienceConfig(timeout = Span(2000, Millis), interval = Span(15, Millis))
}
