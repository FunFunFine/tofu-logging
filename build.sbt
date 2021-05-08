import Publish._
import Dependencies._
import com.typesafe.sbt.SbtGit.git

name := "tofu-logging"

version := "0.1"

scalaVersion := "2.13.5"

lazy val loggingStructured = project
  .in(file("structured"))
  .settings(
    publishName := "logging-structured",
    defaultSettings,
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      circeCore,
      tethys,
      tethysJackson,
      slf4j,
      alleycats,
      scalatest,
      derevo,
      catsTagless
    ),
    libraryDependencies ++= Seq(Tofu.core, Tofu.concurrent, Tofu.data)
  )

lazy val loggingDerivation = project
  .in(file("derivation"))
  .dependsOn(loggingStructured)
  .settings(libraryDependencies ++= Seq(Tofu.opticsMacro % "compile->test", Tofu.derivation % "compile->test"))
  .settings(
    defaultSettings,
    libraryDependencies ++= Seq(derevo, magnolia, slf4j),
    publishName := "logging-derivation"
  )

lazy val loggingLayout = project
  .in(file("layout"))
  .settings(
    defaultSettings,
    libraryDependencies ++= Seq(catsCore, catsEffect, logback, slf4j),
    publishName := "logging-layout"
  )
  .dependsOn(loggingStructured)

lazy val loggingUtil = project
  .in(file("util"))
  .settings(
    defaultSettings,
    publishName := "logging-util",
    libraryDependencies += slf4j,
    libraryDependencies += (Tofu.concurrent)
  )
  .dependsOn(loggingStructured)

lazy val loggingShapeless = project
  .in(file("interop/shapeless"))
  .settings(
    defaultSettings,
    publishName := "logging-shapeless",
    libraryDependencies += shapeless
  )
  .dependsOn(loggingStructured)

lazy val loggingRefined = project
  .in(file("interop/refined"))
  .settings(
    defaultSettings,
    publishName := "logging-refined",
    libraryDependencies += refined
  )
  .dependsOn(loggingStructured)

lazy val loggingLog4Cats = project
  .in(file("interop/log4cats"))
  .settings(
    defaultSettings,
    publishName := "logging-log4cats",
    libraryDependencies += log4Cats
  )
  .dependsOn(loggingStructured)

moduleName := "tofu-logging"

val libVersion = "0.10.1"

lazy val setMinorVersion = minorVersion := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) => v.toInt
    case _            => 0
  }
}

lazy val setModuleName = moduleName := { s"tofu-${(publishName or name).value}" }

lazy val defaultSettings = Seq(
  scalaVersion := Version.scala213,
  setMinorVersion,
  setModuleName,
  defaultScalacOptions,
  scalacWarningConfig,
  libraryDependencies ++= Seq(
    compilerPlugin(kindProjector),
    compilerPlugin(betterMonadicFor),
    scalatest,
    collectionCompat,
    scalaOrganization.value % "scala-reflect" % scalaVersion.value % Provided
  )
) ++ macros ++ simulacrumOptions ++ publishSettings


lazy val defaultScalacOptions = scalacOptions := {
  val tpolecatOptions = scalacOptions.value

  val dropLints = Set(
    "-Ywarn-dead-code",
    "-Wdead-code" // ignore dead code paths where `Nothing` is involved
  )

  val opts = tpolecatOptions.filterNot(dropLints)

  // drop `-Xfatal-warnings` on dev and 2.12 CI
  if (!sys.env.get("CI").contains("true") || (minorVersion.value == 12))
    opts.filterNot(Set("-Xfatal-warnings"))
  else
    opts
}

lazy val scalacWarningConfig = scalacOptions += {
  // // ignore unused imports that cannot be removed due to cross-compilation
  // val suppressUnusedImports = Seq(
  //   "scala/tofu/config/typesafe.scala"
  // ).map { src =>
  //   s"src=${scala.util.matching.Regex.quote(src)}&cat=unused-imports:s"
  // }.mkString(",")

  // print warning category for fine-grained suppressing, e.g. @nowarn("cat=unused-params")
  val verboseWarnings = "any:wv"

  s"-Wconf:$verboseWarnings"
}

lazy val macros = Seq(
  scalacOptions ++= { if (minorVersion.value == 13) Seq("-Ymacro-annotations") else Seq() },
  libraryDependencies ++= { if (minorVersion.value == 12) Seq(compilerPlugin(macroParadise)) else Seq() }
)

lazy val simulacrumOptions = Seq(
  libraryDependencies += simulacrum % Provided,
  pomPostProcess := { node =>
    import scala.xml.transform.{RewriteRule, RuleTransformer}

    new RuleTransformer(new RewriteRule {
      override def transform(node: xml.Node): Seq[xml.Node] = node match {
        case e: xml.Elem
          if e.label == "dependency" &&
            e.child.exists(child => child.label == "groupId" && child.text == simulacrum.organization) &&
            e.child.exists(child => child.label == "artifactId" && child.text.startsWith(s"${simulacrum.name}_")) =>
          Nil
        case _ => Seq(node)
      }
    }).transform(node).head
  }
)
lazy val publishSettings = Seq(
  organization := "tf.tofu",
  publishVersion := libVersion,
  publishMavenStyle := true,
  description := "Opinionated set of tools for functional programming in Scala",
  crossScalaVersions := Seq(Version.scala212, Version.scala213),
  publishTo := {
    if (isSnapshot.value) {
      Some(Opts.resolver.sonatypeSnapshots)
    } else sonatypePublishToBundle.value
  },
  credentials ++= ((Path.userHome / ".sbt" / "tofu.credentials") :: Nil)
    .filter(_.exists())
    .map(Credentials.apply),
  version := {
    val branch = git.gitCurrentBranch.value
    if (branch == "master") publishVersion.value
    else s"${publishVersion.value}-$branch-SNAPSHOT"
  },
  Compile / doc / sources := Seq.empty,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/TinkoffCreditSystems/tofu"),
      "git@github.com:TinkoffCreditSystems/tofu.git"
    )
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/tofu-tf/tofu")),
  developers := List(
    Developer("catostrophe", "λoλcat", "catostrophe@pm.me", url("https://github.com/catostrophe")),
    Developer("danslapman", "Daniil Smirnov", "danslapman@gmail.com", url("https://github.com/danslapman")),
    Developer("odomontois", "Oleg Nizhnik", "odomontois@gmail.com", url("https://github.com/odomontois")),
    Developer("oskin1", "Ilya Oskin", "ilya.arcadich@gmail.com", url("https://github.com/oskin1")),
  )
)
