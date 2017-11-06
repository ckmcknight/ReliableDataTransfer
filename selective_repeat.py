from packet import *
import time
import threading
import random

class SelectiveRepeat:

    def __init__(self, socket, serverWindow, server_address):
        self.server_address = server_address
        self.serverWindow = serverWindow
        self.sock = socket
        self.maxRetries = 10
        self.retryTime = 2
        self.sentData = False
        self.deadConnection = False
        self.sentPacketList = []
        self.sentPacketMap = {} # seq -> (acked, time, packet)

    def listenMode(self):
        map = self.receiveFile()
        if map == False:
            return False
        else:
            itemList = list(map.items())
            itemList.sort(key = lambda x : x[0])
            dataList = [x[1] for x in itemList]
            file = "".join(dataList)
            return file

    def receiveFile(self):
        finished = False
        retries = 0
        packetMap = {}
        while not finished:
            if retries >= self.maxRetries:
                return False
            try:
                print("Receiving Packet")
                packet, server = self.sock.recvfrom(TOTAL_LENGTH)
                packet = Packet(packet)

                if packet.validChecksum() and packet.getType() == "4":
                    retries = 0
                    print("Received Data Packet")
                    print("Packet: " + str(packet.getSeq()))
                    packetMap[packet.getSeq()] = packet.getData()
                    message = createAckPacket(packet.getSeq())
                    self.sock.sendto(message.encode(), self.server_address)
                elif packet.validChecksum() and packet.getType() == "5":
                    retries = 0
                    print("Received End Packet")
                    finished = True
                    message = createAckPacket(packet.getSeq())
                    self.sock.sendto(message.encode(), self.server_address)
                elif not packet.validChecksum():
                    print("Actual Checksum: " + str(packet.getActualChecksum()))
                    print("Calculated Checksum: " + str(packet.getCalculatedChecksum()))
                    print(packet.getData())
                    print("Received Packet with Bad Checksum")

            except Exception as e:
                print(e)
                retries += 1
        return packetMap

    def sendMode(self, msgList):
        msgList = msgList[::-1]
        lock = threading.Lock()
        sendingThread = SendingDataThread(self, lock, msgList, self.serverWindow, self.server_address, self.retryTime)
        receivingThread = ReceiveAcksThread(self, lock, self.server_address)
        threads = [sendingThread, receivingThread]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        if self.sentData:
            return True
        elif self.deadConnection:
            return False
        else:
            print("Error in the threads, impossible case occured")
            return False

class ReceiveAcksThread(threading.Thread):

    def __init__(self, sr, lock, address):
        self.sr = sr
        self.lock = lock
        self.delay = .001
        self.address = address
        threading.Thread.__init__(self)

    def run(self):
        timeouts = 0
        self.lock.acquire()
        while not self.sr.deadConnection and not self.sr.sentData:
            self.lock.release()
            print("Running Receive Acks")
            if timeouts >= self.sr.maxRetries:
                print("Failed to receive data in max number of timeouts")
                self.lock.acquire()
                self.sr.deadConnection = True
                self.lock.release()
                return
            try:
                packet, server = self.sr.sock.recvfrom(TOTAL_LENGTH)
                packet = Packet(packet)
                timeouts = 0
                if (packet.validChecksum() and packet.getType() == "3"):
                    self.lock.acquire()
                    if packet.getAck() in self.sr.sentPacketList:
                        self.sr.sentPacketList.remove(packet.getAck())
                    if packet.getAck() in self.sr.sentPacketMap:
                        self.sr.sentPacketMap[packet.getAck()][0] = True
                    else:
                        print("Received Ack from unsent packet")
                    self.lock.release()
                elif packet.validChecksum() and packet.getType() == "2":
                    ackPack = createAckPacket(packet.getSeq())
                    self.sr.sock.sendto(ackPack, self.address)
                elif packet.validChecksum() and packet.getType() == "5":
                    ackPack = createAckPacket(packet.getSeq())
                    self.sr.sock.sendto(ackPack, self.address)
                    sendPacket = createSendingPacket(101)
                    self.sr.sock.sendto(sendPacket, self.address)
                else:
                    print("Received Corrupted or Unexpected Packet")

            except Exception as e:
                print(e)
                print("Timed-out while trying to receive Acks")
                timeouts += 1

            time.sleep(self.delay)
            self.lock.acquire()
        self.lock.release()
        print("Ending Receive Ack Thread")


