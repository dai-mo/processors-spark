import Common._
import Dependencies._
import com.typesafe.sbt.GitPlugin.autoImport._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease._

val projectName = "org.dcs.spark"


lazy val spark =
  BaseProject("spark", projectName).
    enablePlugins(BuildInfoPlugin).
    settings(libraryDependencies ++= sparkDependencies).
    settings(test in assembly := {}).
    settings(publishArtifact in(Compile, assembly) := true).
    // FIXME: This creates a jar in the target dir. as,
    //        'name'-assembly-'version'.jar
    //        but publishes it as,
    //        'name'-'version'-assembly.jar
    settings(artifact in(Compile, assembly) ~= { art =>
    art.copy(`classifier` = Some("assembly"))
  }).
    settings(addArtifact(artifact in(Compile, assembly), assembly).settings: _*)



// ------- Versioning , Release Section --------

// Git
showCurrentGitBranch

git.useGitDescribe := true

git.baseVersion := "0.0.0"

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

git.gitTagToVersionNumber := {
  case VersionRegex(v, "SNAPSHOT") => Some(s"$v-SNAPSHOT")
  case VersionRegex(v, "") => Some(v)
  case VersionRegex(v, s) => Some(s"$v-$s-SNAPSHOT")
  case v => None
}

lazy val bumpVersion = settingKey[String]("Version to bump - should be one of \"None\", \"Major\", \"Patch\"")
bumpVersion := "None"

releaseVersion := {
  ver =>
    bumpVersion.value.toLowerCase match {
      case "none" => Version(ver).
        map(_.withoutQualifier.string).
        getOrElse(versionFormatError)
      case "major" => Version(ver).
        map(_.withoutQualifier).
        map(_.bump(sbtrelease.Version.Bump.Major).string).
        getOrElse(versionFormatError)
      case "patch" => Version(ver).
        map(_.withoutQualifier).
        map(_.bump(sbtrelease.Version.Bump.Bugfix).string).
        getOrElse(versionFormatError)
      case _ => sys.error("Unknown bump version - should be one of \"None\", \"Major\", \"Patch\"")
    }
}

releaseVersionBump := sbtrelease.Version.Bump.Minor
