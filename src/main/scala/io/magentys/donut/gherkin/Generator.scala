package io.magentys.donut.gherkin

import java.io.File
import io.magentys.donut.gherkin.model._
import io.magentys.donut.gherkin.processors.JSONProcessor
import io.magentys.donut.log.Log
import io.magentys.donut.performance.PerformanceSupport
import io.magentys.donut.template.TemplateEngine
import io.magentys.donut.transformers.cucumber.CucumberTransformer
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.util.Try

object Generator extends Log with PerformanceSupport {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd-HHmm")

  //this wrapper is currently used to help the java maven plugin
  def apply(sourcePath: String,
                    outputPath: String = "donut",
                    filePrefix: String = "",
                    dateTime: String,
                    template: String = "default",
                    countSkippedAsFailure: Boolean = false,
                    countPendingAsFailure: Boolean = false,
                    countUndefinedAsFailure: Boolean = false,
                    countMissingAsFailure: Boolean = false,
                    projectName: String = "",
                    projectVersion: String = ""): ReportConsole = {

    createReport(sourcePath, outputPath, filePrefix,dateTime, template, countSkippedAsFailure, countPendingAsFailure,
      countUndefinedAsFailure, countMissingAsFailure, projectName, projectVersion) match {
      case Some(report) => ReportConsole(report)
      case None => throw new DonutException("An error occurred while generating donut report.")
    }
  }

 private[gherkin] def createReport(sourcePath: String,
                    outputPath: String = "donut",
                    filePrefix: String = "",
                    datetime:String = formatter.print(DateTime.now),
                    template: String = "default",
                    countSkippedAsFailure: Boolean = false,
                    countPendingAsFailure: Boolean = false,
                    countUndefinedAsFailure: Boolean = false,
                    countMissingAsFailure: Boolean = false,
                    projectName: String = "",
                    projectVersion: String = ""): Option[Report] = {

    //Prepare objects
    val statusConf = StatusConfiguration(countSkippedAsFailure,countPendingAsFailure,countUndefinedAsFailure,countMissingAsFailure)
    val projectMetadata = ProjectMetadata(projectName, projectVersion)
    val sourceDir = new File(sourcePath)
    val reportStartedTimestamp = Try(formatter.parseDateTime(datetime)).getOrElse(DateTime.now)

    if (sourceDir.exists) {
      //Step 1: load json files from dir
      val donutFeatures = timed("step1", "Loaded JSON files") {
        val jsonValues = JSONProcessor.loadFrom(sourceDir)
        CucumberTransformer.transform(jsonValues, statusConf)
      }

      if (donutFeatures.nonEmpty) {
        //Step 2: Main Engine - do calculations and produce the report object to bind
        val report: Report = timed("step3", "Produced report") {
          Report(donutFeatures, reportStartedTimestamp, projectMetadata)
        }
        //Step 3: Bind and render the final result
        timed("step4", "Rendered report to html") {
          TemplateEngine(report, s"/templates/$template/index.html").renderToHTML(outputPath, filePrefix)
        }

        Some(report)

      } else {
        log.error(s"No feature reports found at: $sourceDir")
        None
      }
    } else {
      log.error(s"The source directory: $sourceDir does not exist")
      None
    }
  }

}

case class DonutException(mgs:String) extends Exception
