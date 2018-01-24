/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/*
 * Class used to print metrics information received from the MQ broker.
 */
public class MetricsPrinter extends MultiColumnPrinter  {
    public MetricsPrinter(int numCols, int gap, String border, int align)  {
	super(numCols, gap, border, align);
    }

    public MetricsPrinter(int numCols, int gap, String border)  {
	super(numCols, gap, border);
    }

    public void doPrint(String str)  {
        System.out.print(str);
    }

    public void doPrintln(String str)  {
        System.out.println(str);
    }
}
