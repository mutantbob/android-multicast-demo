package com.purplefrog.linuxIPV6Multicast;


import java.io.*;
import java.net.*;
import java.nio.charset.*;

import org.apache.log4j.*;

/**
 * <p>Copyright (C) 2013 Robert Forsman, Ericsson SATV
 * $Author thoth $
 * $Date 2/4/13 $
 */
public class ExpIPV6
{
    private static final Logger logger = Logger.getLogger(ExpIPV6.class);

    public static final int port = 2624;


    public static void main(String[] argv)
        throws IOException
    {
        BasicConfigurator.configure();


        {
            final MulticastSocket server = new MulticastSocket(port);

            server.joinGroup(ssdpSiteLocalV6());

            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        receiveLoop(server);
                    } catch (IOException e) {
                        logger.error("", e);
                    }
                }
            };

            new Thread(r, "multicast receive loop").start();
        }

        System.out.println("multicast = "+ssdpSiteLocalV6().getHostAddress()+" : "+port + "\n" +
            "Type in a payload and hit [ENTER] to transmit");
        copyStdinToMulticast();
    }

    private static void copyStdinToMulticast()
        throws IOException
    {
        DatagramSocket sock = new DatagramSocket();

        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        byte[] buffer = new byte[4 << 10];
        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
        while (true) {
            String line = r.readLine();

            pkt.setAddress(ssdpSiteLocalV6());
            pkt.setPort(port);
            pkt.setData(line.getBytes("UTF-8"));
            sock.send(pkt);
        }
    }

    private static InetAddress ssdpSiteLocalV6()
        throws UnknownHostException
    {
        return InetAddress.getByName("ff05::c");
    }

    public static void receiveLoop(DatagramSocket server)
        throws IOException
    {
        byte[] buffer = new byte[4<<10];
        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);

        Charset utf8 = Charset.forName("UTF-8");

        while (true) {

            server.receive(pkt);

            InetAddress addr = pkt.getAddress();
            System.out.println("RECEIVED from "+addr.getHostAddress());

            System.out.println("\""+new String(pkt.getData(), 0, pkt.getLength(), utf8)+"\"");
        }
    }
}
