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
 * @(#)SQLParserTokenManager.java	1.6 06/28/07
 */ 

package com.sun.messaging.jmq.jmsselector;

import java.io.*;
import java.util.*;

public class SQLParserTokenManager implements SQLParserConstants
{
  //public  java.io.PrintStream debugStream = System.out;
  //public  void setDebugStream(java.io.PrintStream ds) { debugStream = ds; }

   private final int jjStopStringLiteralDfa_0(int pos, long active0) {
   switch (pos)
   {
      case 0:
         if ((active0 & 0x7fc0L) != 0L)
         {
            jjmatchedKind = 20;
            return 37;
         }
         return -1;
      case 1:
         if ((active0 & 0x5cc0L) != 0L)
         {
            jjmatchedKind = 20;
            jjmatchedPos = 1;
            return 37;
         }
         if ((active0 & 0x2300L) != 0L)
            return 37;
         return -1;
      case 2:
         if ((active0 & 0x840L) != 0L)
            return 37;
         if ((active0 & 0x5480L) != 0L)
         {
            jjmatchedKind = 20;
            jjmatchedPos = 2;
            return 37;
         }
         return -1;
      case 3:
         if ((active0 & 0x1400L) != 0L)
            return 37;
         if ((active0 & 0x4080L) != 0L)
         {
            jjmatchedKind = 20;
            jjmatchedPos = 3;
            return 37;
         }
         return -1;
      case 4:
         if ((active0 & 0x4080L) != 0L)
         {
            jjmatchedKind = 20;
            jjmatchedPos = 4;
            return 37;
         }
         return -1;
      case 5:
         if ((active0 & 0x4000L) != 0L)
            return 37;
         if ((active0 & 0x80L) != 0L)
         {
            jjmatchedKind = 20;
            jjmatchedPos = 5;
            return 37;
         }
         return -1;
      default :
         return -1;
   }
}
private final int jjStartNfa_0(int pos, long active0)
{
   return jjMoveNfa_0(jjStopStringLiteralDfa_0(pos, active0), pos + 1);
}
private final int jjStopAtPos(int pos, int kind)
{
   jjmatchedKind = kind;
   jjmatchedPos = pos;
   return pos + 1;
}
private final int jjStartNfaWithStates_0(int pos, int kind, int state)
{
   jjmatchedKind = kind;
   jjmatchedPos = pos;
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) { return pos + 1; }
   return jjMoveNfa_0(state, pos + 1);
}
private final int jjMoveStringLiteralDfa0_0()
{
   switch(curChar)
   {
      case 40:
         return jjStopAtPos(0, 29);
      case 41:
         return jjStopAtPos(0, 30);
      case 42:
         return jjStopAtPos(0, 31);
      case 43:
         return jjStopAtPos(0, 33);
      case 44:
         return jjStopAtPos(0, 36);
      case 45:
         return jjStopAtPos(0, 34);
      case 47:
         return jjStopAtPos(0, 32);
      case 60:
         jjmatchedKind = 23;
         return jjMoveStringLiteralDfa1_0(0x11000000L);
      case 61:
         return jjStopAtPos(0, 27);
      case 62:
         jjmatchedKind = 25;
         return jjMoveStringLiteralDfa1_0(0x4000000L);
      case 63:
         return jjStopAtPos(0, 35);
      case 65:
      case 97:
         return jjMoveStringLiteralDfa1_0(0x40L);
      case 66:
      case 98:
         return jjMoveStringLiteralDfa1_0(0x80L);
      case 69:
      case 101:
         return jjMoveStringLiteralDfa1_0(0x4000L);
      case 73:
      case 105:
         return jjMoveStringLiteralDfa1_0(0x300L);
      case 76:
      case 108:
         return jjMoveStringLiteralDfa1_0(0x400L);
      case 78:
      case 110:
         return jjMoveStringLiteralDfa1_0(0x1800L);
      case 79:
      case 111:
         return jjMoveStringLiteralDfa1_0(0x2000L);
      default :
         return jjMoveNfa_0(0, 0);
   }
}
private final int jjMoveStringLiteralDfa1_0(long active0)
{
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(0, active0);
      return 1;
   }
   switch(curChar)
   {
      case 61:
         if ((active0 & 0x1000000L) != 0L)
            return jjStopAtPos(1, 24);
         else if ((active0 & 0x4000000L) != 0L)
            return jjStopAtPos(1, 26);
         break;
      case 62:
         if ((active0 & 0x10000000L) != 0L)
            return jjStopAtPos(1, 28);
         break;
      case 69:
      case 101:
         return jjMoveStringLiteralDfa2_0(active0, 0x80L);
      case 73:
      case 105:
         return jjMoveStringLiteralDfa2_0(active0, 0x400L);
      case 78:
      case 110:
         if ((active0 & 0x100L) != 0L)
            return jjStartNfaWithStates_0(1, 8, 37);
         return jjMoveStringLiteralDfa2_0(active0, 0x40L);
      case 79:
      case 111:
         return jjMoveStringLiteralDfa2_0(active0, 0x800L);
      case 82:
      case 114:
         if ((active0 & 0x2000L) != 0L)
            return jjStartNfaWithStates_0(1, 13, 37);
         break;
      case 83:
      case 115:
         if ((active0 & 0x200L) != 0L)
            return jjStartNfaWithStates_0(1, 9, 37);
         return jjMoveStringLiteralDfa2_0(active0, 0x4000L);
      case 85:
      case 117:
         return jjMoveStringLiteralDfa2_0(active0, 0x1000L);
      default :
         break;
   }
   return jjStartNfa_0(0, active0);
}
private final int jjMoveStringLiteralDfa2_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(0, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(1, active0);
      return 2;
   }
   switch(curChar)
   {
      case 67:
      case 99:
         return jjMoveStringLiteralDfa3_0(active0, 0x4000L);
      case 68:
      case 100:
         if ((active0 & 0x40L) != 0L)
            return jjStartNfaWithStates_0(2, 6, 37);
         break;
      case 75:
      case 107:
         return jjMoveStringLiteralDfa3_0(active0, 0x400L);
      case 76:
      case 108:
         return jjMoveStringLiteralDfa3_0(active0, 0x1000L);
      case 84:
      case 116:
         if ((active0 & 0x800L) != 0L)
            return jjStartNfaWithStates_0(2, 11, 37);
         return jjMoveStringLiteralDfa3_0(active0, 0x80L);
      default :
         break;
   }
   return jjStartNfa_0(1, active0);
}
private final int jjMoveStringLiteralDfa3_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(1, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(2, active0);
      return 3;
   }
   switch(curChar)
   {
      case 65:
      case 97:
         return jjMoveStringLiteralDfa4_0(active0, 0x4000L);
      case 69:
      case 101:
         if ((active0 & 0x400L) != 0L)
            return jjStartNfaWithStates_0(3, 10, 37);
         break;
      case 76:
      case 108:
         if ((active0 & 0x1000L) != 0L)
            return jjStartNfaWithStates_0(3, 12, 37);
         break;
      case 87:
      case 119:
         return jjMoveStringLiteralDfa4_0(active0, 0x80L);
      default :
         break;
   }
   return jjStartNfa_0(2, active0);
}
private final int jjMoveStringLiteralDfa4_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(2, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(3, active0);
      return 4;
   }
   switch(curChar)
   {
      case 69:
      case 101:
         return jjMoveStringLiteralDfa5_0(active0, 0x80L);
      case 80:
      case 112:
         return jjMoveStringLiteralDfa5_0(active0, 0x4000L);
      default :
         break;
   }
   return jjStartNfa_0(3, active0);
}
private final int jjMoveStringLiteralDfa5_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(3, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(4, active0);
      return 5;
   }
   switch(curChar)
   {
      case 69:
      case 101:
         if ((active0 & 0x4000L) != 0L)
            return jjStartNfaWithStates_0(5, 14, 37);
         return jjMoveStringLiteralDfa6_0(active0, 0x80L);
      default :
         break;
   }
   return jjStartNfa_0(4, active0);
}
private final int jjMoveStringLiteralDfa6_0(long old0, long active0)
{
   if (((active0 &= old0)) == 0L)
      return jjStartNfa_0(4, old0); 
   try { curChar = input_stream.readChar(); }
   catch(java.io.IOException e) {
      jjStopStringLiteralDfa_0(5, active0);
      return 6;
   }
   switch(curChar)
   {
      case 78:
      case 110:
         if ((active0 & 0x80L) != 0L)
            return jjStartNfaWithStates_0(6, 7, 37);
         break;
      default :
         break;
   }
   return jjStartNfa_0(5, active0);
}
private final void jjCheckNAdd(int state)
{
   if (jjrounds[state] != jjround)
   {
      jjstateSet[jjnewStateCnt++] = state;
      jjrounds[state] = jjround;
   }
}
private final void jjAddStates(int start, int end)
{
   do {
      jjstateSet[jjnewStateCnt++] = jjnextStates[start];
   } while (start++ != end);
}
private final void jjCheckNAddTwoStates(int state1, int state2)
{
   jjCheckNAdd(state1);
   jjCheckNAdd(state2);
}
private final void jjCheckNAddStates(int start, int end)
{
   do {
      jjCheckNAdd(jjnextStates[start]);
   } while (start++ != end);
}
/*
private final void jjCheckNAddStates(int start)
{
   jjCheckNAdd(jjnextStates[start]);
   jjCheckNAdd(jjnextStates[start + 1]);
}
*/
static final long[] jjbitVec0 = {
   0xfffffffffffffffeL, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL
};
static final long[] jjbitVec2 = {
   0x0L, 0x0L, 0xffffffffffffffffL, 0xffffffffffffffffL
};
private final int jjMoveNfa_0(int startState, int curPos)
{
   int[] nextStates;
   int startsAt = 0;
   jjnewStateCnt = 37;
   int i = 1;
   jjstateSet[0] = startState;
   int j, kind = 0x7fffffff;
   for (;;)
   {
      if (++jjround == 0x7fffffff)
         reInitRounds();
      if (curChar < 64)
      {
         long l = 1L << curChar;
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 37:
                  if ((0x3ff001800000000L & l) != 0L)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAdd(23);
                  }
                  if (curChar == 36)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAddTwoStates(22, 23);
                  }
                  break;
               case 0:
                  if ((0x3ff000000000000L & l) != 0L)
                  {
                     if (kind > 15)
                        kind = 15;
                     jjCheckNAddStates(0, 6);
                  }
                  else if (curChar == 36)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAddTwoStates(22, 23);
                  }
                  else if (curChar == 39)
                     jjCheckNAddStates(7, 9);
                  else if (curChar == 46)
                     jjCheckNAdd(4);
                  if (curChar == 48)
                     jjstateSet[jjnewStateCnt++] = 1;
                  break;
               case 2:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 15)
                     kind = 15;
                  jjstateSet[jjnewStateCnt++] = 2;
                  break;
               case 3:
                  if (curChar == 46)
                     jjCheckNAdd(4);
                  break;
               case 4:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAddTwoStates(4, 5);
                  break;
               case 6:
                  if ((0x280000000000L & l) != 0L)
                     jjCheckNAdd(7);
                  break;
               case 7:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAdd(7);
                  break;
               case 8:
                  if (curChar == 39)
                     jjCheckNAddStates(7, 9);
                  break;
               case 9:
                  if ((0xffffff7fffffffffL & l) != 0L)
                     jjCheckNAddStates(7, 9);
                  break;
               case 10:
                  if (curChar == 39)
                     jjCheckNAddStates(10, 12);
                  break;
               case 11:
                  if (curChar == 39)
                     jjstateSet[jjnewStateCnt++] = 10;
                  break;
               case 12:
                  if ((0xffffff7fffffffffL & l) != 0L)
                     jjCheckNAddStates(10, 12);
                  break;
               case 13:
                  if (curChar == 39 && kind > 18)
                     kind = 18;
                  break;
               case 22:
                  if (curChar != 36)
                     break;
                  if (kind > 20)
                     kind = 20;
                  jjCheckNAddTwoStates(22, 23);
                  break;
               case 23:
                  if ((0x3ff001800000000L & l) == 0L)
                     break;
                  if (kind > 20)
                     kind = 20;
                  jjCheckNAdd(23);
                  break;
               case 24:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 15)
                     kind = 15;
                  jjCheckNAddStates(0, 6);
                  break;
               case 25:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 15)
                     kind = 15;
                  jjCheckNAdd(25);
                  break;
               case 26:
                  if ((0x3ff000000000000L & l) != 0L)
                     jjCheckNAddTwoStates(26, 3);
                  break;
               case 27:
                  if ((0x3ff000000000000L & l) != 0L)
                     jjCheckNAddTwoStates(27, 28);
                  break;
               case 28:
                  if (curChar != 46)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAddTwoStates(29, 30);
                  break;
               case 29:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAddTwoStates(29, 30);
                  break;
               case 31:
                  if ((0x280000000000L & l) != 0L)
                     jjCheckNAdd(32);
                  break;
               case 32:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAdd(32);
                  break;
               case 33:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAddTwoStates(33, 34);
                  break;
               case 35:
                  if ((0x280000000000L & l) != 0L)
                     jjCheckNAdd(36);
                  break;
               case 36:
                  if ((0x3ff000000000000L & l) == 0L)
                     break;
                  if (kind > 16)
                     kind = 16;
                  jjCheckNAdd(36);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else if (curChar < 128)
      {
         long l = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 37:
                  if ((0x7fffffe87fffffeL & l) != 0L)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAdd(23);
                  }
                  if ((0x7fffffe87fffffeL & l) != 0L)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAddTwoStates(22, 23);
                  }
                  break;
               case 0:
                  if ((0x7fffffe87fffffeL & l) != 0L)
                  {
                     if (kind > 20)
                        kind = 20;
                     jjCheckNAddTwoStates(22, 23);
                  }
                  if ((0x4000000040L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 20;
                  else if ((0x10000000100000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 16;
                  break;
               case 1:
                  if ((0x100000001000000L & l) != 0L)
                     jjCheckNAdd(2);
                  break;
               case 2:
                  if ((0x3e0000003eL & l) == 0L)
                     break;
                  if (kind > 15)
                     kind = 15;
                  jjCheckNAdd(2);
                  break;
               case 5:
                  if ((0x2000000020L & l) != 0L)
                     jjAddStates(13, 14);
                  break;
               case 9:
                  jjCheckNAddStates(7, 9);
                  break;
               case 12:
                  jjCheckNAddStates(10, 12);
                  break;
               case 14:
                  if ((0x2000000020L & l) != 0L && kind > 19)
                     kind = 19;
                  break;
               case 15:
                  if ((0x20000000200000L & l) != 0L)
                     jjCheckNAdd(14);
                  break;
               case 16:
                  if ((0x4000000040000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 15;
                  break;
               case 17:
                  if ((0x10000000100000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 16;
                  break;
               case 18:
                  if ((0x8000000080000L & l) != 0L)
                     jjCheckNAdd(14);
                  break;
               case 19:
                  if ((0x100000001000L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 18;
                  break;
               case 20:
                  if ((0x200000002L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 19;
                  break;
               case 21:
                  if ((0x4000000040L & l) != 0L)
                     jjstateSet[jjnewStateCnt++] = 20;
                  break;
               case 22:
                  if ((0x7fffffe87fffffeL & l) == 0L)
                     break;
                  if (kind > 20)
                     kind = 20;
                  jjCheckNAddTwoStates(22, 23);
                  break;
               case 23:
                  if ((0x7fffffe87fffffeL & l) == 0L)
                     break;
                  if (kind > 20)
                     kind = 20;
                  jjCheckNAdd(23);
                  break;
               case 30:
                  if ((0x2000000020L & l) != 0L)
                     jjAddStates(15, 16);
                  break;
               case 34:
                  if ((0x2000000020L & l) != 0L)
                     jjAddStates(17, 18);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      else
      {
         int hiByte = (int)(curChar >> 8);
         int i1 = hiByte >> 6;
         long l1 = 1L << (hiByte & 077);
         int i2 = (curChar & 0xff) >> 6;
         long l2 = 1L << (curChar & 077);
         MatchLoop: do
         {
            switch(jjstateSet[--i])
            {
               case 9:
                  if (jjCanMove_0(hiByte, i1, i2, l1, l2))
                     jjCheckNAddStates(7, 9);
                  break;
               case 12:
                  if (jjCanMove_0(hiByte, i1, i2, l1, l2))
                     jjCheckNAddStates(10, 12);
                  break;
               default : break;
            }
         } while(i != startsAt);
      }
      if (kind != 0x7fffffff)
      {
         jjmatchedKind = kind;
         jjmatchedPos = curPos;
         kind = 0x7fffffff;
      }
      ++curPos;
      if ((i = jjnewStateCnt) == (startsAt = 37 - (jjnewStateCnt = startsAt)))
         return curPos;
      try { curChar = input_stream.readChar(); }
      catch(java.io.IOException e) { return curPos; }
   }
}
static final int[] jjnextStates = {
   25, 26, 3, 27, 28, 33, 34, 9, 11, 13, 11, 12, 13, 6, 7, 31, 
   32, 35, 36, 
};
private static final boolean jjCanMove_0(int hiByte, int i1, int i2, long l1, long l2)
{
   switch(hiByte)
   {
      case 0:
         return ((jjbitVec2[i2] & l2) != 0L);
      default : 
         if ((jjbitVec0[i1] & l1) != 0L)
            return true;
         return false;
   }
}
private static final String[] jjstrLiteralImages = {
"", null, null, null, null, null, null, null, null, null, null, null, null, 
null, null, null, null, null, null, null, null, null, null, "\74", "\74\75", "\76", 
"\76\75", "\75", "\74\76", "\50", "\51", "\52", "\57", "\53", "\55", "\77", "\54", };
private static final String[] lexStateNames = {
   "DEFAULT", 
};
static final long[] jjtoToken = {
   0x1fff9dffc1L, 
};
static final long[] jjtoSkip = {
   0x3eL, 
};
private JavaCharStream input_stream;
private final int[] jjrounds = new int[37];
private final int[] jjstateSet = new int[74];
protected char curChar;
public SQLParserTokenManager(JavaCharStream stream)
{
   if (JavaCharStream.staticFlag)
      throw new Error("ERROR: Cannot use a static CharStream class with a non-static lexical analyzer.");
   input_stream = stream;
}
public SQLParserTokenManager(JavaCharStream stream, int lexState)
{
   this(stream);
   switchTo(lexState);
}
public void reInit(JavaCharStream stream)
{
   jjmatchedPos = jjnewStateCnt = 0;
   curLexState = defaultLexState;
   input_stream = stream;
   reInitRounds();
}
private final void reInitRounds()
{
   int i;
   jjround = 0x80000001;
   for (i = 37; i-- > 0;)
      jjrounds[i] = 0x80000000;
}
public void reInit(JavaCharStream stream, int lexState)
{
   reInit(stream);
   switchTo(lexState);
}
public void switchTo(int lexState)
{
   if (lexState >= 1 || lexState < 0)
      throw new TokenMgrError("Error: Ignoring invalid lexical state : " + lexState + ". State unchanged.", TokenMgrError.INVALID_LEXICAL_STATE);
   else
      curLexState = lexState;
}

private final Token jjFillToken()
{
   Token t = Token.newToken(jjmatchedKind);
   t.kind = jjmatchedKind;
   String im = jjstrLiteralImages[jjmatchedKind];
   t.image = (im == null) ? input_stream.getImage() : im;
   t.beginLine = input_stream.getBeginLine();
   t.beginColumn = input_stream.getBeginColumn();
   //t.endLine = input_stream.getEndLine();
   //t.endColumn = input_stream.getEndColumn();
   return t;
}

int curLexState = 0;
int defaultLexState = 0;
int jjnewStateCnt;
int jjround;
int jjmatchedPos;
int jjmatchedKind;

public final Token getNextToken() 
{
  int kind;
  Token specialToken = null;
  Token matchedToken;
  int curPos = 0;

  EOFLoop :
  for (;;)
  {   
   try   
   {     
      curChar = input_stream.beginToken();
   }     
   catch(java.io.IOException e)
   {        
      jjmatchedKind = 0;
      matchedToken = jjFillToken();
      return matchedToken;
   }

   try { input_stream.backup(0);
      while (curChar <= 32 && (0x100003600L & (1L << curChar)) != 0L)
         curChar = input_stream.beginToken();
   }
   catch (java.io.IOException e1) { continue EOFLoop; }
   jjmatchedKind = 0x7fffffff;
   jjmatchedPos = 0;
   curPos = jjMoveStringLiteralDfa0_0();
   if (jjmatchedKind != 0x7fffffff)
   {
      if (jjmatchedPos + 1 < curPos)
         input_stream.backup(curPos - jjmatchedPos - 1);
      if ((jjtoToken[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)
      {
         matchedToken = jjFillToken();
         return matchedToken;
      }
      else
      {
         continue EOFLoop;
      }
   }
   int error_line = input_stream.getEndLine();
   int error_column = input_stream.getEndColumn();
   String error_after = null;
   boolean EOFSeen = false;
   try { input_stream.readChar(); input_stream.backup(1); }
   catch (java.io.IOException e1) {
      EOFSeen = true;
      error_after = curPos <= 1 ? "" : input_stream.getImage();
      if (curChar == '\n' || curChar == '\r') {
         error_line++;
         error_column = 0;
      }
      else
         error_column++;
   }
   if (!EOFSeen) {
      input_stream.backup(1);
      error_after = curPos <= 1 ? "" : input_stream.getImage();
   }
   throw new TokenMgrError(EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenMgrError.LEXICAL_ERROR);
  }
}

}
