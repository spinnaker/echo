/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts

import spock.lang.Specification

class ArtifactMatcherSpec extends Specification {

  def matchPayload = [
    "one": "one",
    "two": "two",
    "three": "three"
  ]

  def noMatchPayload = [
    "four": "four",
    "five": "five"
  ]

  def contstraints = [
    "one": "one"
  ]

  def shortConstraint = [
    "one": "o"
  ]

  def constraintsOR = [
    "one": ["uno", "one"]
  ]

  def payloadWithList = [
    "one": ["one"]
  ]

  def stringifiedListConstraints = [
    "one": "['uno', 'one']"
  ]

  def jsonPathConstraintsToString = [
    "\$.one.test1.title": "st"
  ]

  def jsonPathConstraintsToStringNOT = [
    "\$.one.test2.title": "no match"
  ]

  def jsonPathConstraintsToBool = [
    "\$.one.test1.isValid": "true"
  ]

  def jsonPathConstraintsToList = [
    "\$.one.test1.modified": ".yml"
  ]

  def jsonPathConstraintsToListNOT = [
    "\$.one.test1.modified": ".xml"
  ]

  def jsonPathConstraintsToObject = [
    "\$.one.test1.author": "edgar"
  ]

  def jsonPathConstraintsToNumber = [
    "\$.two.test3.count": "3"
  ]

  def jsonPathConstraintsToListOfObjects = [
    "\$.two.test2.changes": "bar"
  ]

  def jsonPathConstraintsToUnknownField = [
    "\$.two.test4.changes": "bar"
  ]

  def jsonPathConstraintsBadJsonPath = [
    "\$.one.test2.changes[a].value": "bar"
  ]

  def complexPayload = [
    "one": [
      "test1": [
        "title": "test",
        "modified": ["folder1/file1.txt", "folder1/file2.txt", "folder2/file1.yml"],
        "isValid": true,
        "author": [
          "name": "edgar",
          "username": "edgarulg"
        ]
      ],
      "test2": [
        "title": "another test",
        "created": ["folder3/new.txt"],
        "isValid": false,
        "author": [
          "name": "jorge",
          "username": "jorge123"
        ],
        "changes": [
          [
            "id": 1,
            "value": "foo"
          ],
          [
            "id": 2,
            "value": "bar"
          ]
        ]
      ]
    ],
    "two": [
      "test3": [
        "count": 3,
        "ref": null,
        "isActive": false
      ]
    ]
  ]

  def "matches when constraint is partial word"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(shortConstraint, matchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(shortConstraint, matchPayload)

    then:
    resultOld && resultNew
  }

  def "matches exact string"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(contstraints, matchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(contstraints, matchPayload)


    then:
    resultOld && resultNew
  }

  def "no match when constraint word not present"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(contstraints, noMatchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(contstraints, noMatchPayload)


    then:
    !resultOld && !resultNew
  }

  def "matches when payload value is in a list of constraint strings"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(constraintsOR, matchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, matchPayload)


    then:
    resultOld && resultNew
  }

  def "no match when val not present in list of constraint strings"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(constraintsOR, noMatchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, noMatchPayload)


    then:
    !resultOld && !resultNew
  }

  def "matches when val is in stringified list of constraints"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, matchPayload)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(stringifiedListConstraints, matchPayload)


    then:
    resultOld && resultNew
  }

  def "matches when payload contains list and constraint is a stringified list"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(stringifiedListConstraints, payloadWithList)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(stringifiedListConstraints, payloadWithList)


    then:
    resultOld && resultNew
  }

  def "matches when payload is a list list and constraints are a list"() {
    when:
    boolean resultOld = ArtifactMatcher.isConstraintInPayload(constraintsOR, payloadWithList)
    boolean resultNew = ArtifactMatcher.isJsonPathConstraintInPayload(constraintsOR, payloadWithList)


    then:
    resultOld && resultNew
  }

  def "matches when val is a string using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToString, complexPayload)

    then:
    result
  }

  def "no match when val is a string using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToStringNOT, complexPayload)

    then:
    !result
  }

  def "matches when val is a boolean using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToBool, complexPayload)

    then:
    result
  }

  def "matches when val is a number using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToNumber, complexPayload)

    then:
    result
  }

  def "matches when val is a List<String> using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToList, complexPayload)

    then:
    result
  }

  def "no matches when val is a List<String> using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToListNOT, complexPayload)

    then:
    !result
  }

  def "no match when val is an Object using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToObject, complexPayload)

    then:
    !result
  }

  def "no match when val is a List<Map> using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToListOfObjects, complexPayload)

    then:
    !result
  }

  def "no match when field not found using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsToUnknownField, complexPayload)

    then:
    !result
  }

  def "no match when bad expression using JSONPath constraint with multi-level json"() {
    when:
    boolean result = ArtifactMatcher.isJsonPathConstraintInPayload(jsonPathConstraintsBadJsonPath, complexPayload)

    then:
    !result
  }


}
