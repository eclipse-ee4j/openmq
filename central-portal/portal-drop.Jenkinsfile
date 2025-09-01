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

  parameters {
    string(name: 'DEPLOYMENT_ID',
           trim: true,
           description: '''
             <p>At this moment it is in the form of <tt>UUID</tt>.</p>
             <p>This was disclosed on build:</p>
             <pre>
               ...
               [INFO] Waiting until Deployment 775a7b9e-6d57-4b63-bb0f-8f4765668f3c is validated
               ...
             </pre>
                        ''')
  }

  stages {
    stage('Drop deployment') {
      steps {
        withCredentials([
          string(credentialsId: 'central-sonatype-bearer-token',
                 variable: 'CENTRAL_SONATYPE_BEARER_TOKEN')
          ]) {
            sh '''
              if [ -z "${DEPLOYMENT_ID}" ]
              then
                echo 'Missing deployment id'
                exit 1
              fi
               '''
            sh '''
              curl --silent \
                   --request DELETE \
                   --header "Authorization: Bearer ${CENTRAL_SONATYPE_BEARER_TOKEN}" \
                   "${CENTRAL_SONATYPE_API_URL}/publisher/deployment/${DEPLOYMENT_ID}"
               '''
        }
      }
    }
  }
}
