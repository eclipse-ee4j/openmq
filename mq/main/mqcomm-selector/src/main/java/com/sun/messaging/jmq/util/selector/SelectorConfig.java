/*
 * Copyright (c) 2000, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2025 Contributors to Eclipse Foundation
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

package com.sun.messaging.jmq.util.selector;

public final class SelectorConfig {
    /*
     * True to short circuit boolean evaluation. For example if you have "e1 AND e2", you do not need to evaluate e2 if e1
     * is false. This boolean is just a safetyvalve in case there is a flaw in the shortCircuit algorithm
     */
    private static boolean shortCircuit = true;
    private static boolean shortCircuitCompileTimeTest = true;

    /*
     * The JMS specification specifically states that type conversions between Strings and numeric values should not be
     * performed. See JMS 1.1 section 3.8.1.1. For example if you set a string property: msg.setStringProperty("count", "2")
     * then the following should evaluate to false because a string cannot be used in an arithmetic expression: "count = 2"
     * The above expression should be "count = '2'" The older selector implementation supported this type conversion for
     * some expressions. This introduces the possibility that some applications may be relying on this bug, and will break
     * now that it is fixed. This boolean let's use switch to the old behavior.
     *
     * If convertTypes is true, then the selector evaluator will convert string values to numeric values. Currently for only
     * = and <>
     */
    private static boolean convertTypes = false;

    public static void setShortCircuit(boolean b) {
        shortCircuit = b;
    }

    public static boolean getShortCircuit() {
        return shortCircuit;
    }

    public static void setShortCircuitCompileTimeTest(boolean b) {
        shortCircuitCompileTimeTest = b;
    }

    public static boolean getShortCircuitCompileTimeTest() {
        return shortCircuitCompileTimeTest;
    }

    public static void setConvertTypes(boolean b) {
        convertTypes = b;
    }

    public static boolean getConvertTypes() {
        return convertTypes;
    }
}
