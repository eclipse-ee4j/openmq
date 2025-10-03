/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.admin.apps.console;

import java.awt.BorderLayout;
import java.io.Serial;
import javax.swing.JPanel;
import javax.swing.JLabel;

/**
 * Splash screen for JMQ Administration console.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class SplashScreenInspector extends InspectorPanel {
    @Serial
    private static final long serialVersionUID = 5529198493272951928L;

    @Override
    public JPanel createWorkPanel() {
        JPanel panel = new JPanel();
        JLabel l = new JLabel(AGraphics.adminImages[AGraphics.SPLASH_SCREEN], JLabel.CENTER);

        panel.setLayout(new BorderLayout());
        panel.add(l, "Center");

        return (panel);
    }

    @Override
    public void inspectorInit() {
    }

    @Override
    public void clearSelection() {
    }

    @Override
    public void selectedObjectUpdated() {
    }
}
