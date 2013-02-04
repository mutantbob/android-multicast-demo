package com.purplefrog.multicastExp;

import android.app.Activity;
import android.content.*;
import android.net.wifi.*;
import android.os.Bundle;
import android.util.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity
{
    private static final int PORT = 2624;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private EditText messageWidget;

    MulticastSocket receiveSock;
    DatagramSocket transmitSock;
    private WifiManager.MulticastLock mLock;
    private TextView logWidget;
    protected StringBuilder logText = new StringBuilder();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        messageWidget = (EditText) findViewById(R.id.message);
        logWidget = (TextView) findViewById(R.id.log);

        Button b = (Button) findViewById(R.id.transmit);
        b.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View view)
            {
                clickedTransmit();
            }
        });

        try {
            transmitSock = new DatagramSocket();

            //

            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            mLock = wifi.createMulticastLock("pseudo-ssdp");
            mLock.acquire();
            receiveSock = makeMulticastListenSocket();
            receiveSock.joinGroup(ssdpSiteLocalV6());

            Thread t = new Thread(new ReceiveTask(), "multicast listener");
            t.start();

        } catch (IOException e) {
            appendToLog(stringStackTrace(e));
        }
    }

    private MulticastSocket makeMulticastListenSocket()
        throws IOException
    {
        if (true){
            MulticastSocket rval = new MulticastSocket(PORT);
            NetworkInterface nif = NetworkInterface.getByName("wlan0");
            if (null != nif) {
                Log.d(LOG_TAG, "picking interface "+nif.getName()+" for transmit");
                rval.setNetworkInterface(nif);
            }
            return rval; // this should have worked
        }

        InetAddress addr = pickWlan0IPV6();
        Log.d(LOG_TAG, "binding to "+addr.getHostAddress());

        return new MulticastSocket(new InetSocketAddress( addr, PORT));
    }

    private InetAddress pickWlan0IPV6()
        throws SocketException
    {
        NetworkInterface nif = NetworkInterface.getByName("wlan0");

        InetAddress addr=null;
        Enumeration<InetAddress> en = nif.getInetAddresses();
        while (en.hasMoreElements()) {
            InetAddress x = en.nextElement();
            if (x instanceof Inet6Address)
                addr = x;
            else if(addr==null)
                addr = x;
        }
        return addr;
    }

    private void appendToLog(String text)
    {
        logText.append(text);

        Runnable r = new Runnable()
        {
            public void run()
            {
                logWidget.setText(logText);
            }
        };
        runOnUiThread(r);
    }

    public static String stringStackTrace(Throwable e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        return sw.toString();
    }

    @Override
    protected void onDestroy()
    {
        receiveSock.close();
        transmitSock.close();
        mLock.release();

        super.onDestroy();
    }

    public static InetAddress ssdpSiteLocalV6()
        throws UnknownHostException
    {
        return InetAddress.getByName("FF05::c");
    }

    public void clickedTransmit()
    {
        final String msg = messageWidget.getText().toString();

        Runnable r = new Runnable()
        {
            public void run()
            {
                try {
                    byte[] bytes = msg.getBytes("UTF-8");
                    transmitSock.send(new DatagramPacket(bytes, bytes.length, ssdpSiteLocalV6(), PORT));
                    Log.d(LOG_TAG, "transmitted "+bytes.length+" bytes");
                } catch (IOException e) {
                    Log.w(LOG_TAG, "", e);
                }
            }
        };
        new Thread(r, "transmit").start();
    }

    private class ReceiveTask
        implements Runnable
    {
        public void run()
        {
            Log.d(LOG_TAG, "listening for packets");

            byte[] buffer = new byte[4<<10];
            DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);

            try {
                while (true) {
                    receiveSock .receive(pkt);

                    Log.d(LOG_TAG, "received "+pkt.getLength()+" bytes");

                    String msg = pkt.getAddress().getHostAddress() + " : " + pkt.getPort() + "\n"
                        + new String(pkt.getData(), 0, pkt.getLength(), "UTF-8")+"\n";

                    appendToLog(msg);
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "multicast receive thread malfunction", e);
            }
        }
    }
}
