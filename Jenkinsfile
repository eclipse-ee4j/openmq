/*
 * Copyright (c) 2020-2024 Contributors to Eclipse Foundation. All rights reserved.
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
    stage('Build OpenMQ Distribution') {
      parallel {
        stage('build') {
          agent any
          tools {
            jdk   'temurin-jdk21-latest'
          }
          steps {
            sh './mvnw -V -B -P staging -P dash-licenses -f mq/main         clean install -Dbuild.letter=j -Dbuild.number=${BRANCH_NAME}/${GIT_COMMIT}/${BUILD_NUMBER}'
            sh './mvnw    -B -P staging                  -f mq/distribution source:jar install'
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
            dir('mq/dist/bundles') {
              stash name: 'built-mq', includes: 'mq.zip'
              archiveArtifacts artifacts: 'mq.zip'
            }
            dir('mq/main') {
              archiveArtifacts artifacts: 'dash-summary.txt'
            }
          }
        }
      }
      post {
        always {
          node(null) {
            recordIssues tools: [ mavenConsole(), javaDoc() ], enabledForFailure: true
          }
        }
      }
    }
    stage('sanity') {
      stages {
        stage('sanity - start and shutdown broker') {
          matrix {
            axes {
              axis {
                name 'SANITY_JDK_JENKINS_TOOL'
                values 'temurin-jdk21-latest'
              }
            }
            stages {
              stage('sanity on specific JDK') {
                agent any
                options {
                    skipDefaultCheckout()
                }
                tools {
                  jdk "${SANITY_JDK_JENKINS_TOOL}"
                }
                environment {
                  BROKER_PORT = """${sh(
                    returnStdout: true,
                    script: 'echo $(((EXECUTOR_NUMBER + 1) * 20 + 7676))'
                  ).trim()}"""
                }
                steps {
                  echo "Sanity test using ${SANITY_JDK_JENKINS_TOOL}"
                  dir('distribution') {
                    deleteDir()
                  }
                  dir('distribution') {
                    unstash 'built-mq'
                    sh 'unzip -q mq.zip'
                    dir('mq') {
                      writeFile file: 'admin.pass', text: 'imq.imqcmd.password=admin'
                      sh 'nohup bin/imqbrokerd -port ${BROKER_PORT} > broker.log 2>&1 &'
                      retry(count: 3) {
                        sleep time: 10, unit: 'SECONDS'
                        script {
                          def brokerLogText = readFile(file: 'broker.log')
                          brokerLogText.matches('(?s)^.*Broker .*:.*ready.*$') || error('Looks like broker did not start in time')
                        }
                      }
                      sh 'java -cp lib/jms.jar:lib/imq.jar:examples/helloworld/helloworldmessage -DimqAddressList=mq://localhost:${BROKER_PORT}/jms HelloWorldMessage > hello.log 2>&1'
                      script {
                        sh 'cat hello.log'
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
                        sh 'bin/imqcmd -b :${BROKER_PORT} -u admin -f -passfile admin.pass shutdown bkr'
                        sh 'cat broker.log'
                      }
                    }
                  }
                }
              }
            }
          }
        }
        stage('sanity - run cluster') {
          agent any
          options {
            skipDefaultCheckout()
          }
          tools {
            jdk 'temurin-jdk21-latest'
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
                script {
                  for (brokerId in [ 0, 1, 2 ]) {
                    sh "nohup bin/imqbrokerd -name broker${brokerId} -port ${7670 + brokerId} -cluster :7670,:7671,:7672 > broker${brokerId}.log 2>&1 &"
                    retry(count: 3) {
                      sleep time: 10, unit: 'SECONDS'
                      script {
                        def brokerLogText = readFile(file: "broker${brokerId}.log")
                        brokerLogText.matches('(?s)^.*Broker .*:.*ready.*$') || error('Looks like broker did not start in time')
                      }
                    }
                  }
                }
                script {
                  sh 'bin/imqcmd -u admin -passfile admin.pass -b :7670 list bkr > clusterlist.log'
                  sh 'cat clusterlist.log'
                  def logFileText = readFile(file: 'clusterlist.log')
                  (logFileText.contains(':7670   OPERATING')
                   && logFileText.contains(':7671   OPERATING')
                   && logFileText.contains(':7672   OPERATING')
                     || error('Cluster list did not produce expected message'))
                }
              }
            }
          }
          post {
            always {
              dir('distribution') {
                dir('mq') {
                  script {
                    for (brokerId in [ 0, 1, 2 ]) {
                      sh "bin/imqcmd -b :${7670 + brokerId} -u admin -f -passfile admin.pass shutdown bkr"
                      sh "cat broker${brokerId}.log"
                    }
                  }
                }
              }
            }
          }
        }
        stage('sanity - services') {
          stages {
            stage('sanity - service: wsjms') {
              agent any
              options {
                  skipDefaultCheckout()
              }
              tools {
                jdk 'temurin-jdk21-latest'
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
                    sh 'nohup bin/imqbrokerd -Dimq.service.activelist=wsjms,admin > broker-wsjms.log 2>&1 &'
                    retry(count: 3) {
                      sleep time: 10, unit: 'SECONDS'
                      script {
                        def brokerLogText = readFile(file: 'broker-wsjms.log')
                        brokerLogText.matches('(?s)^.*Broker .*:.*ready.*$') || error('Looks like broker did not start in time')
                      }
                    }

                    sh './bin/imqcmd -u admin -passfile admin.pass query dst -t q -n sanity.test.queue 2>&1 | tee queue.query.1.log || true'

                    // expected Error while performing this operation on the broker - due to queue not existing yet
                    sh 'grep -q "Could not locate destination sanity.test.queue" queue.query.1.log'

                    sh '''
                          java \
                          -cp lib/jms.jar:lib/imq.jar:lib/tyrus-standalone-client.jar:examples/jms20/syncqueue \
                          -DimqAddressList=mqws://localhost:7670/wsjms \
                          SendObjectMsgsToQueue sanity.test.queue 10 \
                       '''

                    sh './bin/imqcmd -u admin -passfile admin.pass query dst -t q -n sanity.test.queue | tee queue.query.2.log'

                    // expected 10 messages queued
                    sh 'grep -A 1 "Current Number of Messages" queue.query.2.log | grep -q -E "Actual\\s*10"'

                    sh '''
                          java \
                          -cp lib/jms.jar:lib/imq.jar:lib/tyrus-standalone-client.jar:examples/jms20/syncqueue \
                          -DimqAddressList=mqws://localhost:7670/wsjms \
                          SyncQueueConsumer sanity.test.queue 10 \
                       '''

                    sh './bin/imqcmd -u admin -passfile admin.pass query dst -t q -n sanity.test.queue | tee queue.query.3.log'

                    // expected 0 messages queued
                    sh 'grep -A 1 "Current Number of Messages" queue.query.3.log | grep -q -E "Actual\\s*0"'
                  }
                }
              }
              post {
                always {
                  dir('distribution') {
                    dir('mq') {
                      sh 'bin/imqcmd -u admin -f -passfile admin.pass shutdown bkr'
                      sh 'cat broker-wsjms.log'
                    }
                  }
                }
              }
            }
          }
        }
        stage ('sanity - FS JNDI') {
          agent any
          options {
            skipDefaultCheckout()
          }
          tools {
            jdk 'temurin-jdk21-latest'
          }
          steps {
            dir('distribution') {
              deleteDir()
            }
            dir('distribution') {
              unstash 'built-mq'
              sh 'unzip -q mq.zip'
              dir('mq') {
                dir('jndi') {
                  sh '''
                        ../bin/imqobjmgr \
                          -j "java.naming.provider.url=file://$(pwd)" \
                          -j "java.naming.factory.initial=com.sun.jndi.fscontext.RefFSContextFactory" \
                          add \
                            -t xcf \
                            -l cn=sanityConnFact \
                            -o "imqAddressList=mq://localhost:7676/jms"
                     '''
                }
              }
            }
          }
        }
      }
    }
    stage('Code Coverage') {
      agent any
      tools {
        jdk   'temurin-jdk21-latest'
      }
      steps {
        sh './mvnw -V -B -P staging -f mq/main -P jacoco clean verify'
        jacoco execPattern: '**/**.exec',
               classPattern: '**/classes',
               sourcePattern: '**/src/main/java',
               sourceInclusionPattern: '**/*.java'
      }
    }
    stage('Static Analysis') {
      failFast true
      matrix {
        axes {
          axis {
            name 'TOOL_PROFILE'
            values 'ecj'
          }
        }
        stages {
          stage('analysis') {
            agent any
            tools {
              jdk   'temurin-jdk21-latest'
            }
            steps {
              script {
                MAVENOPTS = ''
                if (TOOL_PROFILE == 'ecj') {
                  LOMBOKLOC = sh returnStdout: true,
                     script: '''./mvnw \
                                --quiet \
                                --file mq/main \
                                --non-recursive \
                                dependency:properties \
                                help:evaluate \
                                --activate-profiles staging,ecj \
                                --define forceStdout \
                                --define expression=lombok.repo.location'''
                  MAVENOPTS = "-javaagent:${LOMBOKLOC}=ECJ"
                }
                sh "MAVEN_OPTS=${MAVENOPTS} ./mvnw -V -B -P staging -f mq/main -pl -packager-opensource -P ${TOOL_PROFILE} -DskipTests clean verify -fae"
              }
            }
            post {
              always {
                script {
                  switch (TOOL_PROFILE) {
                    case 'ecj':
                      recordIssues tool: eclipse(), enabledForFailure: true
                      break
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

