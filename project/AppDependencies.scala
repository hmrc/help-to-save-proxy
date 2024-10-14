import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val bootstrapVersion = "9.0.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "com.github.java-json-tools" %% "json-schema-validator"           % "2.2.14",
    "org.typelevel"              %% "cats-core"                       % "2.12.0",
    "com.github.kxbmap"          %% "configs"                         % "0.6.1"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"          %% s"bootstrap-test-$playVersion" % bootstrapVersion % "test",
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.18.0"       % "test",
    "org.mockito"          %% "mockito-scala"                % "1.17.37"        % "test"
  )
}
