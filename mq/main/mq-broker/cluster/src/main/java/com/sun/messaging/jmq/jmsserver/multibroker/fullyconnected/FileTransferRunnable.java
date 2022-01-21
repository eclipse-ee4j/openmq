/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.multibroker.fullyconnected;

import java.util.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

import com.sun.messaging.jmq.io.GPacket;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterTransferFileListInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterTransferFileStartInfo;
import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ClusterTransferFileEndInfo;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;

import com.sun.messaging.jmq.jmsserver.multibroker.raptor.ProtocolGlobals;

class FileTransferRunnable implements Runnable {

    Logger logger = Globals.getLogger();
    BrokerResources br = Globals.getBrokerResources();

    Socket socket = null;
    BrokerAddressImpl remote = null;
    MessageDigest digest = null;
    int timeout = 0;
    ExecutorService es = null;
    ClusterImpl parent = null;

    FileTransferRunnable(Socket conn, int timeout, BrokerAddressImpl remote, ExecutorService es, ClusterImpl parent) throws BrokerException {
        this.socket = conn;
        this.remote = remote;
        this.timeout = timeout;
        this.es = es;
        this.parent = parent;
        try {
            digest = MessageDigest.getInstance("SHA1");
        } catch (Exception e) {
            es.shutdownNow();
            String emsg = "";
            if (e instanceof NoSuchAlgorithmException) {
                emsg = "Unable to create MessageDigest for file transfer";
                logger.log(logger.ERROR, emsg + ": " + e);
            } else {
                emsg = "Unexpectd exception in creating MessageDigest for file transfer";
                logger.logStack(logger.ERROR, emsg, e);
            }
            throw new BrokerException(emsg, e);
        }
    }

