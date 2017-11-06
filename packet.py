CHECKSUM_LENGTH = 8
TYPE_LENGTH = 1
ACKSEQ_LENGTH = 8
DATA_LENGTH = 1024
LENGTH_LENGTH = 3
WINDOW_LENGTH = 8
TOTAL_LENGTH = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + DATA_LENGTH + LENGTH_LENGTH + WINDOW_LENGTH

class Packet:

    def __init__(self, packetStr):
        self.packetStr = packetStr

    def validChecksum(self):
        value = int(self.packetStr[:CHECKSUM_LENGTH], 16)
        calculated = checkSum(self.packetStr[CHECKSUM_LENGTH:])
        return value == calculated

    def getCalculatedChecksum(self):
        return checkSum(self.packetStr[CHECKSUM_LENGTH:])

    def getActualChecksum(self):
        return int(self.packetStr[:CHECKSUM_LENGTH], 16)

    def getType(self):
        return self.packetStr[CHECKSUM_LENGTH: CHECKSUM_LENGTH + TYPE_LENGTH]

    def getSeq(self):
        start = CHECKSUM_LENGTH + TYPE_LENGTH
        end = start + ACKSEQ_LENGTH
        return int(self.packetStr[start : end], 16)

    def getAck(self):
        if self.getType() == "2":
            start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH + WINDOW_LENGTH
            end = start + ACKSEQ_LENGTH
            return int(self.packetStr[start : end], 16)
        else:
            return self.getSeq()

    def getWindow(self):
        start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH
        end = start + WINDOW_LENGTH
        return int(self.packetStr[start : end], 16)

    def getDataLength(self):
        start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH
        end = start + LENGTH_LENGTH
        return int(self.packetStr[start : end], 16)

    def getData(self):
        dataLength = self.getDataLength()
        start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH
        end = start + dataLength
        return self.packetStr[start : end]


def checkSum(aStr):
    value = 7
    for let in aStr:
        value = value * 31 + ord(let)
    value = int(value % 2 **32)
    if value > (2 ** 31) - 1:
        value -= (2 ** 31)
        return abs(-2147483648 + value)
    return abs(value)

def createPacket(packetType, ackSeqNum, data):
    packet = packetType + toHex(ackSeqNum, ACKSEQ_LENGTH) + toHex(len(data), LENGTH_LENGTH) + data
    checksum = toHex(checkSum(packet), CHECKSUM_LENGTH)
    return checksum + packet

def createStartPacket(seq, window):
    return createPacket("1", seq, toHex(window, WINDOW_LENGTH))

def createStartAckPacket(seq, window, ack):
    data = toHex(window, WINDOW_LENGTH) + toHex(ack, ACKSEQ_LENGTH)
    return createPacket("2", seq, data)

def createAckPacket(ack):
    return createPacket("3", ack, "")

def createDataPacket(seq, data):
    return createPacket("4", seq, data)

def createEndPacket(seq):
    return createPacket("5", seq, "")

def createClosePacket(seq):
    return createPacket("6", seq, "")

def createSendingPacket(seq):
    return createPacket("7", seq, "")

def toHex(val, digits):
    if val < 0:
        val *= -1
    num = hex(val)[2:]
    return (digits - len(num)) * "0" + num

