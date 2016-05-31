name := "trees"

organization := "org.piotrm"

version := "0.1"

scalaVersion := "2.11.8"

isSnapshot := true

unmanagedClasspath in Runtime += baseDirectory.value / "../lib"
unmanagedClasspath in (Compile, runMain) += baseDirectory.value / "../lib"
scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-Xlint"
)

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.clapper" %% "argot" % "1.0.3",
  "commons-io" % "commons-io" % "2.4",
  "org.apache.spark"  %% "spark-mllib"         % "1.6.1",
  "com.databricks"    % "spark-csv_2.11"       % "1.4.0"
)

showSuccess := false
//logLevel in run := Level.Warn

fork in run := true
