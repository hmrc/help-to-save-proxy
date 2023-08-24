import sbt.Keys.*
import sbt.*
import uk.gov.hmrc.DefaultBuildSettings.*
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import wartremover.{Wart, Warts}
import wartremover.WartRemover.autoImport.{wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save-proxy"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*config.*;.*(AuthService|BuildInfo|Routes|JsErrorOps|LoggingPagerDutyAlerting|Logging|DWPConnectionHealthCheck|HTSAuditor|OptionalAhcHttpCacheProvider).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

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
    .settings(scoverageSettings *)
    .settings(scalaSettings *)
    .settings(majorVersion := 2)
    .settings(defaultSettings() *)
    .settings(scalaVersion := "2.13.8")
    .settings(PlayKeys.playDefaultPort := 7005)
    .settings(wartRemoverSettings)
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    .settings(Test / compile / wartremoverErrors --= Seq(
      Wart.Any,
      Wart.Equals,
      Wart.Null,
      Wart.NonUnitStatements,
      Wart.PublicInference))
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
    .settings(scalacOptions ++= Seq("-Xcheckinit", "-feature"))
    .settings(scalacOptions += "-Wconf:src=routes/.*:s")
    .settings(Global / lintUnusedKeysOnLoad := false)
    .settings(classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary)
    .settings(classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat)
    .settings(Compile / doc / sources := Seq.empty)
