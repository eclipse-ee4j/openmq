/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

using System;
using System.IO;
using System.Text;
using System.Web;
using System.Net;
using System.Xml;

/*
 * A simple program to send JMS text or binary message(s) through HTTP/POST
 * using XML/SOAP. When sending a file, i.e. binary message, the content 
 * of the file is base64 encoded and place in the body of the SOAP message.
 */
public class SendSOAPMsg
{
    public static string DEFAULT_CONTEXT_ROOT = "/ums";

    // Namespaces used
    public static string NS_SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    public static string NS_SOAP_ENC = "http://schemas.xmlsoap.org/soap/encoding/";
    public static string NS_MQ_SERVICE = "https://mq.java.net/ums";

    // Define tags & attrs
    public static string SOAP_PREFIX = "SOAP-ENV";
    public static string SOAP_ENVELOPE = "Envelope";
    public static string SOAP_HEADER = "Header";
    public static string SOAP_BODY = "Body";

    public static string MQ_PREFIX = "ums";
    public static string MQ_MESSAGEHEADER = "MessageHeader";
    public static string MQ_MESSAGEID = "MessageID";
    public static string MQ_SERVICE = "Service";

    public static string MQ_ID_ATTR = "id";
    public static string MQ_VERSION_ATTR = "version";
    public static string MQ_SERVICE_ATTR = "service";
    public static string MQ_DESTINATION_ATTR = "destination";
    public static string MQ_DOMAIN_ATTR = "domain";
    public static string MQ_SID_ATTR = "sid";
    public static string MQ_USER_ATTR = "user";
    public static string MQ_PASSWORD_ATTR = "password";

    string host = null;
    string service = "send";
    string destination = "simpleSoapQ";
    string domain = "queue";
    string msg = "Hello, C# World!";
    string user = "guest";
    string password = "guest";
    string filePath = null;
    int count = 1;

    /**
     * Print usage
     */
    public void usage()
    {
        Console.WriteLine();
        Console.WriteLine("usage: SendSOAPMsg.exe [options]");
        Console.WriteLine("where options include:");
        Console.WriteLine("  -h               Usage");
        Console.WriteLine("  -s <host:port>   Specify the server host and port.");
        Console.WriteLine("  -d <name>        Specify the destination name. Default is {0}.", destination);
        Console.WriteLine("  [-m \"<message>\" | -f <file>]");
        Console.WriteLine("                   Specify a text message or a file to sent.");
        Console.WriteLine("  -n <count>       Specify number of message to send.");
        Console.WriteLine("  -q               Specify domain is a queue. Default is queue.");
        Console.WriteLine("  -t               Specify domain is a topic.");
        Console.WriteLine("  -u <user>        Specify the user name. Default is guest.");
        Console.WriteLine("  -p <password>    Specify the password. Default is guest.");
        Console.WriteLine();
    }

    /**
     * Default constructor
     */
    public SendSOAPMsg()
    {
    }

    /**
     * Parse command line arguments
     */
    public void parseArgs(string[] args)
    {
        for (int i = 0; i < args.Length; i++)
        {
            string arg = args[i];

            if (arg[0] != '-' && arg[0] != '/')
            {
                throw new ArgumentException();
            }

            switch (arg.TrimStart('-', '/'))
            {
                case "s":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    host = args[++i];
                    break;
                case "d":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    destination = args[++i];
                    break;
                case "f":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    filePath = args[++i];
                    break;
                case "m":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    msg = args[++i];
                    break;
                case "n":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    count = Int32.Parse(args[++i]);
                    break;
                case "q":
                    domain = "queue";
                    break;
                case "t":
                    domain = "topic";
                    break;
                case "u":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    user = args[++i];
                    break;
                case "p":
                    if (args.Length < i + 2)
                    {
                        throw new ArgumentException();
                    }
                    password = args[++i];
                    break;
                case "h":
                default:
                    throw new ArgumentException();
            }
        }

        if (host == null || host.Length == 0)
        {
            throw new ArgumentException("Please specify the UMS server host and port!");
        }

        if (count > 1 && filePath != null)
        {
            Console.WriteLine("WARNING: \'-n\' option ignored. Applicable to text message only.");
            count = 1;
        }
    }

    /**
     * Write the XmlDocument node to the console
     */
    public void writeXml(XmlDocument doc)
    {
        XmlTextWriter writer = new XmlTextWriter(Console.Out);
        writer.Formatting = Formatting.Indented;
        doc.WriteTo(writer);
        writer.Close();
        Console.WriteLine();
    }

