/*
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

import java.util.function.Supplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicXDestinationTest {
    interface DestinationFactory<D extends Destination> {
        D newNullNameDestination();

        D newNamedDestination(String name) throws Exception;
    }

    abstract static class WithDestinationFactory<D extends Destination> implements Supplier<DestinationFactory<D>> {
        final D newNullNameDestination() { return get().newNullNameDestination(); }

        final D newNamedDestination(String name) throws Exception { return get().newNamedDestination(name); }
    }

    abstract static class EqualsForNullNameTest<D extends Destination> extends WithDestinationFactory<D> {
        @Test
        public void testEqualsAgainstNullNameDestination() {
            Destination tested = newNullNameDestination();

            Destination second = newNullNameDestination();

            assertThat(tested).isEqualTo(second);
        }

        @Test
        public void testEqualsAgainstNonNullNameDestination() throws Exception {
            Destination tested = newNullNameDestination();

            Destination second = newNamedDestination("mqTest");

            assertThat(tested).isNotEqualTo(second);
        }
    }

    abstract static class EqualsForNonNullNameTest<D extends Destination> extends WithDestinationFactory<D> {
        @Test
        public void testEqualsAgainstNullNameDestination() throws Exception {
            Destination tested = newNamedDestination("mqCheck");

            Destination second = newNullNameDestination();

            assertThat(tested).isNotEqualTo(second);
        }

        @Test
        public void testEqualsAgainstNonNullNameDestination() throws Exception {
            Destination tested = newNamedDestination("mqCheck");

            Destination second = newNamedDestination("mqCheck");

            assertThat(tested).isEqualTo(second);
        }
    }

    private DestinationFactory<BasicQueue> queueFactory = new DestinationFactory<>() {
        @Override
        public BasicQueue newNullNameDestination() { return new BasicQueue(); }

        @Override
        public BasicQueue newNamedDestination(String name) throws Exception { return new BasicQueue(name); }
    };

    private DestinationFactory<BasicTopic> topicFactory = new DestinationFactory<>() {
        @Override
        public BasicTopic newNullNameDestination() { return new BasicTopic(); }

        @Override
        public BasicTopic newNamedDestination(String name) throws Exception { return new BasicTopic(name); }
    };

    @Nested
    class BasicQueueEqualsForNullNameTest extends EqualsForNullNameTest<BasicQueue> {
        @Override
        public DestinationFactory<BasicQueue> get() { return queueFactory; }
    }

    @Nested
    class BasicTopicEqualsForNullNameTest extends EqualsForNullNameTest<BasicTopic> {
        @Override
        public DestinationFactory<BasicTopic> get() { return topicFactory; }
    }

    @Nested
    class BasicQueueEqualsForNonNullNameTest extends EqualsForNonNullNameTest<BasicQueue> {
        @Override
        public DestinationFactory<BasicQueue> get() { return queueFactory; }
    }

    @Nested
    class BasicTopicEqualsForNonNullNameTest extends EqualsForNonNullNameTest<BasicTopic> {
        @Override
        public DestinationFactory<BasicTopic> get() { return topicFactory; }
    }
}

