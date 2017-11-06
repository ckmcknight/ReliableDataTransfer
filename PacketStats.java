import java.util.*;

public class PacketStats {

	private long time;
	private byte[] data;
	private boolean isAcked;

	public PacketStats () {}

	public PacketStats (long  time, byte[] data, boolean isAcked) {
		this.time = time;
		this.data = data;
		this.isAcked = isAcked;
	}

	public long getTime () {
		return time;
	}
	public void setTime(long newTime) {
		this.time = newTime;
	}

	public byte[] getData () {
		return data;
	}

	public boolean isAcked () {
		return isAcked;
	}

	public void setAcked (boolean ack) {
		this.isAcked = ack;
	}



}