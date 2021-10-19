package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {

    Map<MACAddress, Iface> bridgeMap = new HashMap<>();
    Map<MACAddress, Long> timeMap = new HashMap<>();

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Switch(String host, DumpFile logfile) {
        super(host, logfile);
    }

    /**
     * Update the entries in the forwarding table
     *
     * @param src
     * @param port
     */
    public void updateTable(MACAddress src, Iface port) {
        if (!bridgeMap.containsKey(src)) {
            Long time = System.nanoTime();
            bridgeMap.put(src, port);
            timeMap.put(src, time);
        }
    }

    /**
     * Timeout outdated MAC addresses
     */
    public void timeOut() {
        for (MACAddress addr: bridgeMap.keySet()) {
            if (timeMap.containsKey(addr)) {
                long duration = System.nanoTime() - timeMap.get(addr);
                if (duration > (long) 15 * 1E9) {
                    bridgeMap.remove(addr);
                    timeMap.remove(addr);
                }
            }
        }
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        // Handle packets
        timeOut();
        try {
            MACAddress sourceAddr = etherPacket.getSourceMAC();
            MACAddress destAddr = etherPacket.getDestinationMAC();
            updateTable(sourceAddr, inIface);

            // Update the info for interfaces
            Iface port = this.interfaces.get(inIface.getName());
            port.setMacAddress(sourceAddr);

            // Forward/Flood the packets
            Iface outFace;
            if (bridgeMap.containsKey(destAddr)) {  // Is address available in the forwarding table?
                // Forward frame only from the port which is connected to the
                // destination address
                outFace = bridgeMap.get(destAddr);
                assert (sendPacket(etherPacket, outFace));
            } else {
                // Forward frame from all ports except one on which the frame
                // has arrived
                for (String name : this.interfaces.keySet()) {
                    if (!name.equals(inIface.getName())) {
                        outFace = this.interfaces.get(name);
                        assert (sendPacket(etherPacket, outFace));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