    @Override
    public void run() {
        String from = remote + "[" + socket.getInetAddress() + "]";
        try {
            socket.setTcpNoDelay(true);
        } catch (Exception e) {
            logger.log(logger.WARNING, "Failed to set socket TCP_NODELAY in processing file transfer request: " + e);
        }
        try {
            socket.setSoTimeout(timeout);
        } catch (Exception e) {
            logger.log(logger.WARNING, "Failed to set socket timeout in processing file transfer request: " + e);
        }
        try {
            InputStream is = socket.getInputStream();
            GPacket gp = GPacket.getInstance();
            gp.read(is);
            if (gp.getType() != ProtocolGlobals.G_TRANSFER_FILE_LIST) {
                String[] emsg = { ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), from,
                        ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_TRANSFER_FILE_LIST) };
                logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM_1, emsg));
                try {
                    socket.close();
                } catch (Exception e) {
                    /* ignore */
                }
                return;
            }
            ClusterTransferFileListInfo tfl = ClusterTransferFileListInfo.newInstance(gp);
            String uuid = tfl.getUUID();
            String pendingUUID = parent.pendingFileTransfers.get(remote);
            if (pendingUUID == null || !pendingUUID.startsWith(tfl.getUUID())) {
                logger.log(logger.ERROR,
                        br.getKString(br.E_CLUSTER_RECEIVED_UNKNOW_FILE_TX_LIST, ProtocolGlobals.getPacketTypeDisplayString(gp.getType()) + tfl, remote));
                try {
                    socket.close();
                } catch (Exception e) {
                }
                return;
            }
            if (!tfl.getModule().equals(FileTransferCallback.STORE)) {
                logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM,
                        ProtocolGlobals.getPacketTypeDisplayString(gp.getType()) + tfl.toString(true), from));
                try {
                    socket.close();
                } catch (Exception e) {
                }
                return;
            }
            FileTransferCallback callback = (FileTransferCallback) Globals.getStore();

            int numFiles = tfl.getNumFiles();
            for (int cnt = 0; cnt < numFiles; cnt++) {

                if (parent.fileTransferShutdownIn) {
                    String emsg = br.getKString(br.W_CLUSTER_SERVICE_SHUTDOWN);
                    logger.log(logger.WARNING, emsg);
                    throw new BrokerException(emsg);
                }

                gp.read(is);
                if (gp.getType() != ProtocolGlobals.G_TRANSFER_FILE_START) {
                    String[] emsg = { ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), from,
                            ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_TRANSFER_FILE_START) };
                    logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM_1, emsg));
                    try {
                        socket.close();
                    } catch (Exception e) {
                    }
                    return;
                }
                ClusterTransferFileStartInfo tfs = ClusterTransferFileStartInfo.newInstance(gp);
                if (!tfs.getModule().equals(FileTransferCallback.STORE)) {
                    logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM,
                            ProtocolGlobals.getPacketTypeDisplayString(gp.getType()) + tfs.toString(true), from));
                    try {
                        socket.close();
                    } catch (Exception e) {
                    }
                    return;
                }
                if (!tfs.getBrokerID().equals(tfl.getBrokerID())) {
                    logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM,
                            ProtocolGlobals.getPacketTypeDisplayString(gp.getType()) + tfs.toString(true), from));
                    try {
                        socket.close();
                    } catch (Exception e) {
                    }
                    return;
                }
                String tmpfile = tfs.getFileName() + ".tmp";
                ClusterTransferFileEndInfo fte = null;
                boolean success = false;

                OutputStream os = callback.getFileOutputStream(tmpfile, tfs.getBrokerID(), uuid, cnt == 0, remote);
                try {
                    digest.reset();
                    long size = tfs.getFileSize();
                    byte[] buf = new byte[parent.FILE_TRANSFER_CHUNK_SIZE];
                    int totalread = 0;
                    int count;
                    while (totalread < size) {
                        if (parent.fileTransferShutdownIn) {
                            String emsg = br.getKString(br.W_CLUSTER_SERVICE_SHUTDOWN);
                            logger.log(logger.WARNING, emsg);
                            throw new BrokerException(emsg);
                        }
                        count = 0;
                        try {
                            count = is.read(buf, 0, (int) Math.min(parent.FILE_TRANSFER_CHUNK_SIZE, (size - totalread)));
                        } catch (IOException e) {
                            logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_FILE_TX_READ, from + ": " + e));
                            try {
                                socket.close();
                            } catch (Exception e1) {
                            }
                            return;
                        }
                        if (count < 0) {
                            String[] args = { String.valueOf(totalread), String.valueOf(size - totalread), from };
                            logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_FILE_TX_EOF, args));
                            try {
                                socket.close();
                            } catch (Exception e) {
                            }
                            return;
                        }
                        totalread += count;
                        os.write(buf, 0, count);
                        digest.update(buf, 0, count);
                    }
                    String[] args = { String.valueOf(size), tfs.getFileName(), from };
                    logger.log(logger.INFO, br.getKString(br.I_FILE_TX_COMPLETE, args));

                    gp = GPacket.getInstance();
                    gp.read(is);
                    if (gp.getType() != ProtocolGlobals.G_TRANSFER_FILE_END) {
                        String[] emsg = { ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), from,
                                ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_TRANSFER_FILE_END) };
                        logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM_1, emsg));
                        try {
                            socket.close();
                        } catch (Exception e) {
                        }
                        return;
                    }
                    fte = ClusterTransferFileEndInfo.newInstance(gp);
                    if (!Arrays.equals(digest.digest(), fte.getDigest())) {
                        logger.log(logger.ERROR, br.getKString(br.E_CLUSTER_FILE_TX_DIGEST_MISMATCH, tfs.getFileName(), from));
                        try {
                            socket.close();
                        } catch (Exception e) {
                        }
                        return;
                    }
                    os.close();
                    logger.log(logger.INFO, br.getKString(br.I_FILE_TX_SUCCESS, tfs.getFileName(), from));
                    callback.doneTransfer(tmpfile, tfs.getFileName(), tfs.getBrokerID(), tfs.getLastModifiedTime(), true, remote);
                    success = true;

                } finally {
                    if (!success) {// cleanup
                        try {
                            os.close();
                        } catch (Exception e) {
                        }
                        callback.doneTransfer(tmpfile, tfs.getFileName(), tfs.getBrokerID(), tfs.getLastModifiedTime(), false, remote);
                    }
                }

                if (fte.hasMoreFiles() != ((cnt + 1) < numFiles)) {
                    String[] args = { String.valueOf(numFiles), from, String.valueOf(cnt + 1), String.valueOf(fte.hasMoreFiles()) };
                    String emsg = br.getKString(br.E_CLUSTER_FILE_TX_NUMFILES, args);
                    logger.log(logger.ERROR, emsg);
                    throw new BrokerException(emsg);
                }

            } // for

            if (numFiles > 0) {
                callback.allDoneTransfer(tfl.getBrokerID(), tfl.getUUID(), remote);
                OutputStream sos = socket.getOutputStream();
                gp = ClusterTransferFileEndInfo.getReplyGPacket(Status.OK, (String) null);
                gp.write(sos);
                sos.flush();
                try {
                    gp = GPacket.getInstance();
                    gp.read(is);
                    if (gp.getType() != ProtocolGlobals.G_TRANSFER_FILE_END_ACK_ACK) {
                        String[] args = { ProtocolGlobals.getPacketTypeDisplayString(gp.getType()), from,
                                ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_TRANSFER_FILE_END_ACK_ACK) };
                        String emsg = br.getKString(br.E_CLUSTER_UNEXPECTED_PACKET_FROM_1, args);
                        logger.log(logger.ERROR, emsg);
                        throw new BrokerException(emsg);
                    }
                } catch (Throwable t) {
                    if (parent.DEBUG) {
                        logger.log(logger.INFO,
                                "Exception in receiving " + ProtocolGlobals.getPacketTypeDisplayString(ProtocolGlobals.G_TRANSFER_FILE_END_ACK_ACK) + " from "
                                        + from + " for tranfer files of broker " + tfl.getBrokerID() + ": " + t.getMessage());
                    }
                }
                try {
                    is.close();
                } catch (Exception e) {
                }
                try {
                    sos.close();
                } catch (Exception e) {
                }
                try {
                    socket.close();
                } catch (Exception e) {
                }
            }

        } catch (Throwable t) {
            try {
                socket.close();
            } catch (Exception e) {
            }
            logger.logStack(logger.ERROR, br.getKString(br.E_CLUSTER_PROCESS_FILE_TX_REQUEST, from), t);
        } finally {
            es.shutdownNow();
        }
    }
}

