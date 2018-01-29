//import play.core.PlayVersion
//import sbt.Keys.version
//
//val ScalatestVersion = "3.0.4"
//val hmrcRepoHost = java.lang.System.getProperty("hmrc.repo.host", "https://nexus-preview.tax.service.gov.uk")
//val test = "test"
//
//lazy val root = (project in file("."))
//  .settings(
//    name := "help-to-save-proxy",
//    version := "0.0.1",
//    scalaVersion := "2.11.11",
//    credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
//    scalacOptions ++= Seq("-unchecked", "-deprecation"),
//    resolvers ++= Seq(
//      Resolver.bintrayRepo("hmrc", "releases"),
//      Resolver.jcenterRepo,
//      "hmrc-snapshots" at hmrcRepoHost + "/content/repositories/hmrc-snapshots",
//      "hmrc-releases" at hmrcRepoHost + "/content/repositories/hmrc-releases",
//      "typesafe-releases" at hmrcRepoHost + "/content/repositories/typesafe-releases"),
//    libraryDependencies ++= Seq(
//      "uk.gov.hmrc" %% "auth-client" % "2.5.0",
//      "uk.gov.hmrc" %% "microservice-bootstrap" % "6.15.0",
//      "uk.gov.hmrc" %% "play-config" % "4.3.0",
//      "uk.gov.hmrc" %% "domain" % "5.1.0",
//      "org.typelevel" %% "cats" % "0.9.0",
//      "com.github.kxbmap" %% "configs" % "0.4.4",
//      "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
//      "com.eclipsesource" %% "play-json-schema-validator" % "0.8.9",
//      "uk.gov.hmrc" %% "mongo-lock" % "5.0.0",
//      "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % test,
//      "org.scalatest" %% "scalatest" % "3.0.4" % test,
//      "org.pegdown" % "pegdown" % "1.6.0" % test,
//      "com.typesafe.play" %% "play-test" % PlayVersion.current % test,
//      "com.github.tomakehurst" % "wiremock" % "2.5.1" % test,
//      "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % test,
//      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % test,
//      "com.miguno.akka" % "akka-mock-scheduler_2.11" % "0.5.1" % test,
//      "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % test,
//      "uk.gov.hmrc" %% "stub-data-generator" % "0.4.0" % test
//    ),
//    //inConfig(Defaults.testTasks),
//    unmanagedSourceDirectories in Test += baseDirectory.value / "src/test/scala",
//    unmanagedResourceDirectories in Test += baseDirectory.value / "src/test/resources"
//  )

//NEED TO GET SBT TEST WORKING WITH THIS FILE!!!!!