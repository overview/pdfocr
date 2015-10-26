package org.overviewproject.pdfocr.test

import org.scalatest.{FunSpec,MustMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Span,Millis}

abstract class UnitSpec extends FunSpec with MustMatchers with ScalaFutures with MockitoSugar {
  implicit override def patienceConfig = PatienceConfig(timeout = Span(2000, Millis), interval = Span(15, Millis))
}
