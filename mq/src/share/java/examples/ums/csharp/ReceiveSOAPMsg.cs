/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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
 * A simple program to receive JMS text or binary message(s) through HTTP/POST
 * using XML/SOAP. When receiving a file, i.e. binary message, the file will
 * be saved in the current working directory.
 */
public class ReceiveSOAPMsg
{
    public static string DEFAULT_CONTEXT_ROOT = "/ums";
    public static string DEFAULT_TIMEOUT = "15000";

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

    public static string MQ_SERVICE = "Service";
    public static string MQ_ID_ATTR = "id";
    public static string MQ_VERSION_ATTR = "version";
    public static string MQ_SERVICE_ATTR = "service";
    public static string MQ_DESTINATION_ATTR = "destination";
    public static string MQ_DOMAIN_ATTR = "domain";
    public static string MQ_SID_ATTR = "sid";
    public static string MQ_USER_ATTR = "user";
    public static string MQ_PASSWORD_ATTR = "password";
    public static string MQ_TIMEOUT_ATTR = "timeout";
    public static string MQ_STATUS_ATTR = "status";

    string host = null;
    string service = "receive";
    string destination = "simpleSoapQ";
    string domain = "queue";
    string user = "guest";
    string password = "guest";

    /**
     * Print usage
     */
    public void usage()
    {
        Console.WriteLine();
        Console.WriteLine("usage: ReceiveSOAPMsg.exe [options]");
        Console.WriteLine("where options include:");
        Console.WriteLine("  -h               Usage");
        Console.WriteLine("  -s <host:port>   Specify the server host and port.");
        Console.WriteLine("  -d <name>        Specify the destination name. Default is {0}.", destination);
        Console.WriteLine("  -q               Specify domain is a queue. Default is queue.");
        Console.WriteLine("  -t               Specify domain is a topic.");
        Console.WriteLine("  -u <user>        Specify the user name. Default is guest.");
        Console.WriteLine("  -p <password>    Specify the password. Default is guest.");
        Console.WriteLine();
    }

