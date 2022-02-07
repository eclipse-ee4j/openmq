#
# Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0, which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# This Source Code may also be made available under the following Secondary
# Licenses when the conditions for such availability set forth in the
# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
# version 2 with the GNU Classpath Exception, which is available at
# https://www.gnu.org/software/classpath/license.html.
#
# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
#

FROM jakartaee/cts-base:0.3

ENV OPENMQ_TCK_HOME=/openmq-tck

ARG TCK_VERSION=3.1.0
ARG DOWNLOAD_TCK_NAME=jakarta-messaging-tck-${TCK_VERSION}.zip
ARG DOWNLOAD_TCK_INFO_NAME=jakarta-messaging-tck-${TCK_VERSION}-tckinfo.txt
ARG LOCAL_TCK_NAME=jakarta-messaging-tck.zip
ARG LOCAL_TCK_INFO_NAME=jakarta-messaging-tck-tckinfo.txt
ARG TCK_DOWNLOAD_BASE_URL=https://download.eclipse.org/ee4j/jakartaee-tck/jakartaee10/staged/eftl

RUN mkdir --parents ${OPENMQ_TCK_HOME}                                                              && \
    cd ${OPENMQ_TCK_HOME}                                                                           && \
    wget -q ${TCK_DOWNLOAD_BASE_URL}/${DOWNLOAD_TCK_NAME}      -O ${LOCAL_TCK_NAME}                 && \
    sha256sum ${LOCAL_TCK_NAME}                                                                     && \
    wget -q ${TCK_DOWNLOAD_BASE_URL}/${DOWNLOAD_TCK_INFO_NAME} -O ${LOCAL_TCK_INFO_NAME}            && \
    cat ${LOCAL_TCK_INFO_NAME}                                                                      && \
    unzip -qo ${LOCAL_TCK_NAME}                                                                     && \
    rm -f ${LOCAL_TCK_NAME}                                                                         && \
    mkdir --parents jmstckreport/jmstck                                                             && \
    cd messaging-tck/bin                                                                            && \
    sed -i "s#^jms.home=.*#jms.home=${OPENMQ_TCK_HOME}/mq#g" ts.jte                                 && \
    sed -i 's#^jms.classes=.*#jms.classes=${ri.jars}#g' ts.jte                                      && \
    sed -i "s#^report.dir=.*#report.dir=${OPENMQ_TCK_HOME}/jmstckreport/jmstck#g" ts.jte            && \
    sed -i "s#^work.dir=.*#work.dir=${OPENMQ_TCK_HOME}/jmstckwork/jmstck#g" ts.jte

