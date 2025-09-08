/*
 * Copyright (c) 2025 Contributors to Eclipse Foundation. All rights reserved.
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

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipDefaultCheckout()
  }

  stages {
    stage('Check deployments/files') {
      steps {
        withCredentials([
          string(credentialsId: 'central-sonatype-bearer-token',
                 variable: 'CENTRAL_SONATYPE_BEARER_TOKEN')
          ]) {
            sh '''
              curl --silent \
                   --header 'Accept: application/json' \
                   --header 'Content-Type: application/json' \
                   --header "Authorization: Bearer ${CENTRAL_SONATYPE_BEARER_TOKEN}" \
                   -d '{
                     "page": 0,
                     "size": 500,
                     "sortField": "createTimestamp",
                     "sortDirection": "desc",
                     "deploymentIds": [],
                     "pathStarting": "org/glassfish/mq"
                       }' \
                   "${CENTRAL_SONATYPE_API_URL}/publisher/deployments/files" | tee deployments.json
               '''
        }
        archiveArtifacts artifacts: 'deployments.json',
                         onlyIfSuccessful: true
      }
    }
  }
}
