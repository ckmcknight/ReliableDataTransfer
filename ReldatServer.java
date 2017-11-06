import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

public class ReldatServer {

    private InetAddress serverIp;
    private int serverPort;
    private InetAddress clientIP;
    private int clientPort;

    private DatagramSocket socket;
    private boolean activeConnection;
    private int timeout = 8000;
    private static final int NUM_RETRIES = 10;
    private byte[] recvBuf = new byte[Packet.MAX_PACKET_LENGTH];

    private int seq;
    private int clientWindowSize;
    private int serverWindowSize = 10;
    //private SelectiveRepeat srListen;
    //private SelectiveRepeat srSend;



    public ReldatServer(int serverPort, int window) {
        this.serverWindowSize = window;
        this.serverPort = serverPort;
        this.activeConnection = false;
    }

    public boolean startUpServer() {
        try {
            socket = new DatagramSocket(serverPort);
            socket.setSoTimeout(timeout);
            return true;
        } catch (SocketException ex) {
            System.out.println("UDP Port " + socket + " is occupied.");
            return false;
        }
    }

    public void waitForConnection() {
        boolean notConnected = true;
        while (notConnected) {
            boolean waiting = true;
            int ack = -1;
            int tempClientPort = -1;
            InetAddress tempClientAddress = null;
            DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
            while (waiting) {
                System.out.println("Listening for Connection Requests");
                try {
                    socket.receive(receivePacket);
                    Packet p = new Packet(recvBuf);
                    if (p.validChecksum() && p.getType().equals("1")) {
                        System.out.println("Received Valid Connection Request");
                        waiting = false;
                        clientWindowSize = p.getWindow();
                        ack = p.getSeq();
                        tempClientPort = receivePacket.getPort();
                        tempClientAddress = receivePacket.getAddress();
                    } else {
                        System.out.println("Received Invalid Connection Request");
                    }
                } catch (SocketException ex) {
                    System.out.println("No connections detected");
                } catch (IOException ex) {
                    System.out.println(ex);
                }
            }
            seq = (new Random()).nextInt(2 << 10);

            byte[] startAck = Packet.createStartAckPacket(seq, serverWindowSize, ack);
            DatagramPacket startAckPacket = new DatagramPacket(startAck, startAck.length);
            startAckPacket.setPort(tempClientPort);
            startAckPacket.setAddress(tempClientAddress);

            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            for (int i = 0; i < NUM_RETRIES; i++) {
                try {
                    System.out.println("Sending StartAck Packet");
                    socket.send(startAckPacket);
                } catch (IOException e) {
                    System.out.println(e);
                }
                try {
                    System.out.println("Waiting for StartAck Acknowledgement");
                    socket.receive(recvPacket);
                    Packet packet = new Packet(recvBuf);
                    if (packet.validChecksum() && packet.getType().equals("3")
                            && packet.getAck() == seq
                            && recvPacket.getPort() == tempClientPort
                            && tempClientAddress.equals(recvPacket.getAddress())) {
                        System.out.println("Received Acknowledgement of StartAck");
                        notConnected = false;
                        clientIP = tempClientAddress;
                        clientPort = tempClientPort;
                        activeConnection = true;
                        seq++;
                        break;
                    } else {
                        System.out.println("Received Invalid Acknowledgement of StartAck");
                        System.out.println(packet.validChecksum());
                        System.out.println(packet.getType());
                        System.out.println(packet.getAck());
                        System.out.println(seq);
                    }
                } catch (SocketException ex) {
                    System.out.println("No connections detected");
                } catch (IOException ex) {
                    System.out.println(ex);
                }
            }
        }
        System.out.println("Connection Started");
    }

    private void mainLoop() {
        while (true) {
            waitForConnection();
            boolean remainConnected = true;
            while (remainConnected) {
                String next = receiveInstruction();
                if (next.equals("6")) {
                    //Close case
                    System.out.println("Received Close Connection: Closing Connection");
                    tearDownConnection();
                    remainConnected = false;

                } else if (next.equals("7")) {
                    //Transform Task
                    System.out.println("Client Asked To Transform");
                    // Get File From Client
                    SelectiveRepeat srListen = new SelectiveRepeat(socket, clientPort, clientIP, clientWindowSize);
                    String file = srListen.runListener(); // Get from Selective Repeat
                    if (file == null) {
                        System.out.println("Error While Receiving File: Closing Connection");
                        tearDownConnection();
                        remainConnected = false;
                    } else {
                        System.out.println("Received File: Transforming");
                        file = file.toUpperCase();
                        System.out.println(file);
                        System.out.println("Transformed: Sending back to Client");
                        SelectiveRepeat srSend = new SelectiveRepeat(socket, clientPort, clientIP, file, clientWindowSize);
                        boolean sentCorrectly = srSend.mainSendModeLoop(); // Get from selective repeat
                        if (sentCorrectly) {
                            System.out.println("Sent Transformed File Succesfully");
                            System.out.println("Waiting for next instruction");
                        } else {
                            System.out.println("Failed to Send Correctly: Closing Connection");
                            tearDownConnection();
                            remainConnected = false;
                        }
                    }

                } else if (next.equals("-1")) {
                    System.out.println("Didn't Receive Packet: Closing Connection");
                    tearDownConnection();
                    remainConnected = false;
                }
            }
        }
    }

    private void tearDownConnection() {
        clientIP = null;
        clientPort = -1;
        activeConnection = false;
    }

    private boolean sendAck(int ack) {
        byte[] msg = Packet.createAckPacket(ack);
        DatagramPacket sendPacket = new DatagramPacket(msg, msg.length);
        sendPacket.setPort(clientPort);
        sendPacket.setAddress(clientIP);
        try {
            socket.send(sendPacket);
            return true;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private String receiveInstruction() {
        DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
        int excepts = 0;
        while (excepts < NUM_RETRIES) {
            try {
                System.out.println("Waiting to received next mode");
                socket.receive(recvPacket);
                Packet p = new Packet(recvBuf);
                if (p.validChecksum() && (p.getType().equals("6") || p.getType().equals("7"))) {
                    System.out.println("Received next mode");
                    sendAck(p.getSeq());
                    return p.getType();
                } else {
                    System.out.println("Received invalid packet (for next mode)");
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                excepts++;
            } catch (Exception e) {
                System.out.println(e.getMessage());
                excepts++;
            }
        }
        return "-1";
    }

    public static void main(String[] args) {
        ReldatServer server;
        if (args.length != 2) {
            System.out.println("ERROR: Incorrect number of arguments, starting with default arguments");
            server = new ReldatServer(4567, 100);
        } else {
            String port = args[0];
            String window = args[1];
            System.out.println("port: " + port);
            System.out.println("window: " + window);
            server = new ReldatServer(Integer.parseInt(port), Integer.parseInt(window));
        }
        boolean activeServer = server.startUpServer();
        if (activeServer) {
            server.mainLoop();
        }
    }
}
