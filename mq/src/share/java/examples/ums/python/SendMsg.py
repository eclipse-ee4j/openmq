#!/usr/bin/python
#
# Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
# Copyright (c) 2021 Contributors to the Eclipse Foundation
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0, which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause
#

import sys, getopt, httplib, urllib

DEFAULT_CONTEXT_ROOT = "/ums"

host = ""
dst = "simpleQ"
msg = "Hello, Python World!"
domain = "queue"
user = "guest"
pwd = "guest"
count = 1

# Function to print usage
def usage():
   print
   print "usage: python SendMsg.py [options]"
   print
   print "where options include:"
   print "  -h               Usage"
   print "  -s <host:port>   Specify the UMS server host and port."
   print "  -d <name>        Specify the destination name. Default is simpleQ."
   print "  -m \"<message>\"   Specify the msg to sent."
   print "  -n <count>       Specify the number of message to send."
   print "  -q               Specify the domain is a queue. Default is queue."
   print "  -t               Specify the domain is a topic."
   print "  -u <user>        Specify the user name. Default is guest."
   print "  -p <password>    Specify the password. Default is guest."
   print
   sys.exit(1)

# Function to parse command line arguments
def parseArgs():
   global dst, host, domain, msg, user, pwd, count

   try:
      opts, args = getopt.getopt(sys.argv[1:], 'hqtd:m:n:s:u:p:')
   except getopt.GetoptError:
      print "Error: parsing command line arguments"
      usage()

   for opt in opts:
      if opt[0] == '-h':
         usage()
      if opt[0] == '-d':
         dst = opt[1]
      if opt[0] == '-s':
         host = opt[1]
      if opt[0] == '-q':
         domain = "queue"
      if opt[0] == '-t':
         domain = "topic"
      if opt[0] == '-m':
         msg = opt[1]
      if opt[0] == '-u':
         user = opt[1]
      if opt[0] == '-p':
         pwd = opt[1]
      if opt[0] == '-n':
         count = int(opt[1])

   if len(host) == 0:
      print "Please specify the UMS server host and port!"
      usage()

# Function to post request
#    Returns 0 if successfull and server's responsed data
def doPost(conn, url, body):
   rtnCode = 0
   respData = ""
   headers = {
      "Content-type": "text/plain;charset=UTF-8",
      "Accept": "text/plain"
   }

   try:
      conn.request("POST", url, body, headers)
      resp = conn.getresponse()

      # Get the response
      if resp.status == 200:
         respData = resp.read()
      else:
         print "Failed to post data to http://" + host + url
         print "Response: ", resp.status, resp.reason
         rtnCode = -1
   except KeyboardInterrupt:
      rtnCode = -1
   except Exception, e:
      print "Error: ", e.__class__, "Cannot post data to http://" + host + url
      rtnCode = -1

   return rtnCode, respData

# Main program
def main():

   # Process command line args
   parseArgs()

   print "UMS Server:", host + ", Destination:", dst + ", Domain:", domain
   print

   # Open a connection to the server
   conn = httplib.HTTPConnection(host)

   # Login to UMS
   url = DEFAULT_CONTEXT_ROOT + "/simple?service=login" + \
      "&user=" + urllib.quote(user) + "&password=" + urllib.quote(pwd);
   (rtnCode, sid) = doPost(conn, url, "")
   if rtnCode == -1:
      print "Failed to login to UMS server."
      sys.exit(1)

   # Send message(s)
   url = DEFAULT_CONTEXT_ROOT + "/simple?service=send" + \
      "&destination=" + dst + \
      "&domain=" + domain + \
      "&sid=" + sid
 
   i = 0
   while i < count:
      if count > 1:
         textMsg = "(msg#" + str(i) + ") " + msg
      else:
         textMsg = msg

      # Send request
      (rtnCode, respMsg) = doPost(conn, url, textMsg)
      if rtnCode == -1:
         break

      print "Send msg:", textMsg
      i += 1

   # Close the UMS session
   #    Connection could be bad due to CTRL-C so create a new one
   print
   print "Closing UMS connection, please wait..."

   conn.close()
   conn = httplib.HTTPConnection(host)
   url = DEFAULT_CONTEXT_ROOT + "/simple?service=close&sid=" + sid
   doPost(conn, url, "")
   conn.close()

if __name__ == '__main__':
   main()
