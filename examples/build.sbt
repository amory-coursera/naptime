name := "examples"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  courierRuntime,
  ehcache,
  sangria
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)

org.coursera.courier.sbt.CourierPlugin.courierSettings

sourceDirectories in (Compile, TwirlKeys.compileTemplates) := (unmanagedSourceDirectories in Compile).value

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
//PB.protoSources in Compile += (baseDirectory in LocalRootProject).value / "src/main/protobuf"
