/*
 * Copyright 2026 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OptionParser_parseArgs_Test {
    private final OptionDesc[] options = {
            new OptionDesc("-javahome", OptionType.OPTION_VALUE_NEXT_ARG, "JHOME", null),
            new OptionDesc("-debug", OptionType.OPTION_VALUE_HARDCODED, "DEBUG", "true"),
            new OptionDesc("-verbose", OptionType.OPTION_VALUE_HARDCODED, "VERBOSE", "true", true),
            new OptionDesc("-jmqhome", OptionType.OPTION_VALUE_NEXT_ARG, "MHOME", null),
            new OptionDesc("-loglevel", OptionType.OPTION_VALUE_NEXT_ARG, "LOGLEVEL", null, true),
            new OptionDesc("-name", OptionType.OPTION_VALUE_NEXT_ARG, "NAME", null, "name1=broker1"),
            new OptionDesc("-nar", OptionType.OPTION_VALUE_NEXT_ARG_RES, "NAR", null),
            new OptionDesc("-sr", OptionType.OPTION_VALUE_SUFFIX_RES, "SR", null),
    };

    @Nested
    class ProperArguments {
        private final String[] args = {
                "-javahome", "/usr/local/temurin-jdk",
                "-debug",
                "-verbose",
                "-jmqhome", "/tmp/mqvarhome",
                "-loglevel", "DEBUG",
                "-name", "name1=broker1",
                "-nar", "obj1=Integer1",
                "-nar", "obj2=Integer2",
                "-srCOMMAND=echo",
        };

        @ParameterizedTest
        @CsvSource({
                "JHOME,/usr/local/temurin-jdk",
                "DEBUG,true",
                "MHOME,/tmp/mqvarhome",
                "NAME,name1=broker1",
                "name1,broker1",
                "NAR.obj1,Integer1",
                "NAR.obj2,Integer2",
                "SR.COMMAND,echo",
        })
        void testExisting(String expectedPropertyName, String expectedPropertyValue) throws OptionException {
            Properties parsedProperties = new Properties();

            OptionParser.parseArgs(args, options, parsedProperties);

            assertThat(parsedProperties).contains(Map.entry(expectedPropertyName, expectedPropertyValue));
        }

        @ParameterizedTest
        @CsvSource({
                "VERBOSE",
                "LOGLEVEL",
                "NAR",
                "SR",
        })
        void testIgnored(String unexpectedPropertyName) throws OptionException {
            Properties parsedProperties = new Properties();

            OptionParser.parseArgs(args, options, parsedProperties);

            assertThat(parsedProperties).doesNotContainKey(unexpectedPropertyName);
        }

    }

    @Nested
    class UndescribedArguments {
        private final String[] args = {
                "-fine",
                "-extra",
        };

        @Test
        void test() throws OptionException {
            Properties parsedProperties = new Properties();

            assertThatExceptionOfType(UnrecognizedOptionException.class)
                    .isThrownBy(() -> OptionParser.parseArgs(args, options, parsedProperties));
        }
    }
}
