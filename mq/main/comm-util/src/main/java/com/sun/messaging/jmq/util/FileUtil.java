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

/*
 * @(#)FileUtil.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * A class which encapsulates some common File and Directory operations.
 */
public class FileUtil  { 

    // not to be instantiated
    FileUtil() {
    }

    /**
     * Recursively remove all files and directories under and
     * including path depending on the value of removeTopDir.
     * If path is a directory and removeTopDir is true, the top directory
     * will be removed as well, otherwise the top directory will not
     * be removed.
     * If path is a file, it will be removed regardless of the value of
     * removeTopDir.
     *
     * @param path      File or directory to be removed.
     * @param removeTopDir      If true, the top directory will be removed.
     */
    public static void removeFiles(File path, boolean removeTopDir)
	throws IOException {

	if (!path.exists()) return;

	if (!path.isDirectory()) {
	    // delete the file
	    if (!path.delete()) {
		throw new IOException("failed to delete "+path);
	    }
	} else {
	    String[] files = path.list();
	    if (files != null) {
		for (int i = 0; i < files.length; i++) {
		    removeFiles(new File(path, files[i]), true);
		}
	    }

	    // remove the directory
	    if (removeTopDir && !path.delete()) {
		throw new IOException("failed to delete "+path);
	    }
	}
    }

    /**
     * Returns the absolute canonical path to the file/dir of the path passed in.
     * If for whatever reason, this cannot be determined, the path that is passed
     * in is returned.
     *
     * @param path      Path of file/directory
     * @return 		Canonical version of path param or the path param
     *			itself if the canonical path cannot be determined.
     */
    public static String getCanonicalPath(String path)  {
	File f = new File(path);

	try  {
	    return (f.getCanonicalPath());
	} catch (Exception e)  {
	    return (path);
	}
    }

    /**
     * Copies all files under srcDir to dstDir.
     * If dstDir does not exist, it will be created.
     */
    public static void copyDirectory(File srcDir, File dstDir) throws IOException {
        if (srcDir.isDirectory()) {
            if (!dstDir.exists()) {
                dstDir.mkdir();
            }

            String[] children = srcDir.list();
            for (int i=0; i<children.length; i++) {
                copyDirectory(new File(srcDir, children[i]),
                              new File(dstDir, children[i]));
            }
        } else {
            copyFile(srcDir, dstDir);
        }
    }

    /**
     * Copies src file to dst file.
     * If the dst file does not exist, it is created
     */
    public static void copyFile(File src, File dst) throws IOException {
        // Create channel on the source & destination
        FileChannel srcChannel = new FileInputStream(src).getChannel();
        FileChannel dstChannel = new FileOutputStream(dst).getChannel();

        // Copy file contents from source to destination
        dstChannel.transferFrom(srcChannel, 0, srcChannel.size());

        // Close the channels
        srcChannel.close();
        dstChannel.close();
    }

/* LKS
    public static void main(String[] args) throws Exception {

	String filename = null;
	if (args.length > 1)  {
	    if (args[0].equalsIgnoreCase("-rmdir"))  {
		filename = args[1];
	    }
	}

	if (filename != null) {
	    FileUtil.removeFiles(new File(filename), true);
	}
    }
*/

}
