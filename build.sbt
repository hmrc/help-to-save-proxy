import sbt.*
import sbt.Keys.*
import uk.gov.hmrc.DefaultBuildSettings.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save-proxy"

lazy val microservice =
  Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) *)
    .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
    .settings(onLoadMessage := "")
    .settings(CodeCoverageSettings.settings *)
    .settings(scalaSettings *)
    .settings(majorVersion := 2)
    .settings(defaultSettings() *)
    .settings(scalaVersion := "2.13.12")
    .settings(PlayKeys.playDefaultPort := 7005)
    .settings(
      libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
    )
    .settings(
      retrieveManaged := true,
      update / evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
    )
    .settings(
      resolvers += "HMRC-open-artefacts-maven2" at "https://open.artefacts.tax.service.gov.uk/maven2"
    )
    .settings(scalacOptions ++= Seq("-Xcheckinit", "-feature", "-Xlint:-byname-implicit"))
    .settings(scalacOptions += "-Wconf:src=routes/.*:s")
    .settings(Global / lintUnusedKeysOnLoad := false)
    .settings(classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary)
    .settings(classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat)
    .settings(Compile / doc / sources := Seq.empty)
    // Disable default sbt Test options (might change with new versions of bootstrap)
    .settings(Test / testOptions -= Tests
      .Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
    // Suppress successful events in Scalatest in standard output (-o)
    // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
    .settings(Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"))