class SendingDataThread(threading.Thread):

    def __init__(self, sr, lock, msgList, window, address, retryTime):
        self.window = window
        self.msgList = msgList
        self.sr = sr
        self.lock = lock
        self.delay = .001
        self.address = address
        self.retryTime = retryTime
        self.maxRetries = 10
        threading.Thread.__init__(self)

    def run(self):
        oldest = random.randint(0, 2**10)
        newest = oldest
        self.lock.acquire()
        while not self.sr.deadConnection and not self.sr.sentData:
            if oldest in self.sr.sentPacketMap and self.sr.sentPacketMap[oldest][0] == True:
                oldest += 1
                continue
            self.lock.release()

            self.lock.acquire()
            if len(self.msgList) == 0 and len(self.sr.sentPacketList) == 0:
                self.sr.sentData = True
                self.lock.release()
                break
            self.lock.release()
            if newest - oldest < self.window and len(self.msgList) != 0:
                print("Sending new Packet: " + str(newest))
                print(str(oldest))
                print(str(self.window))
                newPacket = createDataPacket(newest, self.msgList.pop())
                self.lock.acquire()
                self.sr.sentPacketList.append(newest)
                self.sr.sentPacketMap[newest] = [False, time.time(), newPacket]
                self.lock.release()
                newest += 1
                try:
                    self.sr.sock.sendto(newPacket, self.address)
                except Exception as e:
                    print(e)
                    print("Failed to send data in send data thread")

            self.lock.acquire()
            #print("Packet List" + str(self.sr.sentPacketList))
            #print("Packet Map" + str(self.sr.sentPacketMap))
            #print("Current Time" + str(time.time()))
            if len(self.sr.sentPacketList) != 0 and time.time() - self.sr.sentPacketMap[self.sr.sentPacketList[0]][1] > self.retryTime:
                resendSeq = self.sr.sentPacketList.pop(0)
                self.sr.sentPacketList.append(resendSeq)
                self.sr.sentPacketMap[resendSeq][1] = time.time()
                data = self.sr.sentPacketMap[resendSeq][2]
                print("Resending Packet: " + str(resendSeq))
                
                try:
                    self.sr.sock.sendto(data, self.address)
                except Exception as e:
                    print(e)
                    print("Failed to send data in send data thread")
            self.lock.release()

            time.sleep(self.delay)
            self.lock.acquire()

        print("Escaped While Loop")
        time.sleep(2)
        #self.lock.acquire()
        #print("Aquired Lock")
        if self.sr.sentData:
            try:
                self.lock.release()
            except:
                print("Released unlocked lock")
            endPacket = createEndPacket(newest)
            endAcked = False
            timeouts = 0
            self.lock.acquire()
            while not endAcked and not self.sr.deadConnection:
                try:
                    self.lock.release()
                except:
                    print("Released unlocked lock")
                if timeouts >= self.maxRetries:
                    print("Failed to receive data in max number of timeouts")
                    self.lock.acquire()
                    self.sr.deadConnection = True
                    self.lock.release()
                    return
                try:
                    self.sr.sock.sendto(endPacket, self.address)
                    ack, server = self.sr.sock.recvfrom(TOTAL_LENGTH)
                    ackPacket = Packet(ack)
                    if ackPacket.getAck() == newest:
                        endAcked = True
                except Exception as e:
                    print(e)
                    print("Failed to send end of data stream packet")
                    timeouts += 1
            try:
                self.lock.release()
            except:
                print("Released unlocked lock")
        else:
            self.lock.release()
        print("Ending Send Data Thread")
