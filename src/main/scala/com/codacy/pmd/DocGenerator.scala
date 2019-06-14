package com.codacy.pmd

import java.util

import com.codacy.plugins.api.Implicits._
import com.codacy.helpers.ResourceHelper
import com.codacy.plugins.api.results.{Parameter, Pattern, Result, Tool}
import play.api.libs.json.{JsObject, JsString, Json}

import scala.util.{Failure, Properties, Success, Try}
import scala.xml.{Elem, Node, Utility, XML}

object DocGenerator {

  private case class Ruleset(name: String, fullName: String, patterns: List[(Pattern.Description, Pattern.Specification, PatternExtendedDescription)])

  private case class PatternExtendedDescription(patternId: Pattern.Id, extendedDescription: String)

  private val rulesetsRoot = "rulesets"

  def main(args: Array[String]): Unit = {
    val version: String = args.headOption.orElse {
      ResourceHelper.getResourceContent("docs/patterns.json").toOption
        .flatMap { lines => Json.parse(lines.mkString("\n")).as[JsObject].\("version").asOpt[String] }
    }.getOrElse {
      throw new Exception("No version provided")
    }

    Try {
      val patterns = for {
        language <- Languages.languages
        langAlias <- Languages.aliasByLang(language)
        languageRulesetsPath = s"$rulesetsRoot/$langAlias"
        propertiesFilePath = s"$languageRulesetsPath/rulesets.properties"
      } yield {
        val markedRulesets = ResourceHelper.getResourceStream(propertiesFilePath).map { propStream =>
          val prop = new util.Properties()
          prop.load(propStream)
          prop.getProperty("rulesets.filenames").split(",").map(_.trim).filter(_.nonEmpty).to[Set]
        }.getOrElse(Set.empty[String])

        ResourceHelper.listResourceDirectory(languageRulesetsPath)
          .map(_.filter(_.endsWith(".xml")))
          .map {
            rulesetPaths =>
              val patternsPerRuleset = for {
                rulesetFilePath <- rulesetPaths
                rulesetPath = s"$languageRulesetsPath/$rulesetFilePath"
                rulesetName <- rulesetPath.split("/").lastOption.map(_.stripSuffix(".xml"))
              } yield {
                if (!markedRulesets.contains(rulesetPath)) {
                  Console.println(s"${Console.YELLOW} [$language] Ruleset $rulesetName is missing from rulesets.properties")
                }

                for {
                  rulesetContents <- ResourceHelper.getResourceContent(rulesetPath)
                  xml <- Try(XML.loadString(rulesetContents.mkString(Properties.lineSeparator)))
                  rulesetLongName = xml \@ "name"
                } yield {
                  val rulesetPatterns = parsePatterns(language, langAlias, rulesetName, xml)
                  Ruleset(rulesetName, rulesetLongName, rulesetPatterns)
                }
              }

              patternsPerRuleset.flatMap(_.toOption)
          }
      }
      patterns.flatMap(_.toOption).flatten
    } match {
      case Success(rulesets) =>
        val (patternDescriptions, patternSpecifications, extendedDescriptions) = rulesets.flatMap(_.patterns).unzip3
        val spec = Tool.Specification(Tool.Name("pmd-legacy"), Some(Tool.Version(version)), patternSpecifications)
        val jsonSpecifications = Json.prettyPrint(Json.toJson(spec))
        val jsonDescriptions = Json.prettyPrint(Json.toJson(patternDescriptions))

        val specialMappings = Map("clone" -> "cloneImplementation")

        val rulesetNamesStr = rulesets.map {
          case r if specialMappings.contains(r.name) =>
            s"""  val ${specialMappings.getOrElse(r.name, "")} = Value("${r.name}")"""
          case r =>
            val parts = r.name.split("-")
            val prefix = parts.headOption.getOrElse("")
            val remaining = parts.tail.map(_.capitalize).mkString
            s"""  val $prefix$remaining = Value("${r.name}")"""
        }.mkString(Properties.lineSeparator)

        val rulesetMap = rulesets.map {
          case r if specialMappings.contains(r.name) =>
            s"""      "${r.fullName}" -> ${specialMappings.getOrElse(r.name, "")}"""
          case r =>
            val parts = r.name.split("-")
            val prefix = parts.headOption.getOrElse("")
            val remaining = parts.tail.map(_.capitalize).mkString
            s"""      "${r.fullName}" -> $prefix$remaining"""
        }.mkString(s",${Properties.lineSeparator}")

        val rulesetsCodeStrFull =
         s"""/*
            | * AUTOGENERATED: DO NOT CHANGE HERE
            | * Changes should be done on DocGenerator.scala
            | */
            |package com.codacy.pmd
            |
            |import com.codacy.docker.api.{Pattern, Result}
            |
            |object RuleSets extends Enumeration {
            |  type RuleSets = Value
            |
            |$rulesetNamesStr
            |
            |  def getRuleSet(name: String): Option[RuleSets.Value] = RuleNameToSet.get(name)
            |
            |  def getLevelAndCategory(simpleName: String): Option[(Result.Level.Value, Pattern.Category.Value)] = {
            |    RuleNameToSet.get(simpleName)
            |      .orElse {
            |        RuleSets.values.find { v =>
            |          v.toString.equalsIgnoreCase(simpleName)
            |        }
            |      }.flatMap(RuleSetToLevelAndCategory.get)
            |  }
            |
            |  private lazy val RuleNameToSet = {
            |    Map(
            |$rulesetMap
            |    )
            |  }
            |
            |  private lazy val RuleSetToLevelAndCategory = {
            |    Map(
            |      android -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      braces -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      cloneImplementation -> (Result.Level.Warn, Pattern.Category.Compatibility),
            |      codesize -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      comments -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      coupling -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      finalizers -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      imports -> (Result.Level.Info, Pattern.Category.UnusedCode),
            |      j2ee -> (Result.Level.Warn, Pattern.Category.CodeStyle),
            |      junit -> (Result.Level.Warn, Pattern.Category.CodeStyle),
            |      javabeans -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      loggingJakartaCommons -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      loggingJava -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      strictexception -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      strings -> (Result.Level.Warn, Pattern.Category.Performance),
            |      unnecessary -> (Result.Level.Warn, Pattern.Category.Performance),
            |      empty -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      design -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      controversial -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      optimizations -> (Result.Level.Warn, Pattern.Category.Performance),
            |      sunsecure -> (Result.Level.Err, Pattern.Category.ErrorProne),
            |      migrating -> (Result.Level.Info, Pattern.Category.Compatibility),
            |      naming -> (Result.Level.Info, Pattern.Category.CodeStyle),
            |      basic -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      unusedcode -> (Result.Level.Warn, Pattern.Category.UnusedCode),
            |      strictsyntax -> (Result.Level.Warn, Pattern.Category.CodeStyle),
            |      dates -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      TomKytesDespair -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      basicJsf -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      xpath -> (Result.Level.Warn, Pattern.Category.ErrorProne),
            |      performance -> (Result.Level.Warn, Pattern.Category.Performance),
            |      security -> (Result.Level.Err, Pattern.Category.Security),
            |      apexunit -> (Result.Level.Warn, Pattern.Category.CodeStyle),
            |      complexity -> (Result.Level.Warn, Pattern.Category.ErrorProne)
            |    )
            |  }
            |
            |}
          """.stripMargin

        val repoRoot = new java.io.File(".")
        val docsRoot = new java.io.File(repoRoot, "src/main/resources/docs")
        val patternsFile = new java.io.File(docsRoot, "patterns.json")
        val descriptionsRoot = new java.io.File(docsRoot, "description")
        val descriptionsFile = new java.io.File(descriptionsRoot, "description.json")
        val rulesetsCodeFile = new java.io.File(repoRoot, "src/main/scala/com.codacy/pmd/RuleSets.scala")

        ResourceHelper.writeFile(patternsFile.toPath, jsonSpecifications)
        ResourceHelper.writeFile(descriptionsFile.toPath, jsonDescriptions)
        extendedDescriptions.collect { case extendedDescription if extendedDescription.extendedDescription.trim.nonEmpty =>
          val descriptionsFile = new java.io.File(descriptionsRoot, s"${extendedDescription.patternId}.md")
          ResourceHelper.writeFile(descriptionsFile.toPath, extendedDescription.extendedDescription)
        }

        ResourceHelper.writeFile(rulesetsCodeFile.toPath, rulesetsCodeStrFull)

      case Failure(e) =>
        e.printStackTrace()
    }
  }

