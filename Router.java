package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */

		// --- not IPv4 --- 
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- not IPv4 --- 

		// --- check checksum ---
		// TODO: boolean isValidChecksum = false;
		IPv4 packet = (IPv4) etherPacket.getPayload();
		int count = 0;
		ByteBuffer bb = ByteBuffer.wrap(packet.serialize());
		bb.rewind();
		for (int i = 0; i < packet.getHeaderLength() * 2; ++i) {
			short part = bb.getShort();
			if (i != 5) // skip the checksum
				count += 0xffff & part;
		}
		count = ((count >> 16) & 0xffff)
				+ (count & 0xffff);
		short checksum = (short) (~count & 0xffff);
		
		// TODO: isValidChecksum = (checksum == packet.getChecksum());

		if (checksum != packet.getChecksum()) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- check checksum ---

		// --- check ttl ---
		int newTtl = packet.getTtl() - 1;
		if (newTtl <= 0) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		packet.setTtl((byte)newTtl);
		// --- check ttl ---

		// --- check if the packet is interface bound ---
		boolean isBound = false;
		for (Map.Entry<String, Iface> iface : interfaces.entrySet()) {
			if (iface.getValue().getIpAddress() == packet.getDestinationAddress())
				isBound = true;
		}
		if (isBound) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- check if the packet is interface bound ---

		// --- check dest addr ---
		RouteEntry target = routeTable.lookup(packet.getDestinationAddress());
		if (target == null) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- check dest addr ---

		// --- send and recieve by the same interface ---
		if(inIface == target.getInterface()) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- send and recieve by the same interface ---

		// --- check out addr ---
		int out_addr = target.getInterface().getIpAddress();
		// TODO: System.out.printf("Lookup outgoing MAC from: %08X\n", out_addr);
		ArpEntry sourceArp = arpCache.lookup(out_addr);
		if (sourceArp == null) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		// --- check out addr ---

		// --- check arp ---
		int dest_addr = target.getGatewayAddress();
		if (dest_addr == 0) {
			dest_addrs = packet.getDestinationAddress();
			// TODO: System.out.printf("Final Jump To: %08X\n", destinationAddress);
		}
		ArpEntry dest_Arp = arpCache.lookup(dest_addr);
		if (dest_Arp == null) {
			System.out.println("Fail: Packet Dropped");
			return;
		}
		etherPacket.setDestinationMACAddress(dest_Arp.getMac().toBytes());
		// --- check arp ---

		// TODO: System.out.println("Setting source to: " + sourceArp.getMac());
		etherPacket.setSourceMACAddress(sourceArp.getMac().toBytes());
		// TODO: System.out.println("Destination MAC is: " + etherPacket.getDestinationMAC());

		// See IPV4 ln 285
		packet.resetChecksum();

		// TODO: System.out.println("Sending packet out iFace: " + target.getInterface());
		sendPacket(etherPacket, target.getInterface());

		/********************************************************************/
	}

}