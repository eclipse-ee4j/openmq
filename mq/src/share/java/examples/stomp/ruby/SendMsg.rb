#
# Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0, which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause
#

#############################################################################
#
# A simple script to send JMS text message(s) using STOMP Protocol.
#
require 'getoptlong' 
require 'socket'
require 'uri'

$host = nil
$port = nil
$dst = "simpleQ"
$msg = "Hello, World! from Ruby STOMP client"
$domain = "queue"
$user = "guest"
$passcode = "guest"
$count = 1

# Function to print usage
def usage
  puts
  puts "usage: jruby SendMsg.rb [options]"
  puts
  puts "where options include:"
  puts "  -h               Usage"
  puts "  -s <host:port>   Specify the STOMP server host and port."
  puts "  -d <name>        Specify the destination name. Default is simpleQ."
  puts "  -m \"<message>\"   Specify the msg to sent."
  puts "  -n <count>       Specify the number of message to send."
  puts "  -q               Specify the domain is a queue. Default is queue."
  puts "  -t               Specify the domain is a topic."
  puts "  -u <user>        Specify the user name. Default is guest."
  puts "  -p <passcode>    Specify the passcode. Default is guest."
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
      [ '--m', '-m', GetoptLong::REQUIRED_ARGUMENT ],
      [ '--n', '-n', GetoptLong::REQUIRED_ARGUMENT ],
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
      when '--m'
        $msg = arg
      when '--u'
        $user = arg
      when '--p'
        $passcode = arg
      when '--s'
        $host = arg
      when '--n'
        $count = arg.to_i
    end
  end

  if $host == nil
    puts "Please specify the STOMP server host and port!"
    usage()
  end
end

# Function to get protocol reply frame
#    Returns server's responsed frame as an array of string
def getReply(socket, command)

  puts "\nGet #{command} reply ..."

  replyFrame = Array.new

  # Read data from socket until we find the end of frame char, i.e. null
  until (line = socket.gets) == nil
    line = line.chomp

    if line == "\0"
      break
    end

    replyFrame.push(line)

    # Check if null char is at the end of the string
    if line.rindex("\0") != nil
      break
    end
  end

  return replyFrame
end

# Function to check status
#    Returns server's responsed frame as an array of string
#    Raise exception if not successfull
def checkStatus(socket, command)

  replyFrame = getReply(socket, command)

  if replyFrame.length == 0
    raise "No reply"
  end

  if replyFrame.first == "ERROR"
    raise replyFrame.join("\n")
  end

  return replyFrame
end

# Function to transmit data
#    Raise exception if not successfull
def doTransmit(socket, command, headers={}, body="")

  puts "\nTransmit #{command} ..."

  begin
    # Write STOMP command
    socket.write "#{command}\n"

    # Write headers
    headers.each do | k,v |
      socket.write "#{k}:#{v}\n"
    end

    # Write blank line; indicates the end of the headers
    socket.write "\n"

    # Write body
    socket.write body

    socket.write "\0"
    socket.flush
  rescue
    raise "Error: #{$!}"
  end
end

# Main program
begin

  # Process command line args
  parseArgs()

  puts "STOMP server: #{$host}, Destination: #{$dst}, Domain: #{$domain}\n"

  # Open a stream socket
  url = URI.parse("tcp://#{$host}")
  socket = TCPSocket::new(url.host, url.port)

  # Connect to the STOMP server
  begin
    headers = { "login" => $user, "passcode" => $passcode }
    doTransmit(socket, "CONNECT", headers)
    replyFrame = checkStatus(socket, "CONNECT")

    # Print out the reply
    replyFrame.each do | value |
      puts value
    end
  rescue
    puts "Failed to connect to STOMP server.", $!
    socket.close
    exit 1
  end

  # Send message(s)
  headers = {
    "destination" => "/#{$domain}/#{$dst}",
  }
  i = 0
  while i < $count
     if $count > 1
        textMsg = "(msg##{i}) #{$msg}"
     else
        textMsg = $msg
     end

     # Send request
     begin
       headers["receipt"] = "message-#{i}"
       doTransmit(socket, "SEND", headers, textMsg)
       replyFrame = checkStatus(socket, "SEND")

       # Print out the reply
       replyFrame.each do | value |
         puts value
       end
     rescue
        puts "Failed to send message.", $!
        break
     end

     puts "Sent msg: #{textMsg}"
     i += 1
  end

  # Disconnect from STOMP server
  begin
    doTransmit(socket, "DISCONNECT")
  rescue
    puts "Failed to disconnect from STOMP server.", $!
  end

  # Closing the socket
  socket.close
end