  private def parsePatterns(language: String, langAlias: String, rulesetName: String, xml: Elem): List[(Pattern.Description, Pattern.Specification, PatternExtendedDescription)] = {
    (for {
      rule <- xml \\ "rule"
      deprecated = rule \@ "deprecated" if !Try(deprecated.toBoolean).getOrElse(false)
      name = rule \@ "name" if name.trim.nonEmpty
      message = rule \@ "message"
      since = rule \@ "since"
      longDescription = (rule \ "description").text
      example = (rule \ "example").text
    } yield {
      val (parameterDescriptions, parameterSpecifications) = parseParameters(rule).to[Set].unzip
      val rulesetNameClean = rulesetName.stripSuffix(".xml")
      val patternId = Pattern.Id(s"${langAlias}_${rulesetNameClean}_$name")

      val (level, category) = RuleSets.getLevelAndCategory(rulesetNameClean)
        .getOrElse((Result.Level.Warn, Pattern.Category.CodeStyle))

      val timeToFix = Patterns.timeToFix.get(patternId.value).map(Pattern.TimeToFix.apply)

      (
        Pattern.Description(patternId, Pattern.Title(message), None, timeToFix, Some(parameterDescriptions).filter(_.nonEmpty)),
        Pattern.Specification(patternId, level, category, Some(parameterSpecifications).filter(_.nonEmpty), Some(com.codacy.plugins.api.languages.Languages.fromName(language).toSet)),
        PatternExtendedDescription(patternId,
         s"""Since: PMD $since
            |
            |${Utility.escape(longDescription.trim)}
            |
            |Example(s):
            |```
            |${example.trim}
            |```${Properties.lineSeparator}""".stripMargin)
      )
    }).to[List]
  }

  private def parseParameters(rule: Node): List[(Parameter.Description, Parameter.Specification)] = {
    (for {
      property <- rule \\ "property"
      // HACK: Codacy converts the version parameter from 2.0 to 2 leading PMD to fail, excluding it for now
      name = property \@ "name" if name != "version" && name.trim.nonEmpty
      description = Option(property \@ "description").filter(_.trim.nonEmpty).getOrElse(name)
      defaultValueField = Option(property \@ "value").filter(_.trim.nonEmpty)
      defaultValueBody = Option(property \ "value")
        .flatMap(_.theSeq.collectFirst { case v if v.text.trim.nonEmpty => v.text })
    } yield {
      val defaultValue = defaultValueField.orElse(defaultValueBody)
        .map(JsString)
        .getOrElse(JsString(""))

      (
        Parameter.Description(Parameter.Name(name), Parameter.DescriptionText(description)),
        Parameter.Specification(Parameter.Name(name), defaultValue)
      )
    }).to[List]
  }

}