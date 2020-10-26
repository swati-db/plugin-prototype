package com.myplugin.compiler

import com.google.protobuf.Descriptors.{Descriptor, MethodDescriptor, ServiceDescriptor}

case class Method(
                   name: String,
                   inputType: String,
                   outputType: String,
                   methodDescriptor: MethodDescriptor,
                   serviceDescriptor: ServiceDescriptor
                 )
case class GraphQLType(
                 descriptor: Descriptor,
                 modelDescriptor: ModelDescriptor
               )

case class ModelDescriptor(
                            packageDir: String,
                            typeName: String,
                            usedAsInput: Boolean,
                            packageName: String,
                          )

case class ParsedModels (
                          // per file
                          queries: scala.collection.mutable.Map[String, scala.collection.mutable.Seq[Method]] = scala.collection.mutable.Map[String, scala.collection.mutable.Seq[Method]](),
                          mutations: scala.collection.mutable.Map[String, scala.collection.mutable.Seq[Method]] = scala.collection.mutable.Map[String, scala.collection.mutable.Seq[Method]](),
                          // global
                          inputs: scala.collection.mutable.Map[String, GraphQLType] = scala.collection.mutable.Map[String, GraphQLType](),
                          types: scala.collection.mutable.Map[String, GraphQLType] = scala.collection.mutable.Map[String, GraphQLType](),
)