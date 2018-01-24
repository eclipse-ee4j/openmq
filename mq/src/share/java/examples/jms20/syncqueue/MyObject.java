/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.io.Serializable;

/** MyObject.java
  * 
  */

public class MyObject implements Serializable {

                private int value;

                MyObject(int value) {
                        this.value = value;
                }

                int getValue() {
                        return value;
                }

}
