import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Packet {
    private static final int CHECKSUM_LENGTH = 8;
    private static final int TYPE_LENGTH = 1;
    private static final int ACKSEQ_LENGTH = 8;
    public static final int DATA_LENGTH = 1024;
    private static final int LENGTH_LENGTH = 3;
    private static final int WINDOW_LENGTH = 8;
    public static final int MAX_PACKET_LENGTH = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + DATA_LENGTH + LENGTH_LENGTH;
    private static final Charset CHARSET = StandardCharsets.US_ASCII;

    private String packetStr;

    public Packet(String packetStr) {
        this.packetStr = packetStr;
    }

    public Packet(byte[] packetBytes) {
        packetStr = new String(packetBytes, CHARSET);
        packetStr = packetStr.substring(0, CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH + getDataLength());
    }

    public boolean validChecksum() {
        int checksum = Integer.parseInt(packetStr.substring(0, CHECKSUM_LENGTH), 16);
        int length = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH + getDataLength();
        String msg = packetStr.substring(CHECKSUM_LENGTH, length);
        return checksum == checkSum(msg);
    }

    public int getActualCheckSum() {
        return Integer.parseInt(packetStr.substring(0, CHECKSUM_LENGTH), 16);
    }

    public int getCalculatedCheckSum() {
        int length = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH + getDataLength();
        String msg = packetStr.substring(CHECKSUM_LENGTH, length);
        return checkSum(msg);
    }

    public String getType() {
        return packetStr.substring(CHECKSUM_LENGTH, CHECKSUM_LENGTH + TYPE_LENGTH);
    }

    public int getSeq() {
        int start = CHECKSUM_LENGTH + TYPE_LENGTH;
        int end = start + ACKSEQ_LENGTH;
        return Integer.parseInt(packetStr.substring(start, end),16);
    }

    public int getAck() {
        if ("2".equals(getType())) {
            int start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH + WINDOW_LENGTH;
            int end = start + ACKSEQ_LENGTH;
            return Integer.parseInt(packetStr.substring(start, end),16);
        } else {
            return getSeq();
        }
    }

    public int getWindow() {
        int start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH;
        int end = start + WINDOW_LENGTH;
        return Integer.parseInt(packetStr.substring(start, end),16);
    }

    public String getData() {
        int dataLength = getDataLength();
        int start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH + LENGTH_LENGTH;
        int end = start + dataLength;
        return packetStr.substring(start, end);
    }

    @Override
    public String toString() {
        return packetStr;
    }

    private int getDataLength() {
        int start = CHECKSUM_LENGTH + TYPE_LENGTH + ACKSEQ_LENGTH;
        int end = start + LENGTH_LENGTH;
        return Integer.parseInt(packetStr.substring(start, end),16);
    }

    private static byte[] createPacket(String type, int ackSeqNum, String data) {
        String ackSeq = formatToString(ackSeqNum, ACKSEQ_LENGTH);
        String length = formatToString(data.length(), LENGTH_LENGTH);
        String message = type + ackSeq + length + data;
        String check = formatToString(checkSum(message), CHECKSUM_LENGTH);
        return (check + message).getBytes();
    }

    public static byte[] createStartPacket(int seq, int window) {
        return createPacket("1", seq, formatToString(window, WINDOW_LENGTH));
    }

    public static byte[] createStartAckPacket(int seq, int window, int ack) {
        String data = formatToString(window, WINDOW_LENGTH) + formatToString(ack, ACKSEQ_LENGTH);
        return createPacket("2", seq, data);
    }

    public static byte[] createAckPacket(int ack) {
        return createPacket("3", ack, "");
    }

    public static byte[] createDataPacket(int seq, String data) {
        return createPacket("4", seq, data);
    }

    public static byte[] createEndPacket(int seq) {
        return createPacket("5", seq, "");
    }

    public static byte[] createClosePacket(int seq) {
        return createPacket("6", seq, "");
    }

    public static byte[] createSendingPacket(int seq) {
        return createPacket("7", seq, "");
    }

    private static String formatToString(int num, int length) {
        if (num < 0) {
            num = num * -1;
        }
        String ack = Integer.toHexString(num);
        return String.format("%" + Integer.toString(length) + "s", ack).replace(' ', '0');
    }

    private static int checkSum(String str) {
        int value = 7;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            value = value * 31 +  c;
        }
        return Math.abs(value);
    }

    public static void main(String[] args) {
        byte[] response = createStartPacket(12, 123);
        Packet pkt = new Packet(response);
        System.out.println(pkt.validChecksum());
        System.out.println(pkt.getType());
        System.out.println(pkt.getSeq());
        System.out.println(pkt.getAck());
        System.out.println(pkt.getWindow());
        System.out.println(pkt.getData());
    }

}
