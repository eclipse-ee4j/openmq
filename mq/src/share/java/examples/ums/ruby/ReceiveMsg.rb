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

#############################################################################
#
# A simple script to receive JMS text message(s) using UMS API over HTTP.
#
require 'getoptlong'
require 'net/http'
require 'uri'
require 'cgi'

$DEFAULT_CONTEXT_ROOT = "/ums"
$DEFAULT_TIMEOUT = "15000"

$host = nil
$dst = "simpleQ"
$domain = "queue"
$user = "guest"
$pwd = "guest"

# Function to print usage
def usage
  puts
  puts "usage: jruby ReceiveMsg.rb [options]"
  puts
  puts "where options include:"
  puts "  -h               Usage"
  puts "  -s <host:port>   Specify the server host and port."
  puts "  -d <name>        Specify the destination name. Default is simpleQ."
  puts "  -q               Specify domain is a queue. Default is queue."
  puts "  -t               Specify domain is a topic."
  puts "  -u <user>        Specify the user name. Default is guest."
  puts "  -p <password>    Specify the password. Default is guest."
  puts
  exit 1
end

# Function to parse command line arguments
def parseArgs
  begin
    opts = GetoptLong.new(
      [ '--h', '-h', GetoptLong::NO_ARGUMENT ],
      [ '--q', '-q', GetoptLong::NO_ARGUMENT ],
      [ '--t', '-t', GetoptLong::NO_ARGUMENT ],
      [ '--d', '-d', GetoptLong::REQUIRED_ARGUMENT ],
      [ '--s', '-s', GetoptLong::REQUIRED_ARGUMENT ],
      [ '--u', '-u', GetoptLong::REQUIRED_ARGUMENT ],
      [ '--p', '-p', GetoptLong::REQUIRED_ARGUMENT ]
  )
  rescue
    puts "Error: parsing command line arguments"
    usage()
  end

  opts.each do |opt, arg|
    case opt
      when '--h'
        usage()
      when '--q'
        $domain = "queue"
      when '--t'
        $domain = "topic"
      when '--d'
        $dst = arg
      when '--u'
        $user = arg
      when '--p'
        $pwd = arg
      when '--s'
        $host = arg
    end
  end

  if $host == nil
    puts "Please specify the UMS server host and port!"
    usage()
  end
end

# Function to post request
#    Returns server's responsed data if successfull
def doPost(http, url, body)
  error = nil
  respData = nil
  headers = {
    "Content-Type" => "text/plain;charset=UTF-8",
    "Accept" => "text/plain"
  }

  begin
    resp = http.post(url, body, headers)

    # Get the response
    if resp.code == "200"
      respData = resp.body
    else
      error = "Failed to post data to http://#{$host}#{url}\n" +
              "Response: #{resp.code} #{resp.message}"
    end
  rescue
    error = "Error: #{$!}: Cannot post data to http://#{$host}#{url}"
  end

  if error != nil
    raise error
  end

  return respData
end

# Main program
begin

  # Process command line args
  parseArgs()

  puts "UMS Server: #{$host}, Destination: #{$dst}, Domain: #{$domain}\n"

  # Open a connection to the server
  url = URI.parse("http://#{$host}")
  http = Net::HTTP.new(url.host, url.port)

  # Login to UMS
  sid = nil
  begin
    url = "#{$DEFAULT_CONTEXT_ROOT}/simple?service=login" +
     "&user=" + CGI.escape($user) + "&password=" + CGI.escape($pwd);
    sid = doPost(http, url, nil)
  rescue
    puts "Failed to login to UMS server.", $!
    exit 1
  end

  # Get a message from the server (if any)

  url = "#{$DEFAULT_CONTEXT_ROOT}/simple?service=receive" +
        "&destination=#{$dst}&domain=#{$domain}&sid=#{sid}" +
        "&timeout=#{$DEFAULT_TIMEOUT}"

  while true
    # Send request to retrieve a JMS message
    begin
      respMsg = doPost(http, url, nil)
    rescue
      puts $!
      break
    end

    if respMsg.length > 0
      time = Time.now
      puts time.strftime("[%d/%m/%Y:%H:%M:%S] ") + "Received msg: #{respMsg}"
    end
  end

  # Close the UMS session
  puts
  puts "Closing UMS connection, please wait..."

  begin
    url = "#{$DEFAULT_CONTEXT_ROOT}/simple?service=close&sid=#{sid}"
    respMsg = doPost(http, url, nil)
  rescue
    puts "Failed to close UMS connection.", $!
  end
end
