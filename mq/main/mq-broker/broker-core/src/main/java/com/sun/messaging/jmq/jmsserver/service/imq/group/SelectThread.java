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
 * @(#)SelectThread.java	1.29 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.group;

import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.channels.spi.*;
import java.nio.channels.*;
import com.sun.messaging.jmq.util.log.*;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.pool.*;


abstract class SelectThread 
{
    protected Logger logger = Globals.getLogger();
    protected static boolean DEBUG = GroupService.DEBUG;

    protected static final long DEF_TIMEOUT = 120*1000;
    protected long TIMEOUT = Globals.getConfig().
                          getLongProperty(Globals.IMQ +
                             ".shared.timeout", DEF_TIMEOUT);

    

    GroupRunnable parent = null;

    protected String type = "";

    int id = 0;

    private static int LASTID = 0;

    Map all_connections = Collections.synchronizedMap(new HashMap());
    List pending_connections = Collections.synchronizedList(new LinkedList());
    Set cancel_connections = new HashSet();

    HashMap key_con_map = new HashMap();

    Selector selector = null;
    boolean valid = true;
    GroupService svc = null;

    // interestOps set at creation
    protected int INITIAL_KEY = 0; // none

    // interestOps that this thread handles
    protected int POSSIBLE_MASK = 0; // none

    private MapEntry selectorListMapEntry = null;

    public String getStateInfo() {
        return  "[svc,item] = " + svc + "," + selectorListMapEntry 
           + " [a,p,c] = " 
           + all_connections.size() + ","
           + pending_connections.size() + ","
           + cancel_connections.size() + "]";
    }

    protected static String keyMaskToString(int mask) 
    {
        String str = "";
        if ((mask & SelectionKey.OP_ACCEPT) ==  SelectionKey.OP_ACCEPT) {
            str += "OP_ACCEPT ";
        }
        if ((mask & SelectionKey.OP_CONNECT) ==  SelectionKey.OP_CONNECT) {
            str += "OP_CONNECT ";
        }
        if ((mask & SelectionKey.OP_READ) ==  SelectionKey.OP_READ) {
            str += "OP_READ ";
        }
        if ((mask & SelectionKey.OP_WRITE) ==  SelectionKey.OP_WRITE) {
            str += "OP_WRITE";
        }
        return str;
    }

    public synchronized Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("INITIAL_KEY", keyMaskToString(INITIAL_KEY));
        ht.put("POSSIBLE_MASK", keyMaskToString(POSSIBLE_MASK));
        ht.put("valid", Boolean.valueOf(valid));
        synchronized (all_connections) {
            ht.put("all_connections#", Integer.valueOf(all_connections.size()));
            Vector v = new Vector();
            Iterator itr = all_connections.values().iterator();
            while (itr.hasNext()) {
                IMQIPConnection con = (IMQIPConnection)itr.next();
                v.add(Long.valueOf(con.getConnectionUID().longValue()));
            }
            ht.put("all_connections", v);
        }
        synchronized (pending_connections) {
            ht.put("pending_connections#", 
                   Integer.valueOf(pending_connections.size()));
        }
        synchronized (cancel_connections) {
            ht.put("cancel_connections#", 
                   Integer.valueOf(cancel_connections.size()));
        }
        if (selector != null) {
            int cnt = 0;
            try {
                selector.wakeup();
                cnt = selector.selectNow();
            } catch (Exception ex) {
                //acceptable exception
                logger.log(Logger.DEBUGHIGH,"Exception in select ", ex);
            }
            ht.put("selector(cnt)", Integer.valueOf(cnt));
            Set s = selector.selectedKeys();
            ht.put("selector(selectedKeys#)", Integer.valueOf(s.size()));
            Vector sv = new Vector();
            Iterator itr = s.iterator();
            while (itr.hasNext()) {
                SelectionKey sk = (SelectionKey)itr.next();
                IMQIPConnection ic = (IMQIPConnection)sk.attachment();
                sv.add("interest=" + keyMaskToString(sk.interestOps())
                      + " ready=" + keyMaskToString(sk.readyOps())
                      + " conuid = " + (ic == null ? "none" :
                            String.valueOf(ic.getConnectionUID().longValue())));
            }
            ht.put("Selector(selectedKeys)", sv);
            s = selector.keys();
            ht.put("selector(keys#)", Integer.valueOf(s.size()));
            sv = new Vector();
            itr = s.iterator();
            while (itr.hasNext()) {
                SelectionKey sk = (SelectionKey)itr.next();
                IMQIPConnection ic = (IMQIPConnection)sk.attachment();
                sv.add("interest=" + keyMaskToString(sk.interestOps())
                      + " ready=" + keyMaskToString(sk.readyOps())
                      + " conuid = " + (ic == null ? "none" :
                            String.valueOf(ic.getConnectionUID().longValue())));
            }
            ht.put("Selector(keys)", sv);
        }
        return ht;
    }


    public synchronized boolean isValid() {
        return valid;
    }

    public void assign(GroupRunnable parent) {
        this.parent = parent;
    }

    public GroupRunnable getParent() {
        return parent;
    }

    public void free(GroupRunnable runner) {
        destroy("Unknown free");
        this.parent = null;
    }


    public SelectThread(Service svc, MapEntry entry) 
        throws IOException
    {
        selector = Selector.open();
        this.svc = (GroupService)svc;
        this.selectorListMapEntry = entry;
        synchronized (SelectThread.class) {
            this.id = LASTID ++;
        }
    }

    public Selector getSelector() {
        return selector;
    }

    public int size() {
        return all_connections.size();
    }

    /**
     * current + pending connections
     */
    public int totalSize() {
        return all_connections.size() + pending_connections.size();
    }

    public void addNewConnection(IMQIPConnection conn)
        throws IOException
    {
        synchronized (pending_connections) {
            if (!isValid()) {
                throw new IOException(this +" has been destroyed ");
            }
            pending_connections.add(conn);
            wakeup();
        }
    }



    public void removeConnection(IMQIPConnection con, String reason) 
        throws IOException 
    {
        SelectionKey key = null;
        synchronized (key_con_map) {
            key = (SelectionKey)key_con_map.get(con.getConnectionUID());
        }
        if (key != null)
                changeInterest(key, -1, reason);
    }


    protected SelectionKey processPendingConnection(IMQIPConnection con) 
        throws IOException
    {

        if (con == null) return null;

        synchronized (all_connections) {
            all_connections.put(con.getConnectionUID(), con);
        }

        AbstractSelectableChannel sch = con.getChannel();

        if (sch == null) {
            throw new IOException("Connection " + con + 
                   " no longer has a valid channel");
        }

        SelectionKey key = sch.register(selector,
            	    INITIAL_KEY);


        if (key == null) return null;

        key.attach(con);
        GroupNotificationInfo ninfo = (GroupNotificationInfo)
                  con.attachment();

        if (ninfo == null) return null;
        ninfo.setThread(POSSIBLE_MASK, this, key);
        synchronized (key_con_map) {
            key_con_map.put(con.getConnectionUID(), key);
        }
        return key;
    }

    HashMap reasons = new HashMap();
    public void changeInterest(SelectionKey key, int mask, String reason) 
        throws IOException
    {
        if (mask == -1) {
            // cancel
            synchronized (cancel_connections) {
                cancel_connections.add(key);
                reasons.put(key, reason);
             }
             wakeup();
        }
    }

    public synchronized void destroy(String reason)
    {
        synchronized (this) {
            valid = false;
        }
        if (selector != null) {
            try {
                synchronized (all_connections) {
                    Iterator itr = all_connections.values().iterator();
                    while (itr.hasNext()) {
                        IMQIPConnection con = (IMQIPConnection) itr.next();
                        removeConnection(con, reason);
                    }
                }
                selector.close();
            } catch (IOException ex) {
                //OK -> closing
                logger.log(Logger.DEBUG, "exception closing" , ex);
            } finally {
                selector = null;
            }
        }
    }

    // list used in the process thread to minimize time
    // holding lock during cancel processing AND to 
    // prevent holding a lock while calling out
    //
    List cancellist = new ArrayList();

    public void processThread() 
        throws Exception
    {
        while (true) {
            if (!valid) {
                logger.log(Logger.DEBUG,"SelectThread " + this + 
                   " no longer valid" );
                break;
            }
            IMQIPConnection con = null;
            synchronized (pending_connections) {
                if (pending_connections.size() > 0) {
                    con = (IMQIPConnection)pending_connections.remove(0);
                } else {
                    break;
                }
            }
            try {
                processPendingConnection(con);
            } catch (IOException ex) {
                logger.logStack(Logger.DEBUG,"Exception on pending con " + con + " : can not process", ex);
            } finally {
            }
        }
        HashMap cancelreasons = null;
        synchronized (cancel_connections) {
            if (cancel_connections.size() > 0) {
                 cancelreasons = new HashMap();
                 if (reasons != null) {
                     cancelreasons.putAll(reasons);
                     reasons.clear();
                 }
                 cancellist.clear();
                 cancellist.addAll(cancel_connections);
                 cancel_connections.clear();
            }
        }
        if (cancellist.size() > 0) {
            Iterator cancelitr = cancellist.iterator();
            while (cancelitr.hasNext()) {
               SelectionKey key = (SelectionKey)cancelitr.next();
               IMQIPConnection con = (IMQIPConnection)key.attachment();
               Channel chl = key.channel();
               String reason = (cancelreasons == null ? "unknown"
                       : (String)cancelreasons.remove(key));

               try {
                   key.cancel();
               } catch (Exception ex) {
                   // if anything goes wrong .. its
                   // OK .. technically this shouldnt throw
                   // any acceptions but it has in the past
                   // just log at the dbeug level
                   logger.log(Logger.DEBUG, "exception cancling key", ex);
               }
               try {
                   if (chl != null) {
                       Socket soc = ((SocketChannel)chl).socket();
                       chl.close();
                       soc.close();
                   }
               } catch (Exception ex) {
                    //OK -> closing
                    logger.log(Logger.DEBUG, "closing ", ex);
               }
               try {
                    synchronized (pending_connections) {
                        pending_connections.remove(con);
                    }
                    synchronized (all_connections) {
                        all_connections.remove(con.getConnectionUID());
                    }
                    synchronized (key_con_map) {
                        key_con_map.remove(con.getConnectionUID());
                    }
               } catch (Exception ex) {
                    //OK -> closing
                    logger.log(Logger.DEBUG, "closing ", ex);

               }

               try {
                    con.destroyConnection(false, GoodbyeReason.CLIENT_CLOSED,
                            reason);
               } catch (Exception ex) {
                    //OK -> closing
                    logger.log(Logger.DEBUG, "destroying con", ex);
               } 
                       
            }
            cancellist.clear();

        }
        if (valid) {
            try {
                process();
            } catch (Exception ex) {
logger.logStack(Logger.INFO,"Exception processing " + this , ex);
            } finally {
            }
        }

         
    }


    public boolean isBusy() {
         synchronized (all_connections) {
             synchronized (pending_connections) {
                 synchronized (this) {
                     return valid && (!all_connections.isEmpty()
                            || !pending_connections.isEmpty());
                 }
             }
         }
    }

    abstract protected void wakeup() 
        throws IOException;

    abstract protected void process()
        throws IOException;


    public String toString() {
        return "SelectThread[" + type + ":" + id + "]";
    }
}

