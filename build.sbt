import sbt.Keys.*
import sbt.*
import uk.gov.hmrc.DefaultBuildSettings.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import wartremover.{Wart, Warts}
import wartremover.WartRemover.autoImport.{wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save-proxy"

lazy val wartRemoverSettings = {
  // list of warts here: http://www.wartremover.org/doc/warts.html
  val excludedWarts = Seq(
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.LeakingSealed,
    Wart.Nothing,
    Wart.Overloading,
    Wart.ToString,
    Wart.Var,
    Wart.NonUnitStatements,
    Wart.Null,
    Wart.StringPlusAny,
    Wart.Any,
    Wart.Equals,
  )

  Compile / compile / wartremoverErrors ++= Warts.allBut(excludedWarts *)
}

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
    .settings(wartRemoverSettings)
    .settings(
      wartremoverExcluded ++=
        (Compile / routes).value ++
          (baseDirectory.value ** "*.sc").get ++
          (baseDirectory.value ** "HealthCheck.scala").get ++
          (baseDirectory.value ** "HealthCheckRunner.scala").get ++
          (baseDirectory.value ** "Lock.scala").get ++
          Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala"))
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
