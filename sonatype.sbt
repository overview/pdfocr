sonatypeProfileName := "org.overviewproject"

publishMavenStyle := true

licenses := Seq("AGPL3" -> url("http://www.gnu.org/licenses/agpl-3.0.txt"))

homepage := Some(url("https://www.overviewdocs.com"))
scmInfo := Some(ScmInfo(
  url("https://github.com/overview/pdfocr"),
  "scm:git@github.com:overview/pdfocr.git"
))

developers := List(
  Developer(id="adam@adamhooper.com", name="Adam Hooper", email="adam@adamhooper.com", url=url("http://adamhooper.com"))
)
