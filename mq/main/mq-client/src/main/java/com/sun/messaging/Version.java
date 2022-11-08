/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging;

/**
 * This class provides the Version information about the product. The information provided is only returning the version
 * inofrmation for the client. It is quite possible that the broker may be running at a different version level. The
 * version information such as Major release version , Minor release version service pack ,Product build date , JMS API
 * version , Product name , Protocol version etc can be obtained through the various helper methods exposed by this
 * class.
 */

public final class Version {

    private com.sun.messaging.jmq.Version version = null;

    public Version() {
        version = new com.sun.messaging.jmq.Version(false /* not jar */);
    }

    /**
     * Returns the Major release of the Product version for example if the release value is 3.6.1 then the major version
     * value will be 3.
     *
     * @return an integer representing the major release version value if there is an exception returns -1
     */
    public int getMajorVersion() {
        return version.getMajorVersion();
    }

    /**
     * Returns the Minor release of the Product version for example if the release value is 3.6.1 then the returned value of
     * minor version will be 6
     *
     * @return an integer representing the minor release version value if there is an exception returns -1
     */
    public int getMinorVersion() {
        return version.getMinorVersion();
    }

    /**
     * Returns the product name example Eclipse OpenMQ(tm)
     *
     * @return String representing the name of the product
     */
    public String getProductName() {
        return version.getProductName();
    }

    /**
     * Returns the implementation version of the product example 3.6
     *
     * @return String representing the implementation version of the product
     */
    public String getImplementationVersion() {
        return version.getImplementationVersion();
    }

    /**
     * Returns the JMS API version the product implements example 1.1
     *
     * @return String representing the JMS API version which the product is compliant to
     */
    public String getTargetJMSVersion() {
        return version.getTargetJMSVersion();
    }

    /**
     * Returns the Version info of the product, this string is the concatanated value of pacakage name, API version,
     * Protocol version, JMS API version, and the patch information.
     *
     * @return the String value of the product version info
     */
    public String getVersion() {
        return version.getVersion();
    }

    /**
     * Returns the Version info in the form of an array of integer. Parse a version string into four integers. The four
     * integers represent the major product version , minor product version , micro product version and the service pack.
     * This method handles both MQ service pack convetions (sp1) and JDK patch convention (_01). It also handles the JDK
     * convetion of using a '-' to delimit a milestone string. In this case everything after the - is ignored. Examples:
     *
     * 3.0.1sp2 = 3 0 1 2 , 3.0.1 sp 2 = 3 0 1 2 , 3.5 sp 2 = 3 5 0 2 , 3.5.0.2 = 3 5 0 2 , 1.4.1_02 = 1 4 1 2 , 1.4_02 =
     * 1.4 0 2 , 2 = 2 0 0 0 , 1.4.1-beta2 = 1 4 1 0
     *
     * @param str String representing the product version
     * @return array of integers where int[0] = major Version int[1]=minor version int[3]=micro version int[4]= service pack
     *
     * @throws NumberFormatException
     */
    public static int[] getIntVersion(String str) {
        return com.sun.messaging.jmq.Version.getIntVersion(str);
    }

}
