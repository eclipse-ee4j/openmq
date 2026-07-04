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

import static com.sun.messaging.jmq.util.options.OptionType.OPTION_VALUE_NEXT_ARG;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OptionParser_findMatchingOption_Test {
    @Nested
    class EmptyDescriptions {
        @Test
        void test() {
            assertThat(OptionParser.findMatchingOption(new OptionDesc[] {}, "any")).isLessThan(0);
        }
    }

    @Nested
    class NonEmptyDescriptions {
        private final OptionDesc[] options = {
                new OptionDesc("-javahome", OPTION_VALUE_NEXT_ARG, "", "", true),
                new OptionDesc("-jmqhome", OPTION_VALUE_NEXT_ARG, "", "", true),
        };

        @ParameterizedTest
        @CsvSource({
                "missing,-1",
                "-javahome,0",
                "-jmqhome,1",
        })
        void testNonEmptyDescriptions(String searchFor, int expectedIndex) {
            assertThat(OptionParser.findMatchingOption(options, searchFor)).isEqualTo(expectedIndex);
        }
    }
}
