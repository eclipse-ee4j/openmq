/*
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver;

import java.util.*;

import com.sun.messaging.jmq.util.DiagManager;
import com.sun.messaging.jmq.util.DiagDictionaryEntry;

class VMDiagnostics implements DiagManager.Data {
    /**
     * These variables are accessed via reflection
     */
    long max = 0;
    long total = 0;
    long free = 0;
    long used = 0;

    ArrayList dictionary = null;

    @Override
    public synchronized void update() {
        Runtime rt = Runtime.getRuntime();

        max = rt.maxMemory();
        total = rt.totalMemory();
        free = rt.freeMemory();
        used = total - free;
    }

    @Override
    public synchronized List getDictionary() {
        if (dictionary == null) {
            dictionary = new ArrayList();
            dictionary.add(new DiagDictionaryEntry("max", DiagManager.VARIABLE));
            dictionary.add(new DiagDictionaryEntry("total", DiagManager.VARIABLE));
            dictionary.add(new DiagDictionaryEntry("free", DiagManager.VARIABLE));
            dictionary.add(new DiagDictionaryEntry("used", DiagManager.VARIABLE));
        }
        return dictionary;
    }

    @Override
    public String getPrefix() {
        return "java.vm.heap";
    }

    @Override
    public String getTitle() {
        return "Java VM Heap";
    }
}
