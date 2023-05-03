import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "domain"                     % "8.1.0-play-28",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"  % "7.13.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.74.0",
    "com.eclipsesource" %% "play-json-schema-validator" % "0.9.4",
    "org.typelevel"     %% "cats-core"                  % "2.9.0",
    "com.github.kxbmap" %% "configs"                    % "0.6.1",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.12" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.12" % Provided cross CrossVersion.full
  )

  val test = Seq(
    "uk.gov.hmrc"          %% "stub-data-generator"         % "0.5.3"             % "test",
    "org.scalatest"        %% "scalatest"                   % "3.2.15"            % "test",
    "com.vladsch.flexmark" %  "flexmark-all"                % "0.62.2"            % "test",
    "org.scalatestplus"    %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.pegdown"          %  "pegdown"                     % "1.6.0"             % "test",
    "com.typesafe.play"    %% "play-test"                   % PlayVersion.current % "test",
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-test-play-28"     % "0.74.0"            % "test",
    "org.scalamock"        %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.miguno.akka"      %% "akka-mock-scheduler"         % "0.5.5"             % "test",
    "com.typesafe.akka"    %% "akka-testkit"                % "2.6.20"            % "test"
  )

}
