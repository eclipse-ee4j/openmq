/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import javax.transaction.xa.Xid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebugConvertersTest {
  @Mock
  private Xid xid;

  @BeforeEach
  void setupXid() {
    Mockito.when(xid.getGlobalTransactionId()).thenReturn(new byte[] { 65, 66, 67 });
    Mockito.when(xid.getBranchQualifier()).thenReturn(new byte[] { 67, 66, 65 });
  }

  @Test
  void testPrintingXid() throws Exception {
    var stringified = DebugConverters.toString(xid);
    assertThat(stringified).isEqualTo("(GlobalTransactionID=[65, 66, 67], BranchQualifier=[67, 66, 65]) ");
  }
}

