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

package com.sun.messaging.visualvm;

import com.sun.tools.visualvm.application.type.ApplicationType;
import java.awt.Image;
import org.openide.util.ImageUtilities;

public class MQBrokerApplicationType extends ApplicationType {

    protected final int appPID;
    private static final Image NODE_ICON = ImageUtilities.loadImage("com/sun/messaging/visualvm/ui/resources/dukeplug16.gif", true);

    public MQBrokerApplicationType(int pid) {
        appPID = pid;
    }

    @Override
    public String getName() {
        return "Message Queue Broker";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Application type for Message Queue Broker";
    }

    @Override
    public Image getIcon() {
        return (NODE_ICON);
    }
}