    /**
     * Load the specified file as a base64 encoded string
     */
    public string loadFileAsBase64String(string inputFileName)
    {
        System.IO.FileStream inFile;
        byte[] binaryData = null;
        string base64String = null;

        try
        {
            binaryData = File.ReadAllBytes(inputFileName);

            inFile = new FileStream(inputFileName, FileMode.Open, FileAccess.Read);
            binaryData = new Byte[inFile.Length];
            inFile.Read(binaryData, 0, (int)inFile.Length);
            inFile.Close();
        }
        catch (System.Exception e)
        {
            // Error creating stream or reading from it.
            Console.WriteLine("Error reading file: {0}", e.Message);
        }

        // Convert the binary input into Base64 output.
        try
        {
            base64String = Convert.ToBase64String(binaryData, 0, binaryData.Length);
        }
        catch (System.ArgumentNullException)
        {
            Console.WriteLine("Binary data array is null.");
        }

        return base64String;
    }

    /**
     * Post a request and return the server's responsed data.
     */
    public string doPost(String url, String body)
    {
        // Construct HTTP request and send the request
        HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
        req.Method = "POST";
        req.UserAgent = "C# post";
        req.ContentType = "text/plain; charset=\"UTF-8\"";
        req.KeepAlive = false;

        if (body != null && body.Length > 0) {
            Stream reqStream = req.GetRequestStream();
            byte[] buffer = Encoding.ASCII.GetBytes(body);
            reqStream.Write(buffer, 0, buffer.Length); 
            reqStream.Close();
        }

        // Get the response
        HttpWebResponse res = (HttpWebResponse)req.GetResponse();
        if (res.StatusCode != HttpStatusCode.OK)
        {
            StringBuilder sb = new StringBuilder();
            sb.AppendFormat("Failed to post data to http://{0}{1}", host, url).AppendLine();
            sb.AppendFormat("Response: {0} {1}", res.StatusCode, res.StatusDescription);
            res.Close();
            throw new WebException(sb.ToString());
        }

        Stream resStream = res.GetResponseStream();
        StreamReader reader = new StreamReader(resStream);
        String resData = reader.ReadToEnd();
        reader.Close();
        resStream.Close();
        res.Close();

        return resData;
    }

    /**
     * Send a SOAP msg and return the server's responsed data.
     */
    public String doSend(string url, XmlDocument xmlDoc)
    {
        // Construct HTTP request and send the request
        HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
        req.Method = "POST";
        req.UserAgent = "C# post";
        req.ContentType = "text/xml; charset=\"UTF-8\"";
        req.KeepAlive = false;

        Stream reqStream = req.GetRequestStream();
        xmlDoc.Save(reqStream);
        reqStream.Close();

        // Get the response
        HttpWebResponse res = (HttpWebResponse)req.GetResponse();
        if (res.StatusCode != HttpStatusCode.OK)
        {
            StringBuilder sb = new StringBuilder();
            sb.AppendFormat("Failed to post data to {0}", req.RequestUri).AppendLine();
            sb.AppendFormat("Response: {0} {1}", res.StatusCode, res.StatusDescription);
            res.Close();
            throw new WebException(sb.ToString());
        }

        Stream resStream = res.GetResponseStream();
        StreamReader reader = new StreamReader(resStream);
        String resData = reader.ReadToEnd();
        reader.Close();
        resStream.Close();
        res.Close();

        return resData; // Returns response data as an XML string
    }

