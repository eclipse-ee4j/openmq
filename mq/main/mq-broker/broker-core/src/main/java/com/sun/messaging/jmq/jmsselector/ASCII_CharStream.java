/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
 * @(#)ASCII_CharStream.java	1.4 06/28/07
 */

package com.sun.messaging.jmq.jmsselector;

/**
 * An implementation of interface CharStream, where the stream is assumed to contain only ASCII characters (without
 * unicode processing).
 */

public final class ASCII_CharStream {
    public static final boolean staticFlag = false;
    int bufsize;
    int available;
    int tokenBegin;
    public int bufpos = -1;
    private int bufline[];
    private int bufcolumn[];

    private int column = 0;
    private int line = 1;

    private boolean prevCharIsCR = false;
    private boolean prevCharIsLF = false;

    private java.io.Reader inputStream;

    private char[] buffer;
    private int maxNextCharInd = 0;
    private int inBuf = 0;

    private void expandBuff(boolean wrapAround) {
        char[] newbuffer = new char[bufsize + 2048];
        int newbufline[] = new int[bufsize + 2048];
        int newbufcolumn[] = new int[bufsize + 2048];

        try {
            if (wrapAround) {
                System.arraycopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
                System.arraycopy(buffer, 0, newbuffer, bufsize - tokenBegin, bufpos);
                buffer = newbuffer;

                System.arraycopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
                System.arraycopy(bufline, 0, newbufline, bufsize - tokenBegin, bufpos);
                bufline = newbufline;

                System.arraycopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
                System.arraycopy(bufcolumn, 0, newbufcolumn, bufsize - tokenBegin, bufpos);
                bufcolumn = newbufcolumn;

                maxNextCharInd = (bufpos += (bufsize - tokenBegin));
            } else {
                System.arraycopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
                buffer = newbuffer;

                System.arraycopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
                bufline = newbufline;

                System.arraycopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
                bufcolumn = newbufcolumn;

                maxNextCharInd = (bufpos -= tokenBegin);
            }
        } catch (Throwable t) {
            throw new Error(t.getMessage());
        }

        bufsize += 2048;
        available = bufsize;
        tokenBegin = 0;
    }

    private void fillBuff() throws java.io.IOException {
        if (maxNextCharInd == available) {
            if (available == bufsize) {
                if (tokenBegin > 2048) {
                    bufpos = maxNextCharInd = 0;
                    available = tokenBegin;
                } else if (tokenBegin < 0) {
                    bufpos = maxNextCharInd = 0;
                } else {
                    expandBuff(false);
                }
            } else if (available > tokenBegin) {
                available = bufsize;
            } else if ((tokenBegin - available) < 2048) {
                expandBuff(true);
            } else {
                available = tokenBegin;
            }
        }

        int i;
        try {
            if ((i = inputStream.read(buffer, maxNextCharInd, available - maxNextCharInd)) == -1) {
                inputStream.close();
                throw new java.io.IOException();
            } else {
                maxNextCharInd += i;
            }
            return;
        } catch (java.io.IOException e) {
            --bufpos;
            backup(0);
            if (tokenBegin == -1) {
                tokenBegin = bufpos;
            }
            throw e;
        }
    }

    public char beginToken() throws java.io.IOException {
        tokenBegin = -1;
        char c = readChar();
        tokenBegin = bufpos;

        return c;
    }

    private void updateLineColumn(char c) {
        column++;

        if (prevCharIsLF) {
            prevCharIsLF = false;
            line += (column = 1);
        } else if (prevCharIsCR) {
            prevCharIsCR = false;
            if (c == '\n') {
                prevCharIsLF = true;
            } else {
                line += (column = 1);
            }
        }

        switch (c) {
        case '\r':
            prevCharIsCR = true;
            break;
        case '\n':
            prevCharIsLF = true;
            break;
        case '\t':
            column--;
            column += (8 - (column & 07));
            break;
        default:
            break;
        }

        bufline[bufpos] = line;
        bufcolumn[bufpos] = column;
    }

    public char readChar() throws java.io.IOException {
        if (inBuf > 0) {
            --inBuf;
            return (char) ((char) 0xff & buffer[(bufpos == bufsize - 1) ? (bufpos = 0) : ++bufpos]);
        }

        if (++bufpos >= maxNextCharInd) {
            fillBuff();
        }

        char c = (char) ((char) 0xff & buffer[bufpos]);

        updateLineColumn(c);
        return (c);
    }

    public int getEndColumn() {
        return bufcolumn[bufpos];
    }

    public int getEndLine() {
        return bufline[bufpos];
    }

    public int getBeginColumn() {
        return bufcolumn[tokenBegin];
    }

    public int getBeginLine() {
        return bufline[tokenBegin];
    }

    public void backup(int amount) {

        inBuf += amount;
        if ((bufpos -= amount) < 0) {
            bufpos += bufsize;
        }
    }

    public ASCII_CharStream(java.io.Reader dstream, int startline, int startcolumn, int buffersize) {
        inputStream = dstream;
        line = startline;
        column = startcolumn - 1;

        available = bufsize = buffersize;
        buffer = new char[buffersize];
        bufline = new int[buffersize];
        bufcolumn = new int[buffersize];
    }

    public ASCII_CharStream(java.io.Reader dstream, int startline, int startcolumn) {
        this(dstream, startline, startcolumn, 4096);
    }

    public void reInit(java.io.Reader dstream, int startline, int startcolumn, int buffersize) {
        inputStream = dstream;
        line = startline;
        column = startcolumn - 1;

        if (buffer == null || buffersize != buffer.length) {
            available = bufsize = buffersize;
            buffer = new char[buffersize];
            bufline = new int[buffersize];
            bufcolumn = new int[buffersize];
        }
        prevCharIsLF = prevCharIsCR = false;
        tokenBegin = inBuf = maxNextCharInd = 0;
        bufpos = -1;
    }

    public void reInit(java.io.Reader dstream, int startline, int startcolumn) {
        reInit(dstream, startline, startcolumn, 4096);
    }

    public ASCII_CharStream(java.io.InputStream dstream, int startline, int startcolumn, int buffersize) {
        this(new java.io.InputStreamReader(dstream), startline, startcolumn, buffersize);
    }

    public ASCII_CharStream(java.io.InputStream dstream, int startline, int startcolumn) {
        this(dstream, startline, startcolumn, 4096);
    }

    public void reInit(java.io.InputStream dstream, int startline, int startcolumn, int buffersize) {
        reInit(new java.io.InputStreamReader(dstream), startline, startcolumn, 4096);
    }

    public void reInit(java.io.InputStream dstream, int startline, int startcolumn) {
        reInit(dstream, startline, startcolumn, 4096);
    }

    public String getImage() {
        if (bufpos >= tokenBegin) {
            return new String(buffer, tokenBegin, bufpos - tokenBegin + 1);
        } else {
            return new String(buffer, tokenBegin, bufsize - tokenBegin) + new String(buffer, 0, bufpos + 1);
        }
    }

    public char[] getSuffix(int len) {
        char[] ret = new char[len];

        if ((bufpos + 1) >= len) {
            System.arraycopy(buffer, bufpos - len + 1, ret, 0, len);
        } else {
            System.arraycopy(buffer, bufsize - (len - bufpos - 1), ret, 0, len - bufpos - 1);
            System.arraycopy(buffer, 0, ret, len - bufpos - 1, bufpos + 1);
        }

        return ret;
    }

    public void done() {
        buffer = null;
        bufline = null;
        bufcolumn = null;
    }

    /**
     * Method to adjust line and column numbers for the start of a token.<BR>
     */
    public void adjustBeginLineColumn(int newLine, int newCol) {
        int start = tokenBegin;
        int len;

        if (bufpos >= tokenBegin) {
            len = bufpos - tokenBegin + inBuf + 1;
        } else {
            len = bufsize - tokenBegin + bufpos + 1 + inBuf;
        }

        int i = 0, j = 0, k = 0;
        int nextColDiff = 0, columnDiff = 0;

        while (i < len && bufline[j = start % bufsize] == bufline[k = ++start % bufsize]) {
            bufline[j] = newLine;
            nextColDiff = columnDiff + bufcolumn[k] - bufcolumn[j];
            bufcolumn[j] = newCol + columnDiff;
            columnDiff = nextColDiff;
            i++;
        }

        if (i < len) {
            bufline[j] = newLine++;
            bufcolumn[j] = newCol + columnDiff;

            while (i++ < len) {
                if (bufline[j = start % bufsize] != bufline[++start % bufsize]) {
                    bufline[j] = newLine++;
                } else {
                    bufline[j] = newLine;
                }
            }
        }

        line = bufline[j];
        column = bufcolumn[j];
    }

}
