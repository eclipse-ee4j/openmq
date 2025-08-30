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
    string(name: 'RELEASE_VERSION',
           trim: true,
           description: '''
             <p>Version to release.</p>
             <p>Default value is from POM snapshot.</p>
                        ''')
    string(name: 'NEXT_VERSION',
           trim: true,
           description: '''
             <p>Next snapshot version to set (e.g. 1.2-SNAPSHOT).</p>
             <p>Default value is from POM snapshot with last component incremented by 1.</p>
                        ''')
    string(name: 'BRANCH',
           trim: true,
           defaultValue: 'master',
           description: '''
             <p>Branch to release.</p>
             <p>Default value is <tt>master</tt>.</p>
                        ''')
  }

  stages {
    stage('Build and deploy') {
      tools {
        jdk   'temurin-jdk21-latest'
      }

      steps {
        checkout scmGit(branches: [
                          [ name: "*/${params.BRANCH}" ]
                        ],
                        userRemoteConfigs: [
                          [ url: 'git@github.com:eclipse-ee4j/openmq.git',
                            credentialsId: 'github-bot-ssh' ]
                        ],
                        extensions: [
                          [ $class: 'CloneOption',
                            shallow: true,
                            depth: 1 ]
                        ])

        withCredentials([
          file(credentialsId: 'secret-subkeys.asc',
               variable: 'KEYRING')
          ]) {
            sh '''
              gpg --batch --import ${KEYRING}
              for fpr in $(gpg --list-keys --with-colons  | awk -F: '/fpr:/ {print $10}' | sort -u);
              do
                echo -e "5\ny\n" |  gpg --batch --command-fd 0 --expert --edit-key $fpr trust;
              done

              if [ -f KEYS ]
              then
                gpg --import KEYS
              fi

              echo 'pinentry-mode loopback' | tee --append $(gpgconf --list-dirs homedir)/gpg.conf
               '''
        }

        sh '''
          # Setup openmq bot account information

          git config --global user.email "openmq-bot@eclipse.org"
          git config --global user.name "Eclipse OpenMQ"

          git config --global user.signingkey openmq-dev@eclipse.org
          git config list
           '''

        withCredentials([
          string(credentialsId: 'gpg-passphrase',
                 variable: 'KEYRING_PASSPHRASE')
        ]) {
          sh '''
            # Pre-use GPG key before git committing git
            PRESIGNED_FILE=$(mktemp --suffix=.txt)
            date > ${PRESIGNED_FILE}
            echo "${KEYRING_PASSPHRASE}" | gpg --batch --clearsign --passphrase-fd 0 ${PRESIGNED_FILE}
            gpg --verify < ${PRESIGNED_FILE}.asc
             '''
          sh '''
            PRESIGNED_FILE=$(mktemp --suffix=.txt)
            date > ${PRESIGNED_FILE}
            gpg --batch --clearsign --passphrase-fd 0 ${PRESIGNED_FILE}
            gpg --verify < ${PRESIGNED_FILE}.asc
             '''

          sshagent(credentials: ['github-bot-ssh']) {
            sh '''
              MVN_EXTRA="--batch-mode --no-transfer-progress"

              # Directory with project top level pom.xml
              BUILD_DIR="${WORKSPACE}"
              DISTRIBUTION_DIR="${BUILD_DIR}/mq/distribution"

              # Do not use shared maven repository
              export MAVEN_OPTS="-Dmaven.repo.local=${BUILD_DIR}/.repository $MAVEN_OPTS"

              cd ${BUILD_DIR}

              # Check whether top level pom.xml contains SNAPSHOT version
              if ! grep '<version>' pom.xml | grep 'SNAPSHOT' ; then
                echo '-[ Missing SNAPSHOT version in POM! ]-------------------------------------------'
                exit 1
              fi

              echo "Compute release versions"
              SNAPSHOT_VERSION=$(./mvnw ${MVN_EXTRA} help:evaluate -Dexpression=project.version -q -DforceStdout)

              if [ -z "${RELEASE_VERSION}" ]
              then
                if [ -z ${SNAPSHOT_VERSION} ]
                then
                  echo '-[ Missing required snapshot version number! ]----------------------------------'
                fi
                RELEASE_VERSION=$(echo ${SNAPSHOT_VERSION} | sed -e 's/-SNAPSHOT//')
              fi

              # Bash specific code
              if [ -z "${NEXT_VERSION}" ]; then
                NEXT_VERSION=$(echo ${RELEASE_VERSION} | sed -e 's/\\([0-9][0-9]*\\.[0-9][0-9]*\\).*/\\1/')
                set -f
                NEXT_COMPONENTS=(${RELEASE_VERSION//\\./ })
                LAST_INDEX=$((${#NEXT_COMPONENTS[@]} - 1))
                NEXT_COMPONENTS[${LAST_INDEX}]=$((${NEXT_COMPONENTS[${LAST_INDEX}]} + 1))
                NEXT_VERSION=$(echo ${NEXT_COMPONENTS[@]} | tr ' ' '.')'-SNAPSHOT'
              fi

              RELEASE_TAG="${RELEASE_VERSION}-RELEASE"

              echo "Current version: ${SNAPSHOT_VERSION}"
              echo "Release version: ${RELEASE_VERSION}"
              echo "Next version:    ${NEXT_VERSION}"
              echo "Release tag:     ${RELEASE_TAG}"

              if [ -z "${SNAPSHOT_VERSION}" -o -z "${RELEASE_VERSION}" -o -z "${NEXT_VERSION}" ]; then
                echo '-[ Missing required version numbers! ]------------------------------------------'
                exit 1
              fi

              MVN_DEPLOY_ARGS="deploy -DdeploymentName=OpenMQ-${RELEASE_VERSION}"

              GIT_ORIGIN=$(git remote)

              echo '-[ Prepare branch ]-------------------------------------------------------------'
              if [[ -n $(git branch -r | grep "${GIT_ORIGIN}/${RELEASE_VERSION}$") ]]; then
                echo "Error: ${GIT_ORIGIN}/${RELEASE_VERSION} branch already exists"
                exit 1
              fi

              echo '-[ Release tag cleanup ]--------------------------------------------------------'
              if [[ -n $(git ls-remote --tags ${GIT_ORIGIN} | grep "${RELEASE_TAG}$") ]]; then
                echo "Error: ${RELEASE_TAG} tag already exists"
                exit 1
              fi

              cd ${WORKSPACE}

              # Always delete local branch if exists
              git branch --delete "${RELEASE_VERSION}" || true
              git checkout -b ${RELEASE_VERSION}

              # Always delete local tag if exists
              git tag --delete "${RELEASE_TAG}" || true

              cd ${BUILD_DIR}

              # Project identifiers
              ARTIFACT_ID=$(./mvnw ${MVN_EXTRA} -B help:evaluate -Dexpression=project.artifactId | grep -Ev '(^\\[)')
              GROUP_ID=$(./mvnw ${MVN_EXTRA} -B help:evaluate -Dexpression=project.groupId | grep -Ev '(^\\[)')

              # Change the versions of all pom.xml files to refer to the newly to be released version
              echo '-[ Set release version ]--------------------------------------------------------'
              # Set release version
              ./mvnw -U -C \
                  -DnewVersion="${RELEASE_VERSION}" \
                  -DgenerateBackupPoms=false \
                  clean versions:set ${MVN_EXTRA}

              # Collect all pom.xml files that were modified above when their version was updated
              echo '-[ Commit modified pom.xml files ]----------------------------------------------'
              cd ${WORKSPACE}
              git add -u && \
                git commit --gpg-sign -m "Prepare release ${GROUP_ID}:${ARTIFACT_ID}:${RELEASE_VERSION}"

              # Do the actual build, using the version we are about to release
              echo '-[ Full build ]------------------------------------------------------------------'
              cd ${BUILD_DIR}

              # Deploy all but docs to repository
              echo '-[ Deploy artifacts to repository ]-------------------------'

              # https://github.com/CycloneDX/cyclonedx-maven-plugin/issues/597
              # https://cyclonedx.github.io/cyclonedx-maven-plugin/makeAggregateBom-mojo.html#skipNotDeployed
              MVN_CYCLONEDX_597=-Dcyclonedx.skipNotDeployed=false

              ./mvnw -V -U -C -s /home/jenkins/.m2/settings.xml \
                  -DskipTests -Ddoclint=none \
                  -Poss-release -Psbom -Pcbi-jarsign \
                  -pl -docs,-docs/mq-shared-doc-resources,-docs/mq-admin-guide,-docs/mq-dev-guide-c,-docs/mq-dev-guide-java,-docs/mq-dev-guide-jmx,-docs/mq-release-notes,-docs/mq-tech-over \
                  -Dbuild.letter=r -Dbuild.number=${GIT_COMMIT}/${BUILD_NUMBER} \
                  ${MVN_CYCLONEDX_597} \
                  clean ${MVN_DEPLOY_ARGS} ${MVN_EXTRA}
              echo '-[ Deployed artifacts to staging repository ]-------------------------'

              echo '-[ Tag release ]----------------------------------------------------------------'
              cd ${WORKSPACE}
              git tag --sign "${RELEASE_TAG}" -m "Release OpenMQ ${RELEASE_VERSION}"

              echo '-[ Set next snapshot version ]--------------------------------------------------'
              cd ${BUILD_DIR}
              ./mvnw -U -C \
                  -DnewVersion="${NEXT_VERSION}" \
                  -DgenerateBackupPoms=false \
                  clean versions:set ${MVN_EXTRA}

              echo '-[ Commit modified pom.xml files ]----------------------------------------------'
              cd ${WORKSPACE}
              git add -u && \
                git commit --gpg-sign -m "Prepare next development cycle for ${NEXT_VERSION}"

              git status

              echo '-[ Push branch and tag to GitHub ]----------------------------------------------'
              git push "${GIT_ORIGIN}" "${RELEASE_VERSION}" "${RELEASE_TAG}"
               '''
          }
        }
      }
    }
  }
}
