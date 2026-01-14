import sbt.*
import sbt.Keys.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save-proxy"

lazy val microservice =
  Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
    .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
    .settings(onLoadMessage := "")
    .settings(CodeCoverageSettings.settings *)
    .settings(majorVersion := 2)
    .settings(scalaVersion := "3.3.5")
    .settings(PlayKeys.playDefaultPort := 7005)

    .settings(
      libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
    )
    .settings(scalacOptions += "-Wconf:src=routes/.*:s",
      scalacOptions += "-Wconf:msg=Flag.*repeatedly:s")
    // Disable default sbt Test options (might change with new versions of bootstrap)
    .settings(Test / testOptions -= Tests
      .Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
    // Suppress successful events in Scalatest in standard output (-o)
    // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
    .settings(Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"))
