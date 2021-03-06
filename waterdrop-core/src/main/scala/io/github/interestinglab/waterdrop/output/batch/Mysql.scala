/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package io.github.interestinglab.waterdrop.output.batch

import io.github.interestinglab.waterdrop.config.{Config, ConfigFactory}
import io.github.interestinglab.waterdrop.apis.BaseOutput
import org.apache.spark.sql.{Dataset, Row, SaveMode, SparkSession}

import scala.collection.JavaConversions._

class Mysql extends BaseOutput {

  var firstProcess = true

  var config: Config = ConfigFactory.empty()

  /**
   * Set Config.
   * */
  override def setConfig(config: Config): Unit = {
    this.config = config
  }

  /**
   * Get Config.
   * */
  override def getConfig(): Config = {
    this.config
  }

  override def checkConfig(): (Boolean, String) = {

    val requiredOptions = List("url", "table", "user", "password");

    val nonExistsOptions = requiredOptions.map(optionName => (optionName, config.hasPath(optionName))).filter { p =>
      val (optionName, exists) = p
      !exists
    }

    if (nonExistsOptions.length == 0) {

      val saveModeAllowedValues = List("overwrite", "append", "ignore", "error");

      if (!config.hasPath("save_mode") || saveModeAllowedValues.contains(config.getString("save_mode"))) {
        (true, "")
      } else {
        (false, "wrong value of [save_mode], allowed values: " + saveModeAllowedValues.mkString(", "))
      }

    } else {
      (false, "please specify " + nonExistsOptions.map("[" + _._1 + "]").mkString(", ") + " as non-empty string")
    }
  }

  override def prepare(spark: SparkSession): Unit = {
    super.prepare(spark)

    val defaultConfig = ConfigFactory.parseMap(
      Map(
        "save_mode" -> "append" // allowed values: overwrite, append, ignore, error
      )
    )
    config = config.withFallback(defaultConfig)
  }

  override def process(df: Dataset[Row]): Unit = {

    val prop = new java.util.Properties
    prop.setProperty("driver", "com.mysql.jdbc.Driver")
    prop.setProperty("user", config.getString("user"))
    prop.setProperty("password", config.getString("password"))

    val saveMode = config.getString("save_mode")

    if (firstProcess) {
      df.write.mode(saveMode).jdbc(config.getString("url"), config.getString("table"), prop)
      firstProcess = false
    } else if (saveMode == "overwrite") {
      // actually user only want the first time overwrite in streaming(generating multiple dataframe)
      df.write.mode(SaveMode.Append).jdbc(config.getString("url"), config.getString("table"), prop)
    } else {
      df.write.mode(saveMode).jdbc(config.getString("url"), config.getString("table"), prop)
    }
  }
}
