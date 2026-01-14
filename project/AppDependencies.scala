import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val bootstrapVersion = "10.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "org.typelevel"              %% "cats-core"                       % "2.13.0",
    "com.github.java-json-tools" % "json-schema-validator"           % "2.2.14"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"          %% s"bootstrap-test-$playVersion" % bootstrapVersion % "test",
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.18.0"       % "test"
  )
}
