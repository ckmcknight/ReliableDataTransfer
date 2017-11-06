"""
CS 3251 - Spring 2017
2nd programming assignment
DUE DATE: Monday, March 27, 5pm
Reliable Data Transfer
Author1: Stephen Zolnik
Author2: Connor Lindquist
Author3: Charlie McKnight
Python 2.6
"""
import sys
import socket
import time
import sys
import re
from packet import *
import random
import selective_repeat

class Connection:

    def __init__(self, port, serverIp, window):
        self.seq = -1
        self.clientWindow = window
        self.serverWindow = -1
        self.data = ""
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.settimeout(2.0)
        self.server_address = (serverIp, port)
        self.activeConnection = False
        self.num_tries = 10
        self.data_size = 1024

    """
    Start_up perfomrs the clients side of the 3-way handshake
    """
    def start_up(self):
        self.seq = random.randint(0, 2**10)
        while True:
            try:
                # Send data
                print("Sending Start Connection")
                message = createStartPacket(self.seq, self.clientWindow)
                sent = self.sock.sendto(message.encode(), self.server_address)

                # Receive response
                print("Waiting To Receive Start Ack")
                packet, server = self.sock.recvfrom(TOTAL_LENGTH)
                packet = Packet(packet)

                if packet.validChecksum() and packet.getType() == "2" and packet.getAck() == self.seq:
                    print("Received Correct StartAck")
                    self.serverWindow = packet.getWindow()
                    ack = packet.getSeq()
                    self.seq += 1
                    break
                else:
                    print("Received Invalid StartAck")

            except Exception as e:
                print(e)
                print("Error in first part of startup")

        print("Sending StartAck Acknowledgment")
        message = createAckPacket(ack)
        sent = self.sock.sendto(message.encode(), self.server_address)
        print("Connection Started")

    """
    Close connection function. Will gracefully shutdown if it can send and receive packet to/from server
    Otherwise: will timeout after ten attempts and force disconnect
    """
    def close_conn(self):
        tries = 0
        while True:
            try:
                if tries >= self.num_tries:
                    print("Failed to receive disconnect ack")
                    print("Disconnecting")
                    break
                print("Sending initial shutdown packet")
                shutdown = createClosePacket(self.seq)
                sent = self.sock.sendto(shutdown.encode(), self.server_address)

                print("Waiting to receive end ack")
                packet, server = self.sock.recvfrom(TOTAL_LENGTH)
                packet = Packet(packet)

                if packet.validChecksum() and packet.getType() == "3" and packet.getAck() == self.seq:
                    self.seq += 1
                    break
                else:
                    print("Received invalid endAck")

            except Exception as e:
                print(e)
                tries += 1
                print("Error in first part of close")

    """
    Tests if server is ready to receive file transfer packets
    """
    def check_response(self):
        tries = 0
        while tries < self.num_tries:
            try:
                # Send data
                print("Sending Test Connection Packet")
                message = createSendingPacket(self.seq)
                sent = self.sock.sendto(message.encode(), self.server_address)

                # Receive response
                print("Waiting To Test Connection Ack")
                packet, server = self.sock.recvfrom(TOTAL_LENGTH)
                packet = Packet(packet)

                if packet.validChecksum() and packet.getType() == "3" and packet.getAck() == self.seq:
                    print("Received Correct Connection Packet")
                    self.seq += 1
                    return True
                else:
                    print("Received Invalid Connection Packet - try number " + str(tries) + " out of " + str(self.num_tries))

            except Exception as e:
                tries += 1
                print(e)
                print("Error in first part of check response")
        return False

        print("Sending Connection Test Acknowledgment")
        message = createAckPacket(ack)
        sent = self.sock.sendto(message.encode(), self.server_address)
        print("Connection Established")

    """
    Function to split input file into 1024 byte chunks
    Input: Connection, text of file to read
    Output: Array where each element is 1024 bytes (when encoded)
    """
    def splitToArray(self, fileN):
        split = []
        for i in range(0, len(fileN), self.data_size):
            chunk = fileN[i:i+self.data_size]
            split.append(chunk.encode('utf-8'))
        return split

    """
    Reads in file text given command line inputs
    Input: Connection, name of file from command line
    Output: None
    """
    def readFile(self, fileName):
        try:
            splitChunks = []
            with open(fileName,'rb') as f:
                while True:
                    chunk = f.read(self.data_size)
                    if chunk:
                        splitChunks.append(chunk)
                    else:
                        numBlocks = len(splitChunks)
                        break
            return splitChunks
        except:
            raise IOError

    """
    Takes array returned from selective_repeat.py (from server) and converts back to file
    """
    def writeToFile(self, fileList, originalFileName):
        print("Reached write to file. Bytes to reconstruct: " + str(len(fileList)))
        splitName = originalFileName.split('.')
        newName = ""
        for i in range(0,len(splitName)-1):
            newName = newName + splitName[i] + "."
        newName = newName[:-1]
        newName = newName + "-received." + splitName[len(splitName)-1]
        with open(newName, 'w') as myFile:
            for line in fileList:
                myFile.write(line)
            myFile.close()
            return myFile

if __name__ == "__main__":
    if(len(sys.argv) < 3):
        print("Invalid number of input arguments")
        pass
    elif(":" not in sys.argv[1]):
        print("Invalid argument syntax")
        pass
    else:
        splitArg = sys.argv[1].split(':')
        _hostIP = splitArg[0]
        _port = splitArg[1]
        _window_size_client = sys.argv[2]
        if(not _port.isdigit()):
            print("Invalid port - not a number")
            pass
        elif(not _window_size_client.isdigit()):
            print("Invalid window size - not a number")
            pass
        elif(int(_port) < 0):
            print("Invalid port - must be positive integer")
            pass
        elif(int(_window_size_client) <= 0):
            print("Invalid window size - must be positive integer")
            pass
        else:
            window_size = _window_size_client
            _port = int(_port)
            _window_size_client = int(_window_size_client)
            try:
                print("initiating 3 way connection . . .")
                conn = Connection(_port, _hostIP, _window_size_client)
                conn.start_up()
                while True:
                    command = raw_input("Enter Command: ")
                    # command = "transform foo.txt"
                    if("transform" in command):
                        print("Transform called")
                        command = command.split(' ')
                        if(len(command) < 2):
                            print("Invalid file name")
                            pass
                        else:
                            fileName = command[1]
                            try:
                                contentList = conn.readFile(fileName)
                            except IOError:
                                print("File does not exist or name incorrect")
                                break
                            print("Beginning Selective Repeat data transfer . . .")
                            if(conn.check_response()):
                                selRep = selective_repeat.SelectiveRepeat(conn.sock, conn.serverWindow, conn.server_address)
                                if(selRep.sendMode(contentList) == False):
                                    print("Something went wrong in send mode - calling disconnect")
                                    conn.close_conn()
                                    break
                                recvFile = selRep.listenMode()
                                if(recvFile == False):
                                    print("Something went wrong receiving file - calling disconnect")
                                    conn.close_conn()
                                    break
                                print(recvFile)
                                outFile = conn.writeToFile(recvFile, fileName)
                            else:
                                print("Error in check response")
                                break
                    elif("disconnect" in command):
                        print("Disconnect called")
                        conn.close_conn()
                        break
                    else:
                        print("Not a valid input")
            finally:
                print("Closing client")
