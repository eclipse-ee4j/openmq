/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util;

import java.io.*;

/**
 * This interface encapsulates passfile obfuscation/deobfuscation
 *
 * A MQ un-obfuscated passfile has format of properties file with MQ property name=value pairs where 'value' is a
 * password
 */
public interface PassfileObfuscator {

    /**
     * @param source the fully qualified file name of the passfile to be obfuscated
     * @param target the fully qualified file name for the obfuscated passfile
     * @param prefix property name prefix for all name=value pairs
     */
    void obfuscateFile(String source, String target, String prefix) throws IOException;

    /**
     * @param source the fully qualified file name of the passfile to be deobfuscated
     * @param target the fully qualified file name for the deobfuscated passfile
     * @param prefix property name prefix for all name=value pairs
     */
    void deobfuscateFile(String source, String target, String prefix) throws IOException;

    /**
     * @param source the fully qualified file name of the passfile
     * @param prefix property name prefix for all name=value pairs
     * @return InputStream of the deobfuscated passfile
     */
    InputStream retrieveObfuscatedFile(String source, String prefix) throws IOException;

    /**
     * @param source the fully qualified file name of the passfile
     * @param prefix property name prefix for all name=value pairs
     * @return true if the passfile is identified as obfuscated by the PassfileObfuscator
     */
    boolean isObfuscated(String source, String prefix) throws IOException;

}
