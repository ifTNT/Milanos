package com.github.nukcsie110.milanos.entrypoint;

import com.github.nukcsie110.milanos.common.*;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.io.*;
import java.util.Iterator;
import java.util.Set;

public class main {
    private static String HS_address = "127.0.0.1";
    private static int HS_port = 8500;
    private static ArrayList<RelayInfo> relay_list = new ArrayList<RelayInfo>();
    public static int DEFAULT_PORT = 8591;    //設定初始自己的port

    public static void main(String[] args) throws IOException {
        RelayInfo test_relay = new RelayInfo();
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLocalHost(),90);
        test_relay.address = sa;
        relay_list.add(test_relay);
        //run_Entry_server(DEFAULT_PORT);
        new main();
    }

    private static byte[] generateByteSeed(int size) {
        Random random = new Random();
        byte[] rtVal = new byte[size];
        random.nextBytes(rtVal);
        return rtVal;
    }

    private static void HS_get_relay_list() throws IOException {      //CONNECTION TO heartbeat
        try {
            Socket HS_socket = new Socket(HS_address, HS_port);
            HS_socket.setSoTimeout(5000);


            ObjectInputStream in = new ObjectInputStream(HS_socket.getInputStream());
            relay_list = (ArrayList<RelayInfo>) in.readObject();

            HS_socket.close();

        } catch (IOException | ClassNotFoundException ex) {
            System.out.println(ex);
        }
    }

    private static class CHAAAAA {
        private static int[] rand_array = new int[3];    //決定要走哪三個relay
        private static byte[] CID = new byte[16];
        private static byte[] TTL = new byte[1];


        public static int get_three(){
            return  rand_array[2];
        }
        public static ByteBuffer make_header(byte[]  adr,byte[] port){
            ByteBuffer header = ByteBuffer.allocate(23);
            header.put(CID);
            System.out.println(header.remaining());
            header.put(adr);
            System.out.println(header.remaining());
            header.put(port);
            System.out.println(header.remaining());
            header.put(TTL);
            System.out.println(header.remaining());
            header.flip();
//            System.arraycopy(CID, 0, header, 0, CID.length);
//            System.arraycopy(adr, 0, header, 16, adr.length);
//            System.arraycopy(port, 0, header, 20, port.length);
//            System.arraycopy(TTL, 0, header, 22, TTL.length);
            return  header;
//            byte[] temp = new byte[23+payload.length];
//            System.arraycopy(header, 0, temp, 0, header.length);
//            System.arraycopy(payload, 0, temp, 185, payload.length);
//            return temp;
        }


        public static ByteBuffer CH_AND_PA(InetAddress Des_IP,short d_port, byte[] payload) {
            ByteBuffer output = ByteBuffer.allocate(1024);
            byte[] d_b_address = Des_IP.getAddress();
            byte[] d_b_port = ByteBuffer.allocate(2).putShort(d_port).array();
            output = make_header(d_b_address,d_b_port);    //第一層
            ArrayList<byte[]> SEK_list = new ArrayList<byte[]>(3);    //存放的3把key
            //生random出來
            int now_size = relay_list.size();
            for (int a = 0; a < rand_array.length; ++a) {    //生3個random出來
                rand_array[a] = (int) (Math.random() * now_size);
            }
            for (int i = 0; i < 3; i++) {    //生成三把SEK
                byte[] sek = generateByteSeed(256);
                SEK_list.add(i, sek);
            }
            //開始包header
            for (int i = 0; i < 2; i++) {
                RelayInfo temp = relay_list.get(rand_array[i]);
                System.out.println(temp);
                short port = (short)temp.address.getPort();
                InetAddress t_addr = temp.address.getAddress();

                System.out.println(t_addr);
                byte[] b_address = t_addr.getAddress();    //IPv4 32-bits
                byte[] b_port = ByteBuffer.allocate(2).putShort(port).array();    //port 16-bytes
                output = make_header(b_address,b_port);
                //call詠翔function

            }
            return output;
        }
    }




    private static void run_Entry_server(int port) throws IOException {    //啟動server
        System.out.println("Listening for connections on port " + DEFAULT_PORT);
        ServerSocketChannel serverChannel;
        Selector selector;
        try {
            serverChannel = ServerSocketChannel.open();
            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(DEFAULT_PORT);
            ss.bind(address);
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }    //執行server初始化

        while (true) {
            try {
                selector.select();
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        System.out.println("Accepted connection from " + client);
                        client.configureBlocking(false);
                        SelectionKey clientKey = client.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        clientKey.attach(buffer);
                    }
                    if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer output = (ByteBuffer) key.attachment();
                        client.read(output);


                    }
                } catch (IOException ex) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }


    ////加入的
    class SocksClient {
        SocketChannel client, remote;
        boolean connected;
        long lastData = 0;

        SocksClient(SocketChannel c) throws IOException {
            client = c;
            client.configureBlocking(false);
            lastData = System.currentTimeMillis();
        }

        public void newRemoteData(Selector selector, SelectionKey sk) throws IOException {
            ByteBuffer buf = ByteBuffer.allocate(4096);
            if (remote.read(buf) == -1)
                throw new IOException("disconnected");
            lastData = System.currentTimeMillis();
            buf.flip();
            client.write(buf);
        }

        public void newClientData(Selector selector, SelectionKey sk) throws IOException {
            if (!connected) {
                ByteBuffer inbuf = ByteBuffer.allocate(512);
                if (client.read(inbuf) < 1)
                    return;
                inbuf.flip();
                // read socks header
                int ver = inbuf.get() & 0xFF;
                System.out.println(ver);
                if (ver != 4) {
                    //throw new IOException("incorrect version" + ver);
                }
                int cmd = inbuf.get();
                System.out.println(cmd);
                // check supported command
                if (cmd != 1) {
                    //throw new IOException("incorrect version");
                }

                final short port = inbuf.getShort();
                System.out.println("port " + port);
                final byte ip[] = new byte[4];
                // fetch IP
                inbuf.get(ip);
                System.out.println("IP "+InetAddress.getByAddress(ip));
                System.out.println("Guggic");
                InetAddress remoteAddr = InetAddress.getByAddress(ip);
                System.out.println(inbuf);
                byte[] payload = new byte[1024];
                while ((inbuf.get()) != 0){
                    inbuf.get(payload,4,508);
                } ; // username

                // hostname provided, not IP
                if (ip[0] == 0 && ip[1] == 0 && ip[2] == 0 && ip[3] != 0) { // host provided
                    String host = "";
                    byte b;
                    while ((b = inbuf.get()) != 0) {
                        host += b;
                    }
                    remoteAddr = InetAddress.getByName(host);
                    System.out.println(host + remoteAddr);
                }
                System.out.println("I m in,2");
                    //to do
                ByteBuffer ttemp = ByteBuffer.allocate(1024);
                ttemp = CHAAAAA.CH_AND_PA(remoteAddr,port,payload);
                int first = CHAAAAA.get_three();
//                remote = SocketChannel.open(new InetSocketAddress(remoteAddr, port));
//                remote = SocketChannel.open(new InetSocketAddress(relay_list.get(first).address.getAddress(), relay_list.get(first).address.getPort()));
                remote = SocketChannel.open(new InetSocketAddress(90));
                System.out.println("Relayyyyyyyyyyyyyyy");
//                ByteBuffer out = ByteBuffer.allocate(4096);
////                out.put((byte) 0);
////                out.put((byte) (remote.isConnected() ? 0x5a : 0x5b));
////                out.putShort((short) port);
////                out.put(remoteAddr.getAddress());
////                out.flip();
                ttemp.flip();
                client.write(ttemp);

                if (!remote.isConnected())
                    throw new IOException("connect failed");

                remote.configureBlocking(false);
                remote.register(selector, SelectionKey.OP_READ);

                connected = true;
            } else {
                ByteBuffer buf = ByteBuffer.allocate(1024);
                if (client.read(buf) == -1)
                    throw new IOException("disconnected");
                lastData = System.currentTimeMillis();
                buf.flip();
                remote.write(buf);
            }
        }
    }

    private static ArrayList<SocksClient> clients = new ArrayList<SocksClient>();

    // utility function
    public SocksClient addClient(SocketChannel s) {
        SocksClient cl;
        try {
            cl = new SocksClient(s);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        clients.add(cl);
        return cl;
    }

    public main() throws IOException {
        ServerSocketChannel socks = ServerSocketChannel.open();
        socks.socket().bind(new InetSocketAddress(DEFAULT_PORT));
        socks.configureBlocking(false);
        Selector select = Selector.open();
        socks.register(select, SelectionKey.OP_ACCEPT);

        int lastClients = clients.size();
        // select loop
        //HS_get_relay_list();
        while (true) {

            select.select(1000);

            Set keys = select.selectedKeys();
            Iterator iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey k = (SelectionKey) iterator.next();

                if (!k.isValid())
                    continue;

                // new connection?
                if (k.isAcceptable() && k.channel() == socks) {
                    System.out.println("HAHAHA YOU　got it");
                    // server socket
                    SocketChannel csock = socks.accept();
                    if (csock == null)
                        continue;
                    addClient(csock);
                    System.out.println("HAHAHA YOU　add it");
                    csock.register(select, SelectionKey.OP_READ);

                } else if (k.isReadable()) {
                    System.out.println(k.channel());
                    // new data on a client/remote socket
                    for (int i = 0; i < clients.size(); i++) {
                        SocksClient cl = clients.get(i);
                        try {
                            if (k.channel() == cl.client) // from client (e.g. socks client)
                                cl.newClientData(select, k);
                            else if (k.channel() == cl.remote) {  // from server client is connected to (e.g. website)
                                cl.newRemoteData(select, k);
                            }
                        } catch (IOException e) { // error occurred - remove client
                            cl.client.close();
                            if (cl.remote != null)
                                cl.remote.close();
                            k.cancel();
                            clients.remove(cl);
                        }

                    }
                }
            }

            // client timeout check
            for (int i = 0; i < clients.size(); i++) {
                SocksClient cl = clients.get(i);
                if ((System.currentTimeMillis() - cl.lastData) > 30000L) {
                    cl.client.close();
                    if (cl.remote != null)
                        cl.remote.close();
                    clients.remove(cl);
                }
            }
            if (clients.size() != lastClients) {
                System.out.println(clients.size());
                lastClients = clients.size();
            }
        }
    }


}

