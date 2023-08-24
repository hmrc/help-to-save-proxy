import play.core.PlayVersion
import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                %% "domain"                     % "8.1.0-play-28",
    "uk.gov.hmrc"                %% "bootstrap-backend-play-28"  % "7.13.0",
    "uk.gov.hmrc.mongo"          %% "hmrc-mongo-play-28"         % "0.74.0",
    "com.github.java-json-tools" %% "json-schema-validator"      % "2.2.14",
    "org.typelevel"              %% "cats-core"                  % "2.9.0",
    "com.github.kxbmap"          %% "configs"                    % "0.6.1",
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"          %% "stub-data-generator"         % "1.1.0"             % "test",
    "org.scalatest"        %% "scalatest"                   % "3.2.15"            % "test",
    "com.vladsch.flexmark" %  "flexmark-all"                % "0.62.2"            % "test",
    "org.scalatestplus"    %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.pegdown"          %  "pegdown"                     % "1.6.0"             % "test",
    "com.typesafe.play"    %% "play-test"                   % PlayVersion.current % "test",
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-test-play-28"     % "0.74.0"            % "test",
    "org.scalamock"        %% "scalamock"                   % "5.2.0"             % "test",
    "com.miguno.akka"      %% "akka-mock-scheduler"         % "0.5.5"             % "test",
    "com.typesafe.akka"    %% "akka-testkit"                % "2.6.20"            % "test"
  )


}
