// natLib.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 04.05.2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import de.anomic.http.HttpClient;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDomains;
import de.anomic.tools.disorderHeap;
import de.anomic.tools.nxTools;

public class natLib {

    public static String getDI604(final String password) {
	// this pulls off the ip number from the DI-604 router/nat
	/*
	  wget --quiet --ignore-length http://admin:<pw>@192.168.0.1:80/status.htm > /dev/null
	  grep -A 1 "IP Address" status.htm | tail -1 | awk '{print $1}' | awk 'BEGIN{FS=">"} {print $2}'
	  rm status.htm
	*/
	try {
	    ArrayList<String> x = FileUtils.strings(HttpClient.wget("http://admin:"+password+"@192.168.0.1:80/status.htm", null, 10000), "UTF-8");
	    x = nxTools.grep(x, 1, "IP Address");
	    if ((x == null) || (x.size() == 0)) return null;
	    final String line = nxTools.tail1(x);
	    return nxTools.awk(nxTools.awk(line, " ", 1), ">", 2);
	} catch (final Exception e) {
	    return null;
	}
    }

    private static String getWhatIsMyIP() {
	try {
        ArrayList<String> x = FileUtils.strings(
        	HttpClient.wget("http://www.whatismyip.com/", null, 10000), "UTF-8");
	    x = nxTools.grep(x, 0, "Your IP is");
	    final String line = nxTools.tail1(x);
	    return nxTools.awk(line, " ", 4);
	} catch (final Exception e) {
	    return null;
	}
    }

    private static String getStanford() {
	try {
        ArrayList<String> x = FileUtils.strings(
        	HttpClient.wget("http://www.slac.stanford.edu/cgi-bin/nph-traceroute.pl", null, 10000),
        	"UTF-8");
	    x = nxTools.grep(x, 0, "firewall protecting your browser");
	    final String line = nxTools.tail1(x);
	    return nxTools.awk(line, " ", 7);
	} catch (final Exception e) {
	    return null;
	}
    }

    private static String getIPID() {
	try {
        ArrayList<String> x = FileUtils.strings(HttpClient.wget("http://ipid.shat.net/", null, 10000), "UTF-8");
	    x = nxTools.grep(x, 2, "Your IP address");
	    final String line = nxTools.tail1(x);
	    return nxTools.awk(nxTools.awk(nxTools.awk(line, " ", 5), ">", 2), "<", 1);
	} catch (final Exception e) {
	    return null;
	}
    }

    private static boolean isNotLocal(final String ip) {
	if ((ip.equals("localhost")) ||
	    (ip.startsWith("127")) ||
	    (ip.startsWith("192.168")) ||
        (ip.startsWith("172.16")) ||
	    (ip.startsWith("10."))
	    ) return false;
	return true;
    }
    
    private static boolean isIP(final String ip) {
	if (ip == null) return false;
	try {
	    /*InetAddress dummy =*/ InetAddress.getByName(ip);
	    return true;
	} catch (final Exception e) {
	    return false;
	}
    }

    //TODO: This is not IPv6 compatible
    public static boolean isProper(final String ip) {
        final plasmaSwitchboard sb=plasmaSwitchboard.getSwitchboard();
        if (sb != null) {
        	if (sb.isRobinsonMode()) return true;
            final String yacyDebugMode = sb.getConfig("yacyDebugMode", "false");
            if (yacyDebugMode.equals("true")) {
                return true;
            }
            // support for staticIP
            if (sb.getConfig("staticIP", "").equals(ip)) {
                return true;
            }
        }
        if (ip == null) return false;
        if (ip.indexOf(":") >= 0) return false; // ipv6...
        return (isNotLocal(ip)) && (isIP(ip));
    }
    
    public static final InetAddress getInetAddress(final String ip) {
        if (ip == null) return null;
        if (ip.length() < 8) return null;
        final String[] ips = ip.split("\\.");
        if (ips.length != 4) return null;
        final byte[] ipb = new byte[4];
        try {
            ipb[0] = (byte) Integer.parseInt(ips[0]);
            ipb[1] = (byte) Integer.parseInt(ips[1]);
            ipb[2] = (byte) Integer.parseInt(ips[2]);
            ipb[3] = (byte) Integer.parseInt(ips[3]);
        } catch (final NumberFormatException e) {
            return null;
        }
        try {
            return InetAddress.getByAddress(ipb);
        } catch (final UnknownHostException e) {
            return null;
        }
    }

    private static int retrieveOptions() {
	return 3;
    }
    
    private static String retrieveFrom(final int option) {
	if ((option < 0) || (option >= retrieveOptions())) return null;
	if (option == 0) return getWhatIsMyIP();
	if (option == 1) return getStanford();
	if (option == 2) return getIPID();
	return null;
    }

    public static String retrieveIP(final boolean DI604, final String password) {
	String ip;
	if (DI604) {
	    // first try the simple way...
	    ip = getDI604(password);
	    if (isProper(ip)) {
		//System.out.print("{DI604}");
		return ip;
	    }
	}

	// maybe this is a dial-up connection (or LAN and DebugMode) and we can get it from java variables
	/*InetAddress ia = serverCore.publicIP();
	if (ia != null) {
	    ip = ia.getHostAddress();
	    if (isProper(ip)) return ip;
	}*/
	ip = serverDomains.myPublicIP();
	if (isProper(ip)) return ip;

	// now go the uneasy way and ask some web responder
	final disorderHeap random = new disorderHeap(retrieveOptions());
	for (int i = 0; i < retrieveOptions(); i++) {
	    ip = retrieveFrom(random.number());
	    if (isProper(ip)) return ip;
	}
	return null;
    }

    // rDNS services:
    // http://www.xdr2.net/reverse_DNS_lookup.asp
    // http://remote.12dt.com/rns/
    // http://bl.reynolds.net.au/search/
    // http://www.declude.com/Articles.asp?ID=97
    // http://www.dnsstuff.com/
    
    // listlist: http://www.aspnetimap.com/help/welcome/dnsbl.html
    
    
    public static void main(final String[] args) {
	//System.out.println("PROBE DI604     : " + getDI604(""));
	//System.out.println("PROBE whatismyip: " + getWhatIsMyIP());
	//System.out.println("PROBE stanford  : " + getStanford());
	//System.out.println("PROBE ipid      : " + getIPID());
	//System.out.println("retrieveIP-NAT : " + retrieveIP(true,""));
	//System.out.println("retrieveIP     : " + retrieveIP(false,"12345"));

	System.out.println(isProper(args[0]) ? "yes" : "no");
    }

}
