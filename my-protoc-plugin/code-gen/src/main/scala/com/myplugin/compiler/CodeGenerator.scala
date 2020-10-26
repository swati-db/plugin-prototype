package com.myplugin.compiler

import com.google.protobuf.Descriptors.FieldDescriptor.Type
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.Descriptors._
import protocbridge.Artifact
import protocgen.{CodeGenApp, CodeGenRequest, CodeGenResponse}
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, ProtobufGenerator}
import scalapb.options.compiler.Scalapb

import scala.collection.JavaConverters._


object CodeGenerator extends CodeGenApp {
  override def registerExtensions(registry: ExtensionRegistry): Unit = {
    Scalapb.registerAllExtensions(registry)
  }

  // When your code generator will be invoked from SBT via sbt-protoc, this will add the following
  // artifact to your users build whenever the generator is used in `PB.targets`:
  override def suggestedDependencies: Seq[Artifact] =
    Seq(
      Artifact(
        BuildInfo.organization,
        "my-protoc-plugin-core",
        BuildInfo.version,
        crossVersion = true
      )
    )

  // This is called by CodeGenApp after the request is parsed.
  def process(request: CodeGenRequest): CodeGenResponse =
    ProtobufGenerator.parseParameters(request.parameter) match {
      case Right(params) =>

        // Implicits gives you extension methods that provide ScalaPB names and types
        // for protobuf entities.
        val implicits =
          new DescriptorImplicits(params, request.allProtos)

        val responseFile = CodeGeneratorResponse.File.newBuilder()
          .setName("scalapb/com/myplugin/schema.gql")
          .setContent(new Plugin(request, implicits).content)
          .build()
        CodeGenResponse.succeed(Seq(responseFile))
      case Left(error)   =>
        CodeGenResponse.fail(error)
		}
}


class FileProcessor(p: ParsedModels, file: FileDescriptor, implicits: DescriptorImplicits, r: CodeGenRequest) {
  def initFile(): Unit = {
    val res:(Seq[String], Seq[String]) = defineMethods
    for (inputType <- res._1) {
      fillTypeMaps(inputType, p.inputs, inputField = true)
    }
    for (outputType <- res._2) {
      fillTypeMaps(outputType, p.types, inputField = false)
    }
  }

  def getKey: String = {
    file.getFullName
  }

  def generateContent: String = {
    val sb:StringBuffer = new StringBuffer()
    for ((typeName, t) <- p.inputs) {
      sb.append(s"input $typeName {")
      for(field <- t.descriptor.getFields.asScala) {
        sb.append("\n\t").append(s"${field.getName}: ${graphQLType(field)}")
      }
      sb.append("\n}\n")
    }
    for ((typeName, t) <- p.types) {
      sb.append(s"type $typeName {")
      for(field <- t.descriptor.getFields.asScala) {
        sb.append("\n\t").append(s"${field.getName}: ${graphQLType(field)}")
      }
      sb.append("\n}\n")
    }
    if (p.queries(getKey).nonEmpty) {
      sb.append("type Query {")
      sb.append("\n\t")
      sb.append(renderMethod(p.queries(getKey)))
      sb.append("\n}\n")
    }
    // TODO: check why functionalPrinter doesn't work??
    // fp.add()
    /*for ((typeName, t) <- p.inputs) {
      fp.add(s"input $typeName {").indent
        for(field <- t.descriptor.getFields.asScala) {
          fp.add(s"${field.getName}: ${graphQLType(field)}")
        }
      fp.outdent.add("}").add("")
    }
    for ((typeName, t) <- p.types) {
      fp.add(s"type $typeName {").indent
      for(field <- t.descriptor.getFields.asScala) {
        fp.add(s"${field.getName}: ${graphQLType(field)}")
      }
      fp.outdent.add("}").add("")
    }*/
    // fp.result()
    sb.toString
  }

  def renderMethod(methods: scala.collection.mutable.Seq[Method]): String = {
    val sb: StringBuffer = new StringBuffer()
    for (m <- methods) {
      val in: String = "(in: " + m.inputType + ")"
      sb.append(m.name).append(in).append(": ").append(m.outputType)
    }
    sb.toString
  }

  def graphQLType(field: FieldDescriptor): String = {
    field.getType match {
      case Type.STRING => "String"
    }
  }

  def isAlreadyDefined(typeName: String, inputField: Boolean): Boolean = {
    if (inputField) {
      p.inputs.contains(typeName)
    }
    p.types.contains(typeName)
  }

  def fillTypeMaps(typeName: String, objects: scala.collection.mutable.Map[String, GraphQLType], inputField: Boolean): Unit = {
    if (isAlreadyDefined(typeName, inputField)) {
      return
    }
    // this is a message, so add it to the types
    for (f <- r.filesToGenerate) {
      for(messageType <- f.getMessageTypes.asScala) {
        if (typeName == messageType.getName) {
          objects += (typeName -> GraphQLType(
            messageType,
            ModelDescriptor(
              messageType.getFullName, // Need to be better
              messageType.getName,
              inputField,
              messageType.getFullName, // Need to be better
            )
          ))
        }
      }
    }
  }

  def defineMethods: (Seq[String], Seq[String]) = {
    var inputs:Seq[String] = Seq()
    var outputs:Seq[String] = Seq()

    for (svc <- file.getServices.asScala) {
      for (rpc <- svc.getMethods.asScala) {
        val method: Method = Method(
          constructMethodName(svc.getName, rpc.getName),
          rpc.getInputType.getName,
          rpc.getOutputType.getName,
          rpc,
          svc,
        )
        // TODO: get the right type
        p.queries(getKey) = p.queries(getKey) :+ method
        inputs = inputs :+ rpc.getInputType.getName
        outputs = outputs :+ rpc.getOutputType.getName
      }
    }
    (inputs, outputs)
  }

  def constructMethodName(svcName: String, rpcName: String): String = {
    if (svcName.length > 0) {
      return svcName.substring(0, 1).toLowerCase() + svcName.substring(1) + rpcName
    }
    ""
  }
}

class Plugin(request: CodeGenRequest, implicits: DescriptorImplicits) {
  var parsedModels = ParsedModels()
  def content: String = {
    val sb: StringBuilder = new StringBuilder
    sb.append("# Code generated by plugin. DO NOT EDIT \n")
    for (file <- request.filesToGenerate) {
      if (file.getName != "gql.proto") {
        // initialize the queries and mutations for this key
        parsedModels.queries += (file.getFullName -> scala.collection.mutable.Seq())
        parsedModels.mutations += (file.getFullName -> scala.collection.mutable.Seq())
        val fileProcessor = new FileProcessor(parsedModels, file, implicits, request)
        fileProcessor.initFile()
        sb.append(fileProcessor.generateContent)
        sb.append("\n")
      }
    }
    sb.toString()
  }
}
/*class MessagePrinter(message: Descriptor, implicits: DescriptorImplicits) {
  import implicits._
  private val MessageObject =
    message.scalaType.sibling(message.scalaType.name + "FieldNums")

  def scalaFileName =
    MessageObject.fullName.replace('.', '/') + ".scala"

  def result: CodeGeneratorResponse.File = {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setName(scalaFileName)
    b.setContent(content)
    b.build()
  }

  def printObject(fp: FunctionalPrinter): FunctionalPrinter =
    fp
      .add(s"object ${MessageObject.name} {")
      .indented(
        _.print(message.getFields().asScala){ (fp, fd) => printField(fp, fd) }
        .add("")
        .print(message.getNestedTypes().asScala) {
          (fp, m) => new MessagePrinter(m, implicits).printObject(fp)
        }
      )
      .add("}")

  def printField(fp: FunctionalPrinter, fd: FieldDescriptor): FunctionalPrinter =
    fp.add(s"val ${fd.getName} = ${fd.getNumber}")

  def content: String = {
    val fp = new FunctionalPrinter()
    .add(
      s"package ${message.getFile.scalaPackage.fullName}",
      "",
    ).call(printObject)
    fp.result
  }
}*/
