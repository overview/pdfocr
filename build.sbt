// Metadata

name := "pdfocr"

version := "0.0.7"

homepage := Some(url("https://github.com/overview/pdfocr"))

organization := "org.overviewproject"

organizationName := "Overview Services Inc."

organizationHomepage := Some(url("https://www.overviewdocs.com"))

description := "Library that shells to Tesseract to make PDFs searchable"

licenses += "AGPLv3" -> url("http://www.gnu.org/licenses/agpl-3.0.html")

fork in (Compile, run) := true // Main calls System.exit() to give proper return codes


// Compile settings

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.11.8", "2.12.2")

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "org.bouncycastle" % "bcmail-jdk15on" % "1.54", // https://pdfbox.apache.org/2.0/dependencies.html
  "org.bouncycastle" % "bcprov-jdk15on" % "1.54", // https://pdfbox.apache.org/2.0/dependencies.html
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.54", // https://pdfbox.apache.org/2.0/dependencies.html
  "com.github.jai-imageio" % "jai-imageio-core" % "1.3.1", // for TIFF support
  "com.github.jai-imageio" % "jai-imageio-jpeg2000" % "1.3.0", // for JPEG2000 support
  "com.levigo.jbig2" % "levigo-jbig2-imageio" % "2.0",
  "org.apache.pdfbox" % "pdfbox" % "2.0.9",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.mockito" % "mockito-core" % "2.12.0" % "test",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.25" % "test", // So we can mute warnings during testing
  "org.slf4j" % "slf4j-simple" % "1.7.25" % "test"    // So we can mute warnings during testing
)


// Test settings

fork in Test := true

javaOptions in Test ++= Seq("-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO")

testOptions in Test += Tests.Argument("-oDF")

publishTo := Some(Opts.resolver.sonatypeStaging)
