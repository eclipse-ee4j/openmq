/*
 * Copyright (c) 2021 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jms.ra;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.jms.MessageFormatException;

public class ConvertValue_NullValue_Test {
    private static final String nullValueOfMessage = "Attempting to read a null value as a primitive type must be treated as calling the primitive's corresponding valueOf(String) conversion method with a null value.";
    private static final String npexExpectedMessage = "CTS expects this exception type (NPEx) so should conform to it.";

    @FunctionalInterface
    private interface Converter {
        Object apply(Object o) throws MessageFormatException;
    }

    @ParameterizedTest
    @MethodSource("notThrowingOnNullValueConverters")
    void convertingNullShouldNotThrow(Converter converter) throws MessageFormatException {
        converter.apply(null);
    }

    static Stream<Arguments> notThrowingOnNullValueConverters() {
        return Stream.of(of(ConvertValue::toBoolean), of(ConvertValue::toString));
    }

    private static Arguments of(Converter converter) {
        return arguments(converter);
    }

    @ParameterizedTest
    @MethodSource({ "convertersAndValueOfSpecificExpectedExceptionClasses", "convertersAndStaticExpectedExceptionClasses" })
    void convertingNullToPrimitiveShouldThrow(Converter converter, Supplier<Class> exceptionClassSupplier, String expectationMessage)
            throws MessageFormatException {
        assertThrows(exceptionClassSupplier.get(), () -> converter.apply(null), expectationMessage);
    }

    static Stream<Arguments> convertersAndValueOfSpecificExpectedExceptionClasses() {
        return Stream.of(of(ConvertValue::toByte, ConvertValue_NullValue_Test::classOfByteValueOfNull, nullValueOfMessage),
                of(ConvertValue::toShort, ConvertValue_NullValue_Test::classOfShortValueOfNull, nullValueOfMessage),
                of(ConvertValue::toInt, ConvertValue_NullValue_Test::classOfIntValueOfNull, nullValueOfMessage),
                of(ConvertValue::toLong, ConvertValue_NullValue_Test::classOfLongValueOfNull, nullValueOfMessage));
    }

    public static Stream<Arguments> convertersAndStaticExpectedExceptionClasses() {
        return Stream.of(of(ConvertValue::toChar, ConvertValue_NullValue_Test::classOfCharValueOfNull, nullValueOfMessage),
                of(ConvertValue::toFloat, ConvertValue_NullValue_Test::classOfFloatValueOfNull, npexExpectedMessage),
                of(ConvertValue::toDouble, ConvertValue_NullValue_Test::classOfDoubleValueOfNull, npexExpectedMessage));
    }

    private static Arguments of(Converter converter, Supplier<Class> exceptionClass, String expectationMessage) {
        return arguments(converter, exceptionClass, expectationMessage);
    }

    private static Class classOfByteValueOfNull() {
        try {
            Byte.valueOf(null);
        } catch (Exception e) {
            return e.getClass();
        }
        return null;
    }

    private static Class classOfShortValueOfNull() {
        try {
            Short.valueOf(null);
        } catch (Exception e) {
            return e.getClass();
        }
        return null;
    }

    private static Class classOfIntValueOfNull() {
        try {
            Integer.valueOf(null);
        } catch (Exception e) {
            return e.getClass();
        }
        return null;
    }

    private static Class classOfLongValueOfNull() {
        try {
            Integer.valueOf(null);
        } catch (Exception e) {
            return e.getClass();
        }
        return null;
    }

    private static Class classOfCharValueOfNull() {
        return NullPointerException.class;
    }

    private static Class classOfFloatValueOfNull() {
        return NullPointerException.class;
    }

    private static Class classOfDoubleValueOfNull() {
        return NullPointerException.class;
    }
}