    /**
     * Send the message(s) by posting the web request
     */
    public void sendMessage()
    {
        // Get a session ID when sending more than 1 text msg
        string sID = null;
        if (count > 1)
        {
            // Login to UMS
            try
            {
                String url = string.Format(
                    "http://{0}{1}//simple?service=login&user={2}&password={3}",
                    host, DEFAULT_CONTEXT_ROOT, 
                    HttpUtility.UrlEncode(user),
                    HttpUtility.UrlEncode(password));
                sID = doPost(url, null);

                // Handle Ctrl-C
                System.Console.CancelKeyPress += delegate
                {
                    // Close the UMS session
                    if (!String.IsNullOrEmpty(sID))
                    {
                        Console.WriteLine();
                        Console.WriteLine("Closing UMS connection, please wait...");

                        try
                        {
                            url = string.Format(
                                "http://{0}{1}//simple?service=close&sid={2}",
                                host, DEFAULT_CONTEXT_ROOT, sID);
                            doPost(url, null);
                        }
                        catch (Exception e)
                        {
                            Console.WriteLine("Failed to close UMS connection.");
                            throw e;
                        }
                    }
                };
            }
            catch (Exception e)
            {
                Console.WriteLine("Failed to login to UMS server.");
                throw e;
            }
        }

        // Build SOAP envelope
        XmlDocument xmlDoc = new XmlDocument();
        XmlElement envelopeE = xmlDoc.CreateElement(SOAP_PREFIX, SOAP_ENVELOPE, NS_SOAP_ENV);
        XmlElement headerE = xmlDoc.CreateElement(SOAP_PREFIX, SOAP_HEADER, NS_SOAP_ENV);
        XmlElement bodyE = xmlDoc.CreateElement(SOAP_PREFIX, SOAP_BODY, NS_SOAP_ENV);
        xmlDoc.AppendChild(envelopeE);
        envelopeE.AppendChild(headerE);
        envelopeE.AppendChild(bodyE);

        XmlElement msgHeader = xmlDoc.CreateElement(MQ_PREFIX, MQ_MESSAGEHEADER, NS_MQ_SERVICE);
        msgHeader.SetAttribute(MQ_ID_ATTR, NS_MQ_SERVICE, "1.0");
        msgHeader.SetAttribute(MQ_VERSION_ATTR, NS_MQ_SERVICE, "1.1");
        
        headerE.AppendChild(msgHeader);

        XmlElement serviceE = xmlDoc.CreateElement(MQ_PREFIX, MQ_SERVICE, NS_MQ_SERVICE);
        serviceE.SetAttribute(MQ_SERVICE_ATTR, NS_MQ_SERVICE, service);
        serviceE.SetAttribute(MQ_DESTINATION_ATTR, NS_MQ_SERVICE, destination);
        serviceE.SetAttribute(MQ_DOMAIN_ATTR, NS_MQ_SERVICE, domain);

        if (String.IsNullOrEmpty(sID))
        {
            serviceE.SetAttribute(MQ_USER_ATTR, NS_MQ_SERVICE, user);
            serviceE.SetAttribute(MQ_PASSWORD_ATTR, NS_MQ_SERVICE, password);
        }
        else
        {
            serviceE.SetAttribute(MQ_SID_ATTR, NS_MQ_SERVICE, sID);
        }

        msgHeader.AppendChild(serviceE);   

        /*
         * Put the message in the SOAP body. If we're sending a file,
         * then convert the content to base64 encoded string.
         */
        if (String.IsNullOrEmpty(filePath))
        {
            bodyE.InnerText = msg;
        }
        else
        {
            string encodedData = loadFileAsBase64String(filePath);

            // Create a 'File' element to hold the data & set the 'name' attr
            XmlElement fileE = xmlDoc.CreateElement("File");
            fileE.SetAttribute("name", Path.GetFileName(filePath));
            fileE.InnerText = encodedData;

            bodyE.AppendChild(fileE);
        }

        // Send the message(s)
        try
        {
            String url = string.Format("http://{0}{1}/xml", host, DEFAULT_CONTEXT_ROOT);

            for (int i = 0; i < count; i++)
            {
                // Print the SOAP msg to the console for debugging
                //writeXml(xmlDoc);

                // Prepend msg count for text msg only; otherwise leave msg as is
                if (count > 1)
                {
                    bodyE.InnerText = "(msg#" + i + ") " + msg;
                }

                // Send the SOAP msg
                doSend(url, xmlDoc);

                if (String.IsNullOrEmpty(filePath))
                {
                    Console.WriteLine("Send SOAP msg: {0}", bodyE.InnerText);
                }
                else
                {
                    Console.WriteLine("Send SOAP msg: (File) {0}", filePath);
                }
            }
        }
        catch (WebException e)
        {
            Console.WriteLine("HTTP request failed: {0}", e.Message);
            Console.WriteLine(e.StackTrace.ToString());
        }

        // Close the UMS session
        if (!String.IsNullOrEmpty(sID))
        {
            Console.WriteLine();
            Console.WriteLine("Closing UMS connection, please wait...");

            try
            {
                String url = string.Format(
                    "http://{0}{1}//simple?service=close&sid={2}",
                    host, DEFAULT_CONTEXT_ROOT, sID);
                doPost(url, null);
            }
            catch (Exception e)
            {
                Console.WriteLine("Failed to close UMS connection.");
                throw e;
            }
        }
    }

    static void Main(string[] args)
    {
        SendSOAPMsg sender = new SendSOAPMsg();

        try
        {
            sender.parseArgs(args);

            Console.WriteLine("UMS Server: {0}, Destination: {1}, Domain: {2}",
                sender.host, sender.destination, sender.domain);
            Console.WriteLine();
        }
        catch (Exception e)
        {
            Console.WriteLine("An error occurred while parsing command line arguments: {0}", e.ToString());
            sender.usage();
            return;
        }

        sender.sendMessage();
    }
}
