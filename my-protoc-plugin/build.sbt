val Scala213 = "2.13.2"

val Scala212 = "2.12.10"

ThisBuild / organization := "com.example"

ThisBuild / scalaVersion := Scala213

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "my-protoc-plugin-core"
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGen = (projectMatrix in file("code-gen"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
     buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion),
     buildInfoPackage := "com.myplugin.compiler",
     libraryDependencies ++= Seq(
       "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion,
       "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
       "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
       "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
       "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % "1.17.0-0" % "protobuf",
     ),
    PB.targets in Compile := Seq(
      PB.gens.java -> (sourceManaged in Compile).value
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val codeGenJVM212 = codeGen.jvm(Scala212)

lazy val protocGenMyprotocplugin = protocGenProject("protoc-gen-my-protoc-plugin", codeGenJVM212)
  .settings(
    Compile / mainClass := Some("com.myplugin.compiler.CodeGenerator"),
    scalaVersion := Scala212
  )

lazy val e2e = (projectMatrix in file("e2e"))
  .dependsOn(core)
  .enablePlugins(LocalCodeGenPlugin)
  .settings(
    skip in publish := true,
    codeGenClasspath := (codeGenJVM212 / Compile / fullClasspath).value,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.9" % Test,
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value / "scalapb",
      PB.gens.java -> (sourceManaged in Compile).value,
      genModule("com.myplugin.compiler.CodeGenerator$") -> (sourceManaged in Compile).value / "scalapb"
    )
  )
  .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val root: Project =
  project
    .in(file("."))
    .settings(
      publishArtifact := false,
      publish := {},
      publishLocal := {}
    )
    .aggregate(protocGenMyprotocplugin.agg)
    .aggregate(
      codeGen.projectRefs ++
      core.projectRefs: _*
    )
