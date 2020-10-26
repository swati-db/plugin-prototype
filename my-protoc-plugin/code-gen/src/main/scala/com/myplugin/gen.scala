package com.myplugin

import protocbridge.Artifact
import scalapb.GeneratorOption
import protocbridge.SandboxedJvmGenerator

object gen {
  def apply(
      options: GeneratorOption*
  ): (SandboxedJvmGenerator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "scala",
        Artifact(
          com.myplugin.compiler.BuildInfo.organization,
          "my-protoc-plugin-codegen_2.12",
          com.myplugin.compiler.BuildInfo.version
        ),
        "com.myplugin.compiler.CodeGenerator$",
        com.myplugin.compiler.CodeGenerator.suggestedDependencies
      ),
      options.map(_.toString)
    )

  def apply(
      options: Set[GeneratorOption] = Set.empty
  ): (SandboxedJvmGenerator, Seq[String]) = apply(options.toSeq: _*)
}