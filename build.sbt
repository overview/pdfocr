// Metadata

name := "pdfocr"

version := "0.0.1-SNAPSHOT"

organization := "overviewproject.org"

organizationName := "Overview Services Inc."

organizationHomepage := Some(url("https://www.overviewdocs.com"))

description := "Library that shells to Tesseract to make PDFs searchable"

licenses += "AGPLv3" -> url("http://www.gnu.org/licenses/agpl-3.0.html")


// Compile settings

resolvers += "ApacheSnapshot" at "https://repository.apache.org/content/groups/snapshots/"

scalaVersion := "2.11.7"

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "org.bouncycastle" % "bcmail-jdk15" % "1.44", // https://pdfbox.apache.org/1.8/dependencies.html
  "org.bouncycastle" % "bcprov-jdk15" % "1.44", // https://pdfbox.apache.org/1.8/dependencies.html
  "com.github.jai-imageio" % "jai-imageio-core" % "1.3.0", // for TIFF support
  "com.levigo.jbig2" % "levigo-jbig2-imageio" % "1.6.1",
  "org.apache.pdfbox" % "pdfbox" % "2.0.0-SNAPSHOT", // using local copy right now, pending fix for https://issues.apache.org/jira/browse/PDFBOX-3001
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.12" % "test", // So we can mute warnings during testing
  "org.slf4j" % "slf4j-simple" % "1.7.12" % "test"    // So we can mute warnings during testing
)


// Test settings

fork in Test := true

javaOptions in Test ++= Seq("-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO")

testOptions in Test += Tests.Argument("-oDF")


// Publish settings
