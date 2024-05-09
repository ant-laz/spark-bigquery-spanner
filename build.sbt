//do not use "bare style" for build.sbt
//instead use "multi project" for our build.sbt
lazy val root = (project in file(".")).
    settings(
      inThisBuild(List(
       organization := "org.tonz.com",
       //version of scala compatible with latest dataproc images
       scalaVersion := "2.12.18"
     )),
     name := "spark-bigquery-spanner"
   )

// we need to use spark & spanner jdbc in this project
val sparkVersion = "3.5.0"
val sparkJDBC = "2.17.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql"  % sparkVersion,
  "com.google.cloud" % "google-cloud-spanner-jdbc" % sparkJDBC
)


// ============================================================================

// Most moderately interesting Scala projects don't make use of the very simple
// build file style (called "bare style") used in this build.sbt file. Most
// intermediate Scala projects make use of so-called "multi-project" builds. A
// multi-project build makes it possible to have different folders which sbt can
// be configured differently for. That is, you may wish to have different
// dependencies or different testing frameworks defined for different parts of
// your codebase. Multi-project builds make this possible.

// Here's a quick glimpse of what a multi-project build looks like for this
// build, with only one "subproject" defined, called `root`:

// lazy val root = (project in file(".")).
//   settings(
//     inThisBuild(List(
//       organization := "ch.epfl.scala",
//       scalaVersion := "2.13.12"
//     )),
//     name := "hello-world"
//   )

// To learn more about multi-project builds, head over to the official sbt
// documentation at http://www.scala-sbt.org/documentation.html
