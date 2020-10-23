import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    ws,
    hmrc                %% "auth-client"                % "3.2.0-play-26",
    hmrc                %% "domain"                     % "5.10.0-play-26",
    hmrc                %% "mongo-lock"                 % "6.23.0-play-26",
    hmrc                %% "bootstrap-backend-play-26"  % "2.25.0",
    hmrc                %% "simple-reactivemongo"       % "7.30.0-play-26",
    "com.eclipsesource" %% "play-json-schema-validator" % "0.9.4",
    "org.typelevel"     %% "cats-core"                  % "2.0.0",
    "com.github.kxbmap" %% "configs"                    % "0.4.4"
  )

  val test = Seq(
    hmrc                   %% "service-integration-test"    % "0.12.0-play-26"    % "test",
    hmrc                   %% "stub-data-generator"         % "0.5.3"             % "test",
    "org.scalatest"        %% "scalatest"                   % "3.2.0"             % "test",
    "com.vladsch.flexmark" % "flexmark-all"                 % "0.35.10"           % "test",
    "org.scalatestplus"    %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.pegdown"          % "pegdown"                      % "1.6.0"             % "test",
    "com.typesafe.play"    %% "play-test"                   % PlayVersion.current % "test",
    "org.scalamock"        %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.miguno.akka"      %% "akka-mock-scheduler"         % "0.5.5"             % "test",
    "com.typesafe.akka"    %% "akka-testkit"                % "2.5.23"            % "test"
  )

  val akkaVersion = "2.5.23"
  val akkaHttpVersion = "10.0.15"

  val overrides = Seq(
    "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
    "com.typesafe.akka" %% "akka-protobuf"  % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
    "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
  )

}
