import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "help-to-save-proxy"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "auth-client" % "2.5.0",
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.15.0",
    "uk.gov.hmrc" %% "play-config" % "4.3.0",
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "org.typelevel" %% "cats" % "0.9.0",
    "com.github.kxbmap" %% "configs" % "0.4.4",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    "com.eclipsesource" %% "play-json-schema-validator" % "0.8.9",
    "uk.gov.hmrc" %% "mongo-lock" % "5.0.0"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.4" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "com.github.tomakehurst" % "wiremock" % "2.5.1" % scope,
    "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "com.miguno.akka" % "akka-mock-scheduler_2.11" % "0.5.1" % scope,
    "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % scope,
    "uk.gov.hmrc" %% "stub-data-generator" % "0.4.0" % scope
  )

}
