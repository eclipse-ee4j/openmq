/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.util.lists;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class FifoSetIteratorTest {

    @Test
    void consecutiveNextsOnEmptySetShouldThrowNoSuchElementException() {
        consecutiveNextsOnEmptySetShouldThrowNoSuchElementException(new FifoSet());
    }

    @Test
    void consecutiveNextsOnStandardSetThrowNoSuchElementException() {
        consecutiveNextsOnEmptySetShouldThrowNoSuchElementException(new TreeSet());
    }

    private void consecutiveNextsOnEmptySetShouldThrowNoSuchElementException(Set s) {
        Iterator iterator = s.iterator();
        assertThrows(NoSuchElementException.class, () -> iterator.next());
        assertThrows(NoSuchElementException.class, () -> iterator.next());
    }
}
