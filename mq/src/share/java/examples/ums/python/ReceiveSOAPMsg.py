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

import sys, os, getopt, httplib, urllib, datetime, time, base64
import xml.etree.ElementTree as etree

DEFAULT_CONTEXT_ROOT = "/ums"
DEFAULT_TIMEOUT = "15000"

# Namespaces used
NS_SOAP_ENV = "{http://schemas.xmlsoap.org/soap/envelope/}"
NS_SOAP_ENC = "{http://schemas.xmlsoap.org/soap/encoding/}"
NS_MQ_SERVICE = "{https://mq.java.net/ums}"

# Define tags & attrs
SOAP_ENVELOPE = NS_SOAP_ENV + "Envelope"
SOAP_HEADER = NS_SOAP_ENV + "Header"
SOAP_BODY = NS_SOAP_ENV + "Body"

MQ_MESSAGEHEADER = NS_MQ_SERVICE + "MessageHeader"
MQ_SERVICE = NS_MQ_SERVICE + "Service"

MQ_ID_ATTR = NS_MQ_SERVICE + "id"
MQ_VERSION_ATTR = NS_MQ_SERVICE + "version"
MQ_SERVICE_ATTR = NS_MQ_SERVICE + "service"
MQ_CLIENTID_ATTR = NS_MQ_SERVICE + "clientId"
MQ_DESTINATION_ATTR = NS_MQ_SERVICE + "destination"
MQ_DOMAIN_ATTR = NS_MQ_SERVICE + "domain"
MQ_SID_ATTR = NS_MQ_SERVICE + "sid"
MQ_USER_ATTR = NS_MQ_SERVICE + "user"
MQ_PASSWORD_ATTR = NS_MQ_SERVICE + "password"
MQ_TIMEOUT_ATTR = NS_MQ_SERVICE + "timeout"
MQ_STATUS_ATTR = NS_MQ_SERVICE + "status"

host = ""
svc = "receive"
dst = "simpleSoapQ"
domain = "queue"
user = "guest"
pwd = "guest"

# Function to print usage
def usage():
   print
   print "usage: python ReceiveSOAPMsg.py [options]"
   print
   print "where options include:"
   print "  -h               Usage"
   print "  -s <host:port>   Specify the server host and port."
   print "  -d <name>        Specify the destination name. Default is simpleSoapQ."
   print "  -q               Specify domain is a queue. Default is queue."
   print "  -t               Specify domain is a topic."
   print "  -u <user>        Specify the user name. Default is guest."
   print "  -p <password>    Specify the password. Default is guest."
   print
   sys.exit(1)

# Function to parse command line arguments
def parseArgs():
   global dst, host, domain, user, pwd

   try:
      opts, args = getopt.getopt(sys.argv[1:], 'hqtd:s:u:p:')
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
      if opt[0] == '-u':
         user = opt[1]
      if opt[0] == '-p':
         pwd = opt[1]

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

# Function to send SOAP msg
#    Returns 0 if successfull and server's responsed data
def doSend(url, envelope):
   rtnCode = 0
   respData = ""
   soapMsg = etree.tostring(envelope)

   # Construct HTTP request and send the SOAP msg
   try:
      webservice = httplib.HTTP(host)
      webservice.putrequest("POST", url)
      webservice.putheader("Host", host)
      webservice.putheader("User-Agent", "Python post")
      webservice.putheader("Content-type", "text/xml; charset=\"UTF-8\"")
      webservice.putheader("Content-length", "%d" % len(soapMsg))
      webservice.endheaders()
      webservice.send(soapMsg)

      # Get the response
      replyCode, replyMessage, replyHeader = webservice.getreply()
      if replyCode == 200:
         respData = webservice.getfile().read()
      elif replyCode == 404:
         #print "No message available"
         respData = ""
      else:
         print "Failed to post data to http://" + host + url
         print "Response: ", replyCode, replyMessage
         print "Headers: ", replyHeader
         rtnCode = -1
   except KeyboardInterrupt:
      rtnCode = -1
   except Exception, e:
      print "Error: ", e.__class__, "Cannot post data to http://" + host + url
      rtnCode = -1

   return rtnCode, respData

