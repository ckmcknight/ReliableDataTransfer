import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.*;
import java.lang.System;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.io.*;
import java.net.*;


class SelectiveRepeat {

    public int startSeq = (new Random()).nextInt(2 << 10);
    public boolean not_all_acked = true;
    public boolean notDead = true;
    private final Lock lock = new ReentrantLock();
    public final int RTT_CLOCK = 2; //<- generic #
    private final int MAX_RETRIES = 10;
    public PacketStats packetStats = new PacketStats();
    public HashMap <Integer, PacketStats> lookupTable = new HashMap<>();
    private static LinkedList<Integer> sentPacketList = new LinkedList<>();
    private byte[] recvBuf = new byte[Packet.MAX_PACKET_LENGTH];
    private DatagramSocket serverSocket;
    private int clientPort;
    private InetAddress clientAddress;
    private String sendFile;
    private ListenAckMode ackListen;
    private SendMode sending;
    private int windowSize;


    //Call this for Listen Mode
    public SelectiveRepeat (DatagramSocket serverSocket, int clientPort, InetAddress clientAddress, int windowSize) {
        this.serverSocket = serverSocket;
        this.clientPort = clientPort;
        this.clientAddress = clientAddress;
    }

    // Call this for Send Mode
    public SelectiveRepeat (DatagramSocket serverSocket, int clientPort, InetAddress clientAddress, String sendFile, int windowSize) {
        this.serverSocket = serverSocket;
        this.clientPort = clientPort;
        this.clientAddress = clientAddress;
        this.sendFile = sendFile;
        this.windowSize = windowSize;
        ackListen = new ListenAckMode("ackListenThread");
        sending = new SendMode("sendingThread");
    }




