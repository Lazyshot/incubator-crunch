/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.scrunch

import org.apache.crunch.io.{From => from}
import org.apache.crunch.test.CrunchTestSupport

import org.scalatest.junit.JUnitSuite
import _root_.org.junit.Test

class UnionTest extends CrunchTestSupport with JUnitSuite {
  lazy val pipeline = Pipeline.mapReduce[UnionTest](tempDir.getDefaultConfiguration)

  def wordCount(col: PCollection[String]) = {
    col.flatMap(_.toLowerCase.split("\\W+")).count
  }

  @Test def testUnionCollection {
    val shakespeare = tempDir.copyResourceFileName("shakes.txt")
    val maugham = tempDir.copyResourceFileName("maugham.txt")
    val union = pipeline.read(from.textFile(shakespeare)).union(
        pipeline.read(from.textFile(maugham)))
    val wc = wordCount(union).materialize
    assert(wc.exists(_ == ("you", 3691)))
    pipeline.done
  }

  @Test def testUnionTable {
    val shakespeare = tempDir.copyResourceFileName("shakes.txt")
    val maugham = tempDir.copyResourceFileName("maugham.txt")
    val wcs = wordCount(pipeline.read(from.textFile(shakespeare)))
    val wcm = wordCount(pipeline.read(from.textFile(maugham)))
    val wc = wcs.union(wcm).groupByKey.combine(v => v.sum).materialize
    assert(wc.exists(_ == ("you", 3691)))
    pipeline.done
  }
}
