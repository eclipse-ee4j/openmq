/*
 * Copyright (c) 2020 Contributors to Eclipse Foundation. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

pipeline {
  agent any

  tools {
    maven 'apache-maven-latest'
    jdk   'oracle-jdk8-latest'
  }

  stages {
    stage('build') {
      steps {
        sh 'mvn -V -B -P staging -f mq              clean install'
        sh 'mvn    -B -P staging -f mq/distribution source:jar install'
        junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
      }
    }
    stage('docs') {
      steps {
        sh 'mvn    -B            -f docs/mq         clean install'
      }
    }
  }
}

