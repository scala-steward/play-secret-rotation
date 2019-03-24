import ReleaseTransformations._

lazy val baseSettings = Seq(
  scalaVersion := "2.12.8",
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-Xlint", "-unchecked")
)

lazy val core =
  project.settings(baseSettings: _*).settings(
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "2.6.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.threeten" % "threeten-extra" % "1.5.0",
      "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    )
  )

lazy val `aws-parameterstore-secret-supplier-base` =
  project.in(file("aws-parameterstore/secret-supplier")).settings(baseSettings: _*).dependsOn(core)

val awsSdkForVersion = Map(
  1 -> "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.524",
  2 -> "software.amazon.awssdk" % "ssm" % "2.5.15"
)

def awsParameterStoreWithSdkVersion(version: Int)=
  Project(s"aws-parameterstore-sdk-v$version", file(s"aws-parameterstore/secret-supplier/aws-sdk-v$version"))
  .settings(baseSettings: _*)
  .dependsOn(`aws-parameterstore-secret-supplier-base`)
  .settings(libraryDependencies += awsSdkForVersion(version))

lazy val `aws-parameterstore-sdk-v1` = awsParameterStoreWithSdkVersion(1)
lazy val `aws-parameterstore-sdk-v2` = awsParameterStoreWithSdkVersion(2)

lazy val `aws-parameterstore-lambda` = project.in(file("aws-parameterstore/lambda"))
  .settings(baseSettings: _*).dependsOn(`secret-generator`).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
    "com.amazonaws" % "aws-lambda-java-events" % "2.0.2",
    awsSdkForVersion(1)
  )
)

lazy val `secret-generator` = project.settings(baseSettings: _*)

val exactPlayVersions = Map(
  "26" -> "2.6.23",
  "27" -> "2.7.3"
)

def playVersion(majorMinorVersion: String)= {
  Project(s"play-v$majorMinorVersion", file(s"play/play-v$majorMinorVersion"))
    .settings(baseSettings: _*)
    .dependsOn(core)
    .settings(libraryDependencies += "com.typesafe.play" %% "play" % exactPlayVersions(majorMinorVersion))
}

lazy val `play-v26` = playVersion("26")
lazy val `play-v27` = playVersion("27")

lazy val `play-secret-rotation-root` = (project in file("."))
  .aggregate(
    core,
    `play-v26`,
    `play-v27`,
    `aws-parameterstore-secret-supplier-base`,
    `aws-parameterstore-sdk-v1`,
    `aws-parameterstore-sdk-v2`,
    `aws-parameterstore-lambda`
  )
  .settings(baseSettings: _*).settings(
  publishArtifact := false,
  publish := {},
  publishLocal := {},
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
)

assemblyMergeStrategy in assembly := {
  {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
}