# Build SOAP envelope
def buildSOAPEnvelope(sid):
   envelope = etree.Element(SOAP_ENVELOPE)
   header = etree.SubElement(envelope, SOAP_HEADER)

   msgHeader = etree.SubElement(header, MQ_MESSAGEHEADER)
   msgHeader.set(MQ_ID_ATTR, "1.0")
   msgHeader.set(MQ_VERSION_ATTR, "1.1")

   service = etree.SubElement(msgHeader, MQ_SERVICE)
   service.set(MQ_SERVICE_ATTR, svc)
   service.set(MQ_DESTINATION_ATTR, dst)
   service.set(MQ_DOMAIN_ATTR, domain)
   service.set(MQ_TIMEOUT_ATTR, DEFAULT_TIMEOUT)

   # Use session ID if available
   if len(sid) > 0:
      service.set(MQ_SID_ATTR, sid)
   else:
      service.set(MQ_USER_ATTR, user)
      service.set(MQ_PASSWORD_ATTR, pwd)

   body = etree.SubElement(envelope, SOAP_BODY)

   return envelope, body

# Main program
def main():

   # Process command line args
   parseArgs()

   print "UMS Server:", host + ", Destination:", dst + ", Domain:", domain
   print

   # Login to UMS
   url = DEFAULT_CONTEXT_ROOT + "/simple?service=login" + \
      "&user=" + urllib.quote(user) + "&password=" + urllib.quote(pwd);

   conn = httplib.HTTPConnection(host)
   (rtnCode, sid) = doPost(conn, url, "")
   if rtnCode == -1:
      print "Failed to login to UMS server."
      sys.exit(1)
   conn.close()

   # Create a SOAP envelope
   (envelope, body) = buildSOAPEnvelope(sid)

   # Get a message from the server (if any)
   url = DEFAULT_CONTEXT_ROOT + "/xml"
   total = 0
   while 1:
      # Send request
      (rtnCode, xmlData) = doSend(url, envelope)
      if rtnCode == -1:
         break

      if len(xmlData) > 0:
         fileNameAttr = ""
         msg = ""

         root = etree.XML(xmlData)
         for e in root.getiterator():
            if (e.tag == body.tag and e.text != "None"):
               msg = e.text
            elif (e.tag == "File" and e.text != "None"):
               fileNameAttr = e.get("name")
               msg = e.text
 
         # For a binary message, i.e. a file, save the file in the current
         # working directory; otherwise print the text message received.
         if not (msg is None or len(msg) == 0):
            total += 1
            currentTime = datetime.datetime.now()
            if len(fileNameAttr) > 0:
               (file, ext) = os.path.splitext(fileNameAttr)
               fileName = file + "_" + str(long(time.time())) + ext
               data = base64.b64decode(msg)
               print currentTime.strftime("[%d/%m/%Y:%H:%M:%S ") + \
                  str(total) + "]: Received: " + fileNameAttr + \
                  " (" + str(len(data)) + " bytes" + ")"
               print "\tSaved File As: " + fileName
               tmpFile = open(fileName, "wb")
               tmpFile.write(data)
               tmpFile.close()
            else:
               print currentTime.strftime("[%d/%m/%Y:%H:%M:%S ") + \
                  str(total) + "]: Received: " + msg

   # Close the UMS session
   if len(sid) > 0:
      print
      print "Closing UMS connection, please wait..."

      conn = httplib.HTTPConnection(host)
      url = DEFAULT_CONTEXT_ROOT + "/simple?service=close&sid=" + sid
      doPost(conn, url, "")
      conn.close()

if __name__ == '__main__':
   main()