    /**
    * mainSendModeLoop: Starts the multiThreaded Sending mode.
    * @return boolean. true if file successfully sent, false otherwise.
    **/
    public boolean mainSendModeLoop() {
        boolean file_sent = false;
        System.out.println("Send File Mode . . .");
        ListenAckMode ackListen = new ListenAckMode("Listen");
        SendMode sending = new SendMode("Sending");
        Thread t1 = ackListen.start();
        Thread t2 = sending.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }
        file_sent = ackListen.getResult();
        if (!file_sent) {
            return false;
        } else {
            return sendEndPacket((new Random()).nextInt(2 << 10));
        }
    }


    /**
    * runListener: receives the file from the client, and returns a string of all packets concatinated together
    **/
    public String runListener() {
        Map<Integer, Packet> packetMap = new HashMap<Integer, Packet>();
        LinkedList<Packet> packetList;
        System.out.println("Listening");
        int excepts = 0;
        while (true) {
        	if (excepts > MAX_RETRIES) {
        		System.out.println("Retried Max Number of Times");
        		return null;
        	}
            try {
                // receive
                DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                serverSocket.receive(receivePacket);
                excepts = 0;
                Packet p = new Packet(recvBuf);
                System.out.println("SERVER: Received Packet");
                byte[] ackPacket = new byte[Packet.MAX_PACKET_LENGTH];

                if (p.validChecksum() && p.getType().equals("4")) {
                    System.out.println("SERVER: packet is valid");
                    packetMap.put(p.getSeq(), p);
                    ackPacket = Packet.createAckPacket(p.getSeq());
                    DatagramPacket responsePacket = new DatagramPacket(ackPacket, ackPacket.length);
                    responsePacket.setPort(clientPort);
                    responsePacket.setAddress(clientAddress);
                    System.out.println("SERVER: Sending Ack");
                    serverSocket.send(responsePacket);

                } else if (p.validChecksum() && p.getType().equals("5")) { // end packet
                    System.out.println("SERVER: All packets have been received.");
                    packetList = new LinkedList<Packet>(packetMap.values());
                    sortPacketList(packetList);
                    ackPacket = Packet.createAckPacket(p.getSeq());
                    DatagramPacket responsePacket = new DatagramPacket(ackPacket, ackPacket.length);
                    responsePacket.setPort(clientPort);
                    responsePacket.setAddress(clientAddress);
                    System.out.println("SERVER: Sending End Ack");
                    serverSocket.send(responsePacket);
                    break;

                } else if (p.validChecksum() && p.getType().equals("7")) {
                	ackPacket = Packet.createAckPacket(p.getSeq());
                    DatagramPacket responsePacket = new DatagramPacket(ackPacket, ackPacket.length);
                    responsePacket.setPort(clientPort);
                    responsePacket.setAddress(clientAddress);
                    System.out.println("SERVER: Sending Sending Ack");
                    serverSocket.send(responsePacket);
                } else { // sending previous ack to let the client know to resend the corrupted packet
                    System.out.println("SERVER: The packet was corrupted");
                    System.out.println(p.getActualCheckSum());
                    System.out.println(p.getCalculatedCheckSum());
                    System.out.println(p);
                }

                // DatagramPacket responsePacket = new DatagramPacket(ackPacket, ackPacket.length);
                // responsePacket.setPort(clientPort);
                // responsePacket.setAddress(clientAddress);
                // System.out.println("Sending Ack");
                // serverSocket.send(responsePacket);

            } catch (RuntimeException e) {
                System.out.println("SERVER: my thread interrupted");
                System.out.println(e);
                excepts++;
            } catch (IOException e) {
                System.out.println(e);
                excepts++;
            }
        }
        System.out.println("Listen_Mode IS DONE!");

        String result = "";
        for (Packet p : packetList) {
            result += "" + p.getData();
        }

        return result;
    }

    public boolean sendEndPacket(int seq) {
        System.out.println("Sending End Packet");
        byte[] endPacket = Packet.createEndPacket(seq);
        int retries = 0;
        while(retries < MAX_RETRIES) {
            try {
                DatagramPacket packet = new DatagramPacket(endPacket, endPacket.length);
                packet.setPort(clientPort);
                packet.setAddress(clientAddress);
                serverSocket.send(packet);
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }

            try {
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                serverSocket.receive(packet);
                Packet p = new Packet(recvBuf);
                if (p.validChecksum() && p.getType().equals("3") && p.getAck() == seq) {
                    System.out.println("Received Valid EndAck");
                    return true;
                } else if (p.validChecksum()) {
                    System.out.println("Expected EndAck received another packet");
                    retries = 0;
                } else {
                    System.out.println("Received Corrupt Packet while expecting EndAck");
                    retries = 0;
                }
            } catch (SocketTimeoutException e) {
                System.out.println(e.getMessage());
                retries++;
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return false;

    }

    /**
    * sortPacketList: sorts the list of packets based on the sequence number of each packet
    **/
    public void sortPacketList(LinkedList<Packet> packetList) {
        System.out.println("Sort the List . . .");

        Collections.sort(packetList, new Comparator<Packet>(){
            @Override
            public int compare(Packet p1, Packet p2) {
                return p1.getSeq() - p2.getSeq();
            }
        });
    }
//}

//******************************** beginning of the two threaded classes**********************************
    class SendMode implements Runnable {
        private Thread t;
        private String threadName;

        public SendMode (String threadName) {
            this.threadName = threadName;
        }

        public void run () {
            System.out.println("In the " + threadName);
            LinkedList<String> dataList = file_to_LinkedList(sendFile);
            System.out.println("trying to send");
            int oldestPacket = startSeq;
            int newestPacket = oldestPacket;
            lock.lock();
            while (not_all_acked && notDead) {
                lock.unlock();

                lock.lock();
                if (dataList.isEmpty() && sentPacketList.isEmpty()) {
                    System.out.println("Everything sent successfully");
                    not_all_acked = false;
                    break;
                }
                lock.unlock();

                lock.lock();
                if (lookupTable.containsKey(oldestPacket) && lookupTable.get(oldestPacket).isAcked()) {
                    oldestPacket++;
                    continue;
                }
                lock.unlock();

                if (newestPacket - oldestPacket < windowSize && !dataList.isEmpty()) {
                    lock.lock();
                    System.out.print("Sending new Packet: ");
                    System.out.println(newestPacket);
                    sendNextPacket(newestPacket, dataList);
                    newestPacket++;
                    lock.unlock();
                }

                long currTime = System.currentTimeMillis();
                currTime = TimeUnit.MILLISECONDS.toSeconds(currTime);
                lock.lock();
                if(!sentPacketList.isEmpty()) {
                    PacketStats statPack = lookupTable.get(sentPacketList.get(0));//checking the oldest packet time
                    if (currTime - statPack.getTime() > RTT_CLOCK) {
                        System.out.print("Resending Packet: ");
                        System.out.println(new Packet(statPack.getData()).getSeq());
                        resendOldestPacket(sentPacketList);
                    }

                }
                lock.unlock();
                lock.lock();
            }
            lock.unlock();
            System.out.println("Ending Send Thread");
        }

        public Thread start () {
            if (t == null) {
                 t = new Thread (this, threadName);
                 t.start ();
            }
            return t;
        }


        public void sendNextPacket (int seq, List<String> packetArray) {
            byte[] pkt = Packet.createDataPacket(seq, packetArray.remove(0));
            try {
                DatagramPacket packet = createPacket(pkt);
                serverSocket.send(packet);
                long currentTime = System.currentTimeMillis();
                currentTime = TimeUnit.MILLISECONDS.toSeconds(currentTime);
                packetStats = new PacketStats(currentTime, pkt, false);
                lookupTable.put(seq, packetStats);
                sentPacketList.add(seq);
            } catch (IOException e) {
                System.out.println(e);
            } catch (NullPointerException e) {
                    System.out.println(e);
            }
        }

        public void resendOldestPacket (LinkedList<Integer> packetList) {
            int seq = packetList.remove(0);
            PacketStats p = lookupTable.get(seq);
            long currTime = System.currentTimeMillis();
            currTime = TimeUnit.MILLISECONDS.toSeconds(currTime);
            p.setTime(currTime);
            byte[] pkt = p.getData();
            packetList.add(seq);
            try {
                DatagramPacket packet = createPacket(pkt);
                serverSocket.send(packet);
            } catch (IOException e) {
                System.out.println(e);
            } catch (NullPointerException e) {
                    System.out.println(e);
            }
        }

        public DatagramPacket createPacket(byte[] pkt) {
            DatagramPacket packet = new DatagramPacket(pkt, pkt.length);
            packet.setPort(clientPort);
            packet.setAddress(clientAddress);
            return packet;
        }

        public LinkedList<String> file_to_LinkedList (String file) {
            LinkedList<String> packetList = new LinkedList<>();
            String p = "";
            int index = 0;
            while (index < file.length()) {
                packetList.add(file.substring(index, Math.min(index + Packet.DATA_LENGTH, file.length())));
                index += Packet.DATA_LENGTH;
            }
            System.out.println("SERVER: got FILE");
            return packetList;
        }
    }


    class ListenAckMode implements Runnable {
        private Thread t;
        private String threadName;


        public ListenAckMode (String threadName) {
            this.threadName = threadName;
        }

        public void run () {
            System.out.println("SERVER: In the " + threadName);
            LinkedList<String> packetList = file_to_LinkedList(sendFile);
            int unreceivedCount = 0;
            lock.lock(); // sets to true
            while (not_all_acked && notDead) {
                lock.unlock();
                if (unreceivedCount > MAX_RETRIES) {
                    lock.lock();
                    notDead = false;
                    lock.unlock();
                    break;
                }
                try {
                    DatagramPacket recvAck = new DatagramPacket(recvBuf, recvBuf.length);
                    serverSocket.receive(recvAck);
                    Packet a = new Packet(recvBuf);
                    unreceivedCount = 0;
                    if (a.validChecksum()) {

                        if (a.getType().equals("2")) {
                            System.out.println("start ACK");

                        } else if (a.getType().equals("3")) {
                            // ACK packet
                            System.out.println("Received ACK");
                            int seq = a.getAck();
                            lock.lock();
                            PacketStats pkt = lookupTable.get(seq);
                            pkt.setAcked(true);
                            sentPacketList.remove(sentPacketList.indexOf(seq));
                            lock.unlock();
                        }
                        if (a.getType().equals("5")) {
                            System.out.println("Received End Packet");
                            int ackNum = a.getSeq();
                            byte[] ack = Packet.createAckPacket(ackNum);
                            DatagramPacket responsePacket = new DatagramPacket(ack, ack.length);
		                    responsePacket.setPort(clientPort);
		                    responsePacket.setAddress(clientAddress);
		                    System.out.println("SERVER: Sending End Ack");
		                    serverSocket.send(responsePacket);
                        }

                    }
                } catch (SocketTimeoutException e) {
                    System.out.println(e.getMessage());
                    System.out.println(unreceivedCount);
                    unreceivedCount++;
                } catch (IOException e) {
                    System.out.println(e);
                }
                lock.lock();
            }
            try {
            	lock.unlock();
            } catch(IllegalMonitorStateException e) {
	        	System.out.println("Released unlocked lock");
            }
            System.out.println("Ending Listen Thread");
        }

        public Thread start () {
            if (t == null) {
                 t = new Thread (this, threadName);
                 t.start ();
            }
            return t;
        }

        public boolean getResult() {
            return !not_all_acked;
        }

        public LinkedList<String> file_to_LinkedList (String file) {
            LinkedList<String> packetList = new LinkedList<>();
            String p = "";
            int index = 0;
            try {
                while (index < file.length()) {
                    packetList.add(file.substring(index, Math.min(index + Packet.DATA_LENGTH, file.length())));
                    index += Packet.DATA_LENGTH;
                }
            } catch (NullPointerException e) {
                    System.out.println(e);
            }
            return packetList;
        }
    }
//******************************** end of the two threaded classes**********************************

}

// public class SelectiveRepeat {

// 	public static void main (String args[]) {
// 		System.out.println("Testing . . . ");
// 		SelectiveRepeatDemo sr = new SelectiveRepeatDemo();
// 		sr.mainSendModeLoop();
// 	}
// }



