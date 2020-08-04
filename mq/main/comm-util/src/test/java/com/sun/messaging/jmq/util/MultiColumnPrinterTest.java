/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation.
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
 * package com.sun.messaging.jmq.util;
 */

package com.sun.messaging.jmq.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class MultiColumnPrinterTest {
    private static final String SYSTEM_LINE_SEPARATOR = "*";

    static class TestedMultiColumnPrinter extends MultiColumnPrinter {
        private PrintWriter out;

        TestedMultiColumnPrinter(PrintWriter writer) {
            out = writer;
        }

        @Override
        public void doPrintln(String str) {
            out.print(str);
            out.print(SYSTEM_LINE_SEPARATOR);
            out.flush();
        }

        @Override
        public void doPrint(String str) {
            out.print(str);
            out.flush();
        }
    }

    private TestedMultiColumnPrinter mcp;

    private ByteArrayOutputStream baos;

    @BeforeEach
    public void setUp() {
        baos = new ByteArrayOutputStream();
        mcp = new TestedMultiColumnPrinter(new PrintWriter(baos));
    }

    @Test
    void testTwoColumns() {
        mcp.addTitle(new String[] { "E", null }, new int[] { 2, 0 });
        mcp.addTitle(new String[] { "m", "q" });
        mcp.add(new String[] { "M", "Q" });

        mcp.print();
        String printedOutput = baos.toString();

        assertThat(printedOutput).isEqualTo("E         " + SYSTEM_LINE_SEPARATOR + "m    q" + SYSTEM_LINE_SEPARATOR
                + "M    Q" + SYSTEM_LINE_SEPARATOR);
    }

    @Disabled(value = "Due to ArrayIndexOutOfBoundsException at MCP.print")
    @Test
    void testFourColumns() {
        mcp.setNumCol(4);
        mcp.addTitle(new String[] { "E", null, "F", null }, new int[] { 2, 0, 2, 0 });
        mcp.addTitle(new String[] { "1", "2", "3", "4" });
        mcp.add(new String[] { "o", "p", "e", "n" });

        mcp.print();
        String printedOutput = baos.toString();

        assertThat(printedOutput).isEqualTo("E         F         " + SYSTEM_LINE_SEPARATOR + "1    2    3    4" + SYSTEM_LINE_SEPARATOR
                + "o    p    e    n" + SYSTEM_LINE_SEPARATOR);
    }
}
