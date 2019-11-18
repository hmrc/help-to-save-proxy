import play.core.PlayVersion
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.{SbtAutoBuildPlugin, _}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}

val ScalatestVersion = "3.0.4"
val test = "test"

val appName = "help-to-save-proxy"

lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val dependencies = Seq(
  ws,
  "uk.gov.hmrc" %% "auth-client" % "2.31.0-play-26",
  "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
  "org.typelevel" %% "cats-core" % "2.0.0",
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-26",
  "com.eclipsesource" %% "play-json-schema-validator" % "0.9.4",
  "uk.gov.hmrc" %% "mongo-lock" % "6.15.0-play-26",
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.1.0"
)

lazy val testDependencies = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % test,
  "org.scalatest" %% "scalatest" % "3.0.8" % test,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % test,
  "com.miguno.akka" %% "akka-mock-scheduler" % "0.5.5" % test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.23" % test,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % test
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*config.*;.*(AuthService|BuildInfo|Routes|JsErrorOps|LoggingPagerDutyAlerting|Logging|DWPConnectionHealthCheck|HTSAuditor|OptionalAhcHttpCacheProvider).*",
    ScoverageKeys.coverageMinimum := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
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
    Wart.Null)

  wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*)
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"))
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(majorVersion := 2)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7005)
  .settings(scalafmtOnCompile := true)
  .settings(wartRemoverSettings)
  // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
  // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
  // imcompatible with a lot of WordSpec
  .settings(wartremoverErrors in (Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference))
  .settings(wartremoverExcluded ++=
    routes.in(Compile).value ++
      (baseDirectory.value ** "*.sc").get ++
      (baseDirectory.value ** "HealthCheck.scala").get ++
      (baseDirectory.value ** "HealthCheckRunner.scala").get ++
      (baseDirectory.value ** "Lock.scala").get ++
      Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
  .settings(
    libraryDependencies ++= dependencies ++ testDependencies
  )
  .settings(
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo,
    "emueller-bintray" at "http://dl.bintray.com/emueller/maven" // for play json schema validator
  ))
  .settings(scalacOptions += "-Xcheckinit")

lazy val compileAll = taskKey[Unit]("Compiles sources in all configurations.")

compileAll := {
  val a = (compile in Test).value
  ()
}