    /**
     * Default constructor
     */
    public ReceiveSOAPMsg()
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
     * Save the specified base64 encoded string as a file in the current directory
     */
    public int saveFileFromBase64String(string outputFileName, string base64String)
    {
        System.IO.FileStream outFile;
        byte[] binaryData = null;
        int bytesWrite = 0;

        // Convert to binary output from Base64 string.
        try
        {
            binaryData = Convert.FromBase64String(base64String);
        }
        catch (System.ArgumentNullException)
        {
            Console.WriteLine("Base 64 binary data is null.");
        }

        try
        {
            outFile = new FileStream(outputFileName, FileMode.Create, FileAccess.Write);
            bytesWrite = binaryData.Length;
            outFile.Write(binaryData, 0, bytesWrite);
            outFile.Close();
        }
        catch (System.Exception e)
        {
            // Error creating stream or writting to it.
            Console.WriteLine("Error writing file: {0}", e.Message);
        }

        return bytesWrite;
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

        if (body != null && body.Length > 0)
        {
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
            sb.AppendFormat("Failed to post data to ", url).AppendLine();
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
    public XmlDocument doSend(string url, XmlDocument xmlDoc)
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
        XmlDocument resData = new XmlDocument();
        resData.Load(resStream);
        resStream.Close();
        res.Close();

        return resData; // Returns response data is an XmlDocument
    }

    public void receiveMessage()
    {
        // Login to UMS to get a session ID
        string sID = null;
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
        serviceE.SetAttribute(MQ_TIMEOUT_ATTR, NS_MQ_SERVICE, DEFAULT_TIMEOUT);
        serviceE.SetAttribute(MQ_SID_ATTR, NS_MQ_SERVICE, sID);

        msgHeader.AppendChild(serviceE);

        // Get a message from the server (if any)
        try
        {
            String url = string.Format("http://{0}{1}/xml", host, DEFAULT_CONTEXT_ROOT);
            
            int total = 0;
            while (true)
            {
                // Print the SOAP msg to the console for debugging
                //writeXml(xmlDoc);

                // Send request
                XmlDocument xmlRes = doSend(url, xmlDoc);

                /*
                 * Read and parse the SOAP response:
                 *
                 * Note: If there is no message to receive, the ums:status attribute in the
                 *       ums:Service element of the ums:MessageHeader element is set to 404,
                 *       e.g. <ums:Service ums:service="receive_reply" ums:status="404"/>
                 */
                XmlNodeList nodItems = xmlRes.GetElementsByTagName(MQ_SERVICE, NS_MQ_SERVICE);
                if (nodItems.Count > 0)
                {
                    XmlAttributeCollection attCol = nodItems[0].Attributes;
                    XmlAttribute serviceAtt = attCol[MQ_SERVICE_ATTR, NS_MQ_SERVICE];
                    if (serviceAtt != null && serviceAtt.Value.Equals("receive_reply"))
                    {
                        XmlAttribute statusAtt = attCol[MQ_STATUS_ATTR, NS_MQ_SERVICE];
                        if (statusAtt != null && statusAtt.Value.Equals("404"))
                        {
                            continue; // No message to receive
                        }
                    }
                }

                // Retrieve the message from the SOAP body
                string fileName = null;
                string msg = null;

                nodItems = xmlRes.GetElementsByTagName(SOAP_BODY, NS_SOAP_ENV);
                if (nodItems.Count > 0)
                {
                    XmlNode bodyN = nodItems[0];
                    nodItems = xmlRes.GetElementsByTagName("File");
                    if (nodItems.Count > 0)
                    {
                        XmlNode fileN = nodItems[0];
                        XmlAttributeCollection attCol = fileN.Attributes;
                        XmlAttribute fileNameAttr = attCol["name"];
                        if (fileNameAttr != null)
                        {
                            fileName = fileNameAttr.Value;
                            msg = fileN.InnerText;
                        }
                    }
                    else
                    {
                        msg = bodyN.InnerText;
                    }
                }

                // For a binary message, i.e. a file, save the file in the current
                // working directory; otherwise print the text message received.
                DateTime time = DateTime.Now;
                total += 1;
                if (fileName != null)
                {
                    string file = Path.GetFileNameWithoutExtension(fileName);
                    string ext = Path.GetExtension(fileName);
                    string newFile = string.Format("{0}_{1}{2}", file, DateTime.Now.Ticks, ext);
                    int fileSize = saveFileFromBase64String(newFile, msg);
                    Console.WriteLine("[{0} {1}]: Received: {2} ({3} bytes)",
                        time.ToString("dd/MM/yyyy:HH:mm:ss"), total, fileName, fileSize);
                    Console.WriteLine("\tSaved File As: {0}", newFile);

                    // Starts a process resource for the file received, 
                    // e.g. play mp3 file or view image file, ...
                    try
                    {
                        System.Diagnostics.Process.Start(newFile);
                    }
                    catch (Exception e)
                    {
                        Console.WriteLine("Failed to start a process resource for {0}: {1}",
                            newFile, e.Message);
                    }
                }
                else
                {
                    Console.WriteLine("[{0} {1}]: Received: {2}",
                        time.ToString("dd/MM/yyyy:HH:mm:ss"), total, msg);
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
        ReceiveSOAPMsg receiver = new ReceiveSOAPMsg();

        try
        {
            receiver.parseArgs(args);

            Console.WriteLine("UMS Server: {0}, Destination: {1}, Domain: {2}",
                receiver.host, receiver.destination, receiver.domain);
            Console.WriteLine();
        }
        catch (Exception e)
        {
            Console.WriteLine("An error occurred while parsing command line arguments: " + e.ToString());
            receiver.usage();
            return;
        }

        receiver.receiveMessage();
    }
}
