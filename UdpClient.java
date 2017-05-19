import java.nio.ByteBuffer;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class UdpClient {

	private int dataSize = 4;
	private int version = 4;
	private int hlen = 5;
	private long totalRTT = 0;

	// Header fields
	private short versionHlenAndTos = 0x4500;
	private short length = (short) ((version * hlen) + dataSize);
	private short ident = 0;
	private short flagsAndOffset = 0x4000; // no fragmentation
	private byte ttl = 50;
	private byte protocol = 17; // UDP
	private short checksum = 0;// header only
	private int sourceAddr = 0; // IP address of my choice
	byte[] destAddr; // IP address of server
	// Ignoring Options/Pad

	private short destPort;

	public static void main(String[] args) {
		UdpClient udp = new UdpClient();
		try {
			Socket socket = new Socket("codebank.xyz", 38005);
			udp.setDestAddr(socket.getInetAddress().getAddress());

			// Handshake
			byte[] data = new byte[udp.getDataSize()];
			data[0] = (byte) 0xDE;
			data[1] = (byte) 0xAD;
			data[2] = (byte) 0xBE;
			data[3] = (byte) 0xEF;

			socket.getOutputStream().write(udp.getIpv4Packet(data));

			// response
			int response = socket.getInputStream().read();
			for (int j = 0; j < 3; j++) {
				response <<= 8;
				response |= socket.getInputStream().read();
			}

			System.out.println(String.format("Handshake response: 0x%X\n", response));

			// port number
			udp.setDestPort((short) socket.getInputStream().read());
			udp.setDestPort((short) (udp.getDestPort() << 8));
			udp.setDestPort((short) (udp.getDestPort() | socket.getInputStream().read()));

			System.out.println("Port number received: " + response);

			// Send packets
			udp.setDataSize(2);
			for (int i = 0; i < 12; ++i) {
				System.out.println("Sending packets with " + udp.getDataSize() + " bytes of data");
				udp.fillData();

				byte[] packet = udp.getIpv4Packet(udp.getUdpPacket());
				long startTime = System.nanoTime();
				socket.getOutputStream().write(packet);

				response = socket.getInputStream().read();
				for (int j = 0; j < 3; j++) {
					response <<= 8;
					response |= socket.getInputStream().read();
				}

				long endTime = System.nanoTime();

				long totalTime = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.MILLISECONDS);

				udp.setTotalRTT(totalTime);

				System.out.println(String.format("Response: 0x%X\n", response));
				System.out.println("RTT: " + totalTime + "ms");

				udp.setDataSize(udp.getDataSize() * 2);
			}

			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Average RTT: " + (udp.getTotalRTT() / 12) + "ms");

	}

	public byte[] fillData() {

		byte[] data = new byte[dataSize];

		for (int i = 0; i < dataSize; i++) {
			data[i] = 0;
		}
		return data;
	}

	public byte[] getIpv4Packet(byte[] data) {

		// 8 from UDP header size
		length = (short) ((version * hlen) + (8 + dataSize));

		// Checksum
		checksum = 0;
		ByteBuffer bb = ByteBuffer.allocate(20 + dataSize);
		bb.putShort(versionHlenAndTos);
		bb.putShort(length);
		bb.putShort(ident);
		bb.putShort(flagsAndOffset);
		bb.put(ttl);
		bb.put(protocol);
		bb.putShort(checksum);
		bb.putInt(sourceAddr);
		bb.put(destAddr);
		bb.put(data);

		checksum = checksum(bb.array());

		// Create Packet
		bb.clear();
		bb.putShort(versionHlenAndTos);
		bb.putShort(length);
		bb.putShort(ident);
		bb.putShort(flagsAndOffset);
		bb.put(ttl);
		bb.put(protocol);
		bb.putShort(checksum);
		bb.putInt(sourceAddr);
		bb.put(destAddr);
		bb.put(data);

		return bb.array();
	}

	public byte[] getUdpPacket() {

		byte[] data = new byte[8 + dataSize];

		byte[] srcPort = new byte[2];
		Random rand = new Random();
		rand.nextBytes(srcPort);

		// Checksum
		checksum = 0;
		ByteBuffer bb = ByteBuffer.allocate(8 + dataSize);
		bb.put(srcPort);
		bb.putShort(destPort);
		bb.putShort((short) (8 + dataSize));
		bb.putShort(checksum);
		bb.put(data);

		checksum = checksum(bb.array());

		// Create Packet
		bb.clear();
		bb.put(srcPort);
		bb.putShort(destPort);
		bb.putShort((short) (8 + dataSize));
		bb.putShort(checksum);
		bb.put(data);

		return data;
	}

	// Includes: UDP header, data, and a "pseudoheader" that includes
	// part of the Ipv4 header
	public short checksum(byte[] b) {
		int sum = 0;
		int length = b.length;
		int i = 0;

		while (length > 1) {
			int s = ((b[i++] << 8) & 0xFF00) | (b[i++] & 0x00FF);
			sum += s;
			if ((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
			length -= 2;
		}

		return (short) ~(sum & 0xFFFF);
	}

	public int getDataSize() {
		return dataSize;
	}

	public int getVersion() {
		return version;
	}

	public int getHLen() {
		return hlen;
	}

	public short getDestPort() {
		return destPort;
	}

	public long getTotalRTT() {
		return totalRTT;
	}

	public void setLength(short l) {
		length = l;
	}

	public void setDataSize(int d) {
		dataSize = d;
	}

	public void setDestAddr(byte[] b) {
		destAddr = b;
	}

	public void setDestPort(short p) {
		destPort = p;
	}

	public void setTotalRTT(long t) {
		totalRTT += t;
	}

}
