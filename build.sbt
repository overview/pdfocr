lazy val root = (project in file(".")).
  settings(
    organization := "com.overviewdocs",
    name := "pdfocr",
    version := "0.0.1",
    scalaVersion := "2.11.7",
    fork in Test := true,
    javaOptions in Test ++= Seq(
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=ERROR"
    ),
    libraryDependencies ++= Seq(
      "org.bouncycastle" % "bcmail-jdk15" % "1.44", // https://pdfbox.apache.org/1.8/dependencies.html
      "org.bouncycastle" % "bcprov-jdk15" % "1.44", // https://pdfbox.apache.org/1.8/dependencies.html
      "com.github.jai-imageio" % "jai-imageio-core" % "1.3.0", // for TIFF support
      "com.levigo.jbig2" % "levigo-jbig2-imageio" % "1.6.1",
      "org.apache.pdfbox" % "pdfbox" % "1.8.10",
      "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
      "org.slf4j" % "jcl-over-slf4j" % "1.7.12" % "test", // So we can mute warnings during testing
      "org.slf4j" % "slf4j-simple" % "1.7.12" % "test"    // So we can mute warnings during testing
    )
  )
