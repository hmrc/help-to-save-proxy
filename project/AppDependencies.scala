import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val bootstrapVersion = "8.4.0"
  val hmrcMongoVersion = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo"          %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "com.github.java-json-tools" %% "json-schema-validator"           % "2.2.14",
    "org.typelevel"              %% "cats-core"                       % "2.9.0",
    "com.github.kxbmap"          %% "configs"                         % "0.6.1"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"          %% s"bootstrap-test-$playVersion" % bootstrapVersion % "test",
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.18.0"       % "test",
    "org.scalamock"        %% "scalamock"                    % "5.2.0"          % "test",
    "com.github.pjfanning" %% "pekko-mock-scheduler"         % "0.6.0"          % "test",
    "org.apache.pekko"     %% "pekko-testkit"                % "1.0.2"          % "test"
  )
}
