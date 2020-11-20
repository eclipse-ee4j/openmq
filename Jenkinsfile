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
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Build OpenMQ Distribution and Documentation') {
      parallel {
        stage('build') {
          agent any
          tools {
            maven 'apache-maven-latest'
            jdk   'oracle-jdk8-latest'
          }
          steps {
            sh 'mvn -V -B -P staging -f mq              clean install'
            sh 'mvn    -B -P staging -f mq/distribution source:jar install'
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
            dir('mq/dist/bundles') {
              stash name: 'built-mq', includes: 'mq.zip'
            }
          }
        }
        stage('docs') {
          agent any
          tools {
            maven 'apache-maven-latest'
            jdk   'oracle-jdk8-latest'
          }
          steps {
            sh 'mvn    -B            -f docs/mq         clean install'
          }
        }
        stage('C Client') {
          agent {
            kubernetes {
              yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: openmq-cpp-dev
    image: ee4j/openmq-cpp-dev:0.1-3
    command:
    - cat
    tty: true
"""
            }
          }
          steps {
            container('openmq-cpp-dev') {
              sh 'ant -f mq/main/packager-opensource buildcclient'
            }
          }
        }
      }
    }
    stage('sanity') {
      agent any
      tools {
        jdk   'oracle-jdk8-latest'
      }
      steps {
        dir('distribution') {
          deleteDir()
        }
        dir('distribution') {
          unstash 'built-mq'
          sh 'unzip -q mq.zip'
          dir('mq') {
            writeFile file: 'admin.pass', text: 'imq.imqcmd.password=admin'
            sh 'nohup bin/imqbrokerd > broker.log 2>&1 &'
            retry(count: 3) {
              sleep time: 10, unit: 'SECONDS'
              script {
                def brokerLogText = readFile(file: 'broker.log')
                brokerLogText.matches('(?s)^.*Broker .*:.*ready.*$') || error('Looks like broker did not start in time')
              }
            }
            sh 'java -cp lib/jms.jar:lib/imq.jar:examples/helloworld/helloworldmessage HelloWorldMessage > hello.log 2>&1'
            script {
              def logFileText = readFile(file: 'hello.log')
              (logFileText.contains('Sending Message: Hello World')
               && logFileText.contains('Read Message: Hello World')) || error('HelloWorldMessage did not produce expected message')
            }
          }
        }
      }
      post {
        always {
          dir('distribution') {
            dir('mq') {
              sh 'bin/imqcmd -u admin -f -passfile admin.pass shutdown bkr'
            }
          }
        }
      }
    }
    stage('Static Analysis') {
      matrix {
        axes {
          axis {
            name 'TOOL_PROFILE'
            values 'pmd', 'spotbugs'
          }
        }
        stages {
          stage('analysis') {
            agent any
            tools {
              maven 'apache-maven-latest'
              jdk   'oracle-jdk8-latest'
            }
            steps {
              sh "mvn -V -B -P staging -f mq -P ${TOOL_PROFILE} clean install -fae"
            }
          }
        }
      }
    }
  }
}

