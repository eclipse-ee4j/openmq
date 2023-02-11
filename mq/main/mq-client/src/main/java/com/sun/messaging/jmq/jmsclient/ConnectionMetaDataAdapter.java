/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsclient;

import jakarta.jms.ConnectionMetaData;

import com.sun.messaging.jmq.Version;

public abstract class ConnectionMetaDataAdapter implements ConnectionMetaData {
    private static final String JMSVersion = "3.1";
    private static final int JMSMajorVersion = 3;
    private static final int JMSMinorVersion = 1;
    private static final String JMSProviderName = new Version().getProductName();
    private static final String providerVersion = "6.4";
    private static final int providerMajorVersion = 6;
    private static final int providerMinorVersion = 4;

    public static String getMqName() {
        return JMSProviderName;
    }

    public static String getMqVersion() {
        return providerVersion;
    }

    /**
     * @return the JMS version
     */
    @Override
    public final String getJMSVersion() {
        return JMSVersion;
    }

    /**
     * @return the JMS major version number
     */
    @Override
    public final int getJMSMajorVersion() {
        return JMSMajorVersion;
    }

    /**
     * @return the JMS minor version number
     */
    @Override
    public final int getJMSMinorVersion() {
        return JMSMinorVersion;
    }

    /**
     * @return the JMS provider name
     */
    @Override
    public final String getJMSProviderName() {
        return getMqName();
    }

    /**
     * @return the JMS provider version
     */
    @Override
    public final String getProviderVersion() {
        return getMqVersion();
    }

    /**
     * @return the JMS provider major version number
     */
    @Override
    public final int getProviderMajorVersion() {
        return providerMajorVersion;
    }

    /**
     * @return the JMS provider minor version number
     */
    @Override
    public final int getProviderMinorVersion() {
        return providerMinorVersion;
    }
}
