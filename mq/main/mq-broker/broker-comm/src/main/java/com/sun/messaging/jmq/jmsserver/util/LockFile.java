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
 * @(#)LockFile.java	1.11 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.util;

import java.io.*;
import java.nio.channels.FileLock;
import java.net.*;
import java.util.*;

import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.*;

/**
 * This class encapsulates a broker lock file. The lock file makes sure
 * that no two brokers using the same instance name are running at the
 * same time (using different port numbers). The algorithm goes like this:
 *
 * Try to create a lock file in $JMQ_VARHOME/instances/<instancename>lock
 * If lock didn't exist previously and was created then we got the
 *    lock write <instancename>:hostname:port\n to lock file and return.
 * Else if lock file already exists read it.
 * If contents of lock file match the instancename, hostname and port
 *    of this broker then the lock file was left over from a previous
 *    run of this broker and we assume we got the lock and return.
 * Else try to connect to the broker on host:port to see if it is still up.
 * If we connect to broker then we failed to get lock. return.
 * Else assume the lock file is cruft. Remove it and try to acquire again.
 *     
 */

public class LockFile
{
    private static LockFile currentLockFile = null;

    private String hostname = null;
    private String instance = null;
    private String filePath = null;
    private int    port = 0;
    private boolean isMyLock = false;


    private LockFile() {
    }

    private LockFile(String instance, String hostname, int port) {
        this.hostname = hostname;
        this.instance = instance;
        this.port = port;
    }

    public static synchronized void clearLock()
    {
        currentLockFile = null;
    }

    /**
     * Get the lock file for the specified instance of the broker.
     * If no lock file exists one is created using the parameters
     * provided. If one does exist it is loaded.
     * The caller should use the isMyLock() method on the returned
     * LockFile to determine if it acquired the lock, or if somebody
     * else has it.
     *
     */
    public synchronized static LockFile getLock(String varhome,
				       String instance,
				       String hostname,
				       int port, boolean useFileLock)
        throws IOException
    {

        LockFile lf = null;
        File file = new File(getLockFilePath(varhome, instance));

        // Grab lock by creating lock file. 
        if (file.createNewFile()) {
            // Got the lock! Lock file didn't exist and was created.
            // Write info to it and register for it to be removed on VM exit
            lf = new LockFile(instance, hostname, port);
            lf.filePath = file.getCanonicalPath();
            lf.writeLockFile(file, useFileLock, true);
            lf.isMyLock = true;
            file.deleteOnExit();
	    currentLockFile = lf;
            return lf;
        }

        // Lock file already exists. Read in contents
        lf = loadLockFile(file, useFileLock);
        if (lf == null) { 
            lf = new LockFile(instance, hostname, port);
            lf.filePath = file.getCanonicalPath();
            lf.writeLockFile(file, useFileLock, true);
            lf.isMyLock = true;
            file.deleteOnExit();
	    currentLockFile = lf;
            return lf;
        }
        lf.filePath = file.getCanonicalPath();

        // Check if it is ours (maybe left over if we previously crashed).
        if (port == lf.getPort() &&
            equivalentHostNames(hostname, lf.getHost(), false) &&
            instance.equals(lf.getInstance())) {

            // It's ours! No need to read-write it.
            file.deleteOnExit();
            lf.isMyLock = true;
	        currentLockFile = lf;
            return lf;
        } else if ( port == lf.getPort() &&
        		    isSameIP (hostname, lf.getHost()) &&
        		    instance.equals(lf.getInstance())
        		) {
        	
        	//update hostname if same ip with diff host name
        	lf.updateHostname(hostname, useFileLock);
            file.deleteOnExit();
            lf.isMyLock = true;
	        currentLockFile = lf;
            return lf;
        }

        // Not ours. See if owner is still running
        // Try opening socket to other broker's portmapper. If we
        // can open a socket then the lock file is in use.
        try (Socket s = new Socket(InetAddress.getByName(lf.hostname),
                                   lf.port)) {
            lf.isMyLock = false;
        } catch (IOException e) {
            // Looks like owner is not running. Take lock
            if (!file.delete()) {
                throw new IOException(CommGlobals.getBrokerResources()
                    .getString(BrokerResources.X_LOCKFILE_BADDEL));
            }
            // Lock file should be gone, resursive call should acquire it
            return getLock(varhome, instance, hostname, port, useFileLock);
        } 

	currentLockFile = lf;
        return lf;
    }
    
    /**
     * check if host1 and host 2 have the same IP address.
     * @param host1
     * @param host2
     * @return true if we can obtain IPs from host1 and host2 and they are the equal.
     */
    public static boolean isSameIP (String host1, String host2) {
    	
    	boolean sflag = false;
    	
    	try {
    		
    		String addr1 = InetAddress.getByName(host1).getHostAddress();
    		
    		String addr2 = InetAddress.getByName(host2).getHostAddress();
    		
    		if ( addr1.equals(addr2) ) {
    			sflag = true;
    		}
    		
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return sflag;
    }

    /**
     * Return the path to the lock file
     */
    public static String getLockFilePath(String varhome, String instance) {
        return varhome + File.separator + CommGlobals.INSTANCES_HOME_DIRECTORY +
		File.separator + instance + File.separator + "lock";
    }

    /**
     * Returns true if this process acquired the lock. Returns false
     * if another process has the lock
     */
    public boolean isMyLock() {
        return isMyLock;
    }

    public String getHost() {
        return hostname;
    }

    public String getInstance() {
        return instance;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getPort() {
        return port;
    }

    public String toString() {
        return instance + " " + hostname + ":" + port + " (" + isMyLock + ")";
    }

    /**
     * Update the port number in the lock file. Needed because the
     * broker's port number may change via admin while it is running
     */
    public void updatePort(int port, boolean useFileLock)
    throws IOException {

        File file = new File(filePath);
	int oldPort = this.port;

	this.port = port;

	try {
            writeLockFile(file, useFileLock);
	} catch (IOException e) {
	    this.port = oldPort;
	    throw e;
        }
    }

    /**
     * Update the hostname in the lock file. Needed because the
     * broker's hostname may change via admin while it is running
     */
    public void updateHostname(String hostname, boolean useFileLock)
    throws IOException {

        File file = new File(filePath);
	String oldHostname = this.hostname;

	this.hostname = hostname;

	try {
            writeLockFile(file, useFileLock);
	} catch (IOException e) {
	    this.hostname = oldHostname;
	    throw e;
        }
    }

    /**
     * Load the lock file. Does not attempt to acquire it.
     *
     * @return null if file is empty and useFileLock true 
     */
    public static synchronized LockFile loadLockFile(
        File file, boolean useFileLock)
        throws IOException {

        byte[] data = new byte[128];
        LockFile lf = new LockFile();

        FileInputStream fis = new FileInputStream(file);
        FileLock filelock = null;
        try {
            if (useFileLock) {
                if (file.length() == 0) {
                    //The other broker process may have just created it
                    return null;
                } else {
                    // get shared-lock
                    filelock = fis.getChannel().tryLock(0L, Long.MAX_VALUE, true); 
                    if (filelock == null) {
                        try { //try once more 
                            Thread.sleep(500);
                        } catch (Exception e) {/* ignore */}
                        filelock = fis.getChannel().tryLock(0L, Long.MAX_VALUE, true); 
                        if (filelock == null) {
                            throw new IOException(
                                CommGlobals.getBrokerResources().
                                getKString(BrokerResources.X_OBTAIN_SHARED_LOCK_FILE,
                                file.toString()));
                        }
                    }
                }
            }
            fis.read(data);
            String s = new String(data, "UTF8");
            StringTokenizer st = null;
            int i1 = s.indexOf(':');
            if (i1 == -1) {
                throw new IOException(
                    CommGlobals.getBrokerResources().
                    getKString(BrokerResources.X_LOCKFILE_CONTENT_FORMAT,
                    file.toString(), "["+s+"]"));
            }
            st = new StringTokenizer(s.substring(0, i1), " \t\n\r\f");
            lf.instance = st.nextToken();
            int i2 = s.lastIndexOf(':');
            if (i2 == -1 || i1 == i2) {
                throw new IOException(
                    CommGlobals.getBrokerResources().getKString(
                    BrokerResources.X_LOCKFILE_CONTENT_FORMAT, 
                    file.toString(), "["+s+"]"));
            }
            st = new StringTokenizer(s.substring(i2+1), " \t\n\r\f");
            lf.port = Integer.parseInt(st.nextToken());
            st = new StringTokenizer(s.substring(i1+1, i2), " \t\n\r\f");
            lf.hostname = st.nextToken();
       } finally {
            if (filelock != null) {
                filelock.release();
            }
            fis.close();
       }

       return lf;
    }

    /**
     * Write the lock file. Assumes the file already exists.
     */
    public void writeLockFile(File file, boolean useFileLock)
    throws IOException {
        writeLockFile(file, useFileLock, false);
    }

    private synchronized void writeLockFile(File file, 
        boolean useFileLock, boolean checkEmpty)
        throws IOException {

        String data = instance + ":" + hostname + ":" + port + "\n";

        FileOutputStream os = new FileOutputStream(file);
        FileLock filelock = null;
        try {
            if (useFileLock) {
                //get exclusive-lock
                filelock = os.getChannel().tryLock();
                if (filelock == null) {
                    try { //try once more 
                        Thread.sleep(500);
                    } catch (Exception e) {/* ignore */}
                    filelock = os.getChannel().tryLock();
                    if (filelock == null) {
                        throw new IOException(
                            CommGlobals.getBrokerResources().
                            getKString(BrokerResources.X_OBTAIN_EXCLUSIVE_LOCK_FILE,
                            file.toString(), this.toString()));
                    }
                }
                if (checkEmpty && file.length() != 0) {
                    throw new IOException(
                        CommGlobals.getBrokerResources().getKString(
                        BrokerResources.X_OBTAIN_EXCLUSIVE_LOCK_FILE_EMPTY,
                        file, this.toString())); 
                }
            }
            os.write(data.getBytes("UTF8"));
            os.getChannel().force(false);
        } finally {
           if (filelock != null) {
               filelock.release();
           }
           os.close();
        }
        return;
    }

    /**
     * Get the current lock file
     */
    public static LockFile getCurrentLockFile() {
	return currentLockFile;
    }

    /**
     * Check if two hostname strings are equivalent. Note this is just
     * a simple string comparison.
     *
     * If "exact" is true then the two strings must match exactly.
     * Otherwise one string can be an unqualified version of the other.
     * For example if "exact" is false then the following are considered
     * equivalent: foo.central, foo.central.sun.com, foo. But foo.east
     * would not be equivalent.
     *
     * @param   h1  First hostname
     * @param   h2  Second hostname
     * @param   exact   True to perform an exact match. False to perform
     *                  a unqualified match.
     *
     */
    public static boolean equivalentHostNames(
                        String h1, String h2, boolean exact) {

        if (exact) {
            // Check for exact match
            return h1.equals(h2);
        }

        // Split hostnames by dots and make sure each component matches
        StringTokenizer st1 = new StringTokenizer(h1, ".");
        StringTokenizer st2 = new StringTokenizer(h2, ".");
        while (st1.hasMoreTokens() && st2.hasMoreTokens()) {
            if (!st1.nextToken().equals(st2.nextToken())) {
                return false;
            }
        }

        return true;
    }
}

