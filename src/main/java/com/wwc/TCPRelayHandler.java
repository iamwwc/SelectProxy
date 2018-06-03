package com.wwc;

import com.wwc.Encryption.Encryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class TCPRelayHandler {
    private static Logger log = LoggerFactory.getLogger(TCPRelayHandler.class);
    private static final int VERSION = 0x05;

    private static final int CMD_CONNECT = 0x01;
    private static final int CMD_BIND = 0x02;
    private static final int CMD_UDP_ASSOCIATE = 0x03;

    private static final int ATYP_IPV4 = 0x01;
    private static final int ATYP_DOMAIN_NAME = 0x03;
    private static final int ATYP_IPV6 = 0x04;

    private static final int STREAM_INIT = 0;
    private static final int STREAM_ADDR_CONNECTING = 1;
    private static final int STREAM_DNS_RESOLVING = 2;
    private static final int STREAM_RUNNING = 3;

    private Queue<ByteBuffer> buffers = new LinkedList<>();

    private static final int BUF_SIZE = 16 * 1024;
    private static final int BUF_INIT = 50;

    private boolean isLocal;
    private int timeout ;

    private  boolean isDestroyed = false;

    private int status;

    private InetSocketAddress inetSocketAddress ;
    private InetSocketAddress proxySocketAddress;

    private EventLoop eventLoop ;
    private Selector selector ;
    private TCPRelay tcpRelay ;
    private ProtocolParser parser ;
    private Encryptor encryptor;
    private HashMap<String,Object> config ;

    private SocketChannel localChannel;
    private SocketChannel remoteChannel;

    private int hashCode = this.hashCode();

    private long lastActive = Util.getCurrentTime();

    private Queue<ByteBuffer> writeToLocal = new LinkedList<>();
    private Queue<ByteBuffer> writeToRemote = new LinkedList<>();

    //decrypt must remove header
    private Handler<ByteBuffer> handler = (data) ->{
        assert (status == STREAM_RUNNING ||status ==STREAM_ADDR_CONNECTING):"bad socks process" ;

        log.debug("recv completed data from protocol parser, data size: [{}]",data.remaining());
        try {
            data = encryptor.decrypt(data);
        } catch (Exception e) {
            log.debug("",e);
            destroyHandler();
            return;
        }
        if(status == STREAM_RUNNING){
            if(isLocal){
                writeToLocal.offer(data);
                if(localChannel != null && localChannel.isConnected()){
                    registerOps(localChannel,SelectionKey.OP_WRITE);
                }

            }else{
                writeToRemote.offer(data);
                if(remoteChannel != null && remoteChannel.isConnected()){
                    registerOps(remoteChannel, SelectionKey.OP_WRITE);
                }

            }
        }else if(status == STREAM_ADDR_CONNECTING){
            try {
                processAddr(data);
                connectToRemote();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                log.debug("Exception: [{}], destroy handler",e.getMessage());
                destroyHandler();
            } catch (IOException e) {
                e.printStackTrace();
            }
            status = STREAM_RUNNING;
        }
    };

    public TCPRelayHandler(SocketChannel socketChannel,HashMap<String,Object> config, Selector selector, EventLoop eventLoop, TCPRelay tcpRelay,boolean isLocal){
        this.isLocal = isLocal;

        this.localChannel = socketChannel;
        this.config = config;
        this.selector = selector;
        this.eventLoop = eventLoop;
        this.tcpRelay = tcpRelay;
        this.status = isLocal ? STREAM_INIT : STREAM_ADDR_CONNECTING;
        this.parser = new ProtocolParser(handler,isLocal);
        this.encryptor = new Encryptor(config,isLocal);
        this.timeout = (int) config.get("timeout");
        if(isLocal){
            String host = (String)config.get("server_address");
            int port = (Integer)config.get("server_port");
            proxySocketAddress = new InetSocketAddress(host,port);
        }
        InetSocketAddress inet = null;
        try {
            inet = (InetSocketAddress) localChannel.getRemoteAddress();
        } catch (IOException e) {
            log.debug("[{}],Exception: [{}]",hashCode,e.getMessage());
            destroyHandler();
        }
        if(inet != null){
            log.info("[{}], accept from [{}:{}]",hashCode,inet.getHostString(),inet.getPort());
        }

    }

    public void handleEvent(SelectionKey key){
        if(isDestroyed){
            return;
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try{
            if(socketChannel == localChannel){
                if(key.isWritable()){
                    onLocalWrite();
                }else{
                    onLocalRead();
                }
            }else{
                if(key.isWritable()){
                    onRemoteWrite();
                }else if(key.isReadable()){
                    onRemoteRead();
                }else {
                    boolean success = socketChannel.finishConnect();
                    socketChannel.register(selector,SelectionKey.OP_WRITE | SelectionKey.OP_READ,this);
                    if(success){
                        InetSocketAddress addr = (InetSocketAddress) socketChannel.getLocalAddress();
                        log.debug("connected to [{}:{}]",addr.getHostString(),addr.getPort());
                    }
                }
            }

        }catch(Exception e ){
            log.debug("[{}],Exception: [{}], handler destroyed",hashCode,e.getMessage());
            destroyHandler();
        }

    }

    private void onLocalRead(){
        ByteBuffer data = buffers.poll();
        if(data == null){
            data = ByteBuffer.allocate(BUF_SIZE);
        }
        data.clear();

        int length = 0;
        try {
            length = localChannel.read(data);
        } catch (IOException e) {
            destroyHandler();
            log.debug("Exception: [{}]",e.getMessage());
            return;
        }

        if(length == -1){
            //there no more data to read, we should close socket.
            destroyHandler();
            return;
        }
        if(length == 0){
            return;
        }
        log.debug("[{}], read from local [{}] bytes",hashCode,length);
        data.flip();
        if(isLocal){
            try {
                processClientLocalRead(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            parser.put(data);
        }
        updateActivity();

    }



    private void processClientLocalRead(ByteBuffer data)
            throws IOException,
                BadSocksHandshake {

        if(status == STREAM_RUNNING){
            try {
                data = encryptor.encrypt(data);
            } catch (Exception e) {
                log.debug("Exception: [{}], destroy handler",e.getMessage());
                e.printStackTrace();
                destroyHandler();
            }
            writeToRemote.offer(data);
            registerOps(remoteChannel,SelectionKey.OP_WRITE);

        }else if(status == STREAM_ADDR_CONNECTING){
            data = processStreamAddrConnecting(data);
            connectToRemote();
            try {
                data = encryptor.encrypt(data);
            } catch(Exception e ){
                log.debug("Exception: [{}]",e.getMessage());
                e.printStackTrace();
                destroyHandler();
            }
            writeToRemote.offer(data);
            registerOps(remoteChannel,SelectionKey.OP_WRITE);
            status = STREAM_RUNNING;
            localChannel.write(ByteBuffer.wrap(new byte[]{5,0,0,1,0,0,0,0,0x10,0x10}));
        }else if(status == STREAM_INIT){
            processStreamInit(data);
            status = STREAM_ADDR_CONNECTING;
        }

    }

    private void processStreamInit(ByteBuffer data)
            throws BadSocksHandshake,
                    IOException {

        int version = data.get();
        if(version != VERSION){
            log.debug("Bad Socks Handshake, expected: [{}], actual: [{}]",VERSION, version);
            throw new BadSocksHandshake("Only supporty Socks version 5");
        }
        localChannel.write(ByteBuffer.wrap(new byte[]{0x05,0x00}));
    }


//            +----+-----+-------+------+----------+----------+
//            |VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
//            +----+-----+-------+------+----------+----------+
//            | 1  |  1  | X'00' |  1   | Variable |    2     |
//            +----+-----+-------+------+----------+----------+

    //becaues of ByteBuffer.position and limit, caller must use flip for called
    private ByteBuffer processStreamAddrConnecting(ByteBuffer data)
            throws UnknownHostException {

        int cmd = data.get(1);
        switch(cmd){
            case CMD_CONNECT:{
                data.position(3);
                data = data.slice();
                data = processAddr(data);
                data = data.slice();
                break;
            }
            case CMD_BIND:{

            }
            case CMD_UDP_ASSOCIATE:{

            }
            log.error("only support CMD_CONNECT now");
        }
        return data;
    }

    private ByteBuffer processAddr(ByteBuffer data)
            throws UnknownHostException {
        String dstHost = null;
        int dstPort = 0;
        int type = data.get(0);
        int position = 0;
        switch(type){
            case ATYP_IPV4:{
                byte[] ipv4 = new byte[4];
                data.get(ipv4,0,4);
                dstHost = InetAddress.getByAddress(ipv4).getHostAddress();
                dstPort = data.getShort(5) & 0xffff;
                position = 7;
                break;
            }
            case ATYP_DOMAIN_NAME:{
                int length = data.get(1);
                byte[] host = new byte[length];
                data.position(2);
                data.get(host,0,length);
                dstHost = new String(host, Charset.forName("UTF-8"));
                dstPort = data.getShort(length + 2) & 0xffff;
                position = length + 2 + 2;
                break;
            }
            case ATYP_IPV6:{
                byte[] ipv6 = new byte[16];
                data.get(ipv6,0,16);
                dstHost = InetAddress.getByAddress(ipv6).getHostAddress();
                dstPort = data.getShort(17) & 0xffff;
                position = 19;
                break;
            }
        }
        if(!isLocal){
            inetSocketAddress = new InetSocketAddress(dstHost,dstPort);
        }

        log.debug("Socks parse completed, dst: [{}:{}]",dstHost,dstPort);
        if(isLocal){
            data.rewind();
        }else{
            data.position(position);
        }
        return data;
    }

    private void processServerLocalRead(){

    }

    private void onRemoteRead()
            throws IOException {

        ByteBuffer data = buffers.poll();
        if(data == null){
            data = ByteBuffer.allocate(BUF_SIZE);
        }
        data.clear();
        int length = remoteChannel.read(data);
        if(length == 0){
            return;
        }
        if(length == -1){
            destroyHandler();
            return;
        }
        data.flip();
        if(isLocal){
         parser.put(data);
        }else{
            try {
                data = encryptor.encrypt(data);
                assert data.remaining() != 0;
                writeToLocal.offer(data);
                registerOps(localChannel,SelectionKey.OP_WRITE);
            }catch(Exception e ){
                e.printStackTrace();
            }
        }

    }

    private void onRemoteWrite()
            throws IOException {
        while(!writeToRemote.isEmpty()){
            ByteBuffer data = writeToRemote.peek();
            int before = data.remaining();
            int after = remoteChannel.write(data);
            int remaining = before - after;
            if(remaining > 0 ){
                data.compact();
                int newLimit = data.position();
                data.rewind();
                data.limit(newLimit);
            }else{
                data.clear();
                buffers.offer(data);
                writeToRemote.remove();
            }
            assert before >= after :"this handler may in trouble, use ByteBuffer.flip?";
        }
        unregisterOps(remoteChannel,SelectionKey.OP_WRITE);
    }

    private void onLocalWrite()
            throws IOException {
        while(!writeToLocal.isEmpty()){
            ByteBuffer data = writeToLocal.peek();
            int before = data.remaining();
            int after = localChannel.write(data);
            if(before > after ){
                data.compact();
                int newLimit = data.position();
                data.rewind();
                data.limit(newLimit);
            }else{
                data.clear();
                buffers.offer(data);
                writeToLocal.remove();
            }
            assert before >= after : "this handler may in trouble, use ByteBuffer.flip?";
        }
        unregisterOps(localChannel,SelectionKey.OP_WRITE);
    }

    private void writeToChannel(SocketChannel channel, ByteBuffer data) {
        if(channel == null || data == null){
            return;
        }

        if(channel == localChannel){

            writeToLocal.offer(data);
            registerOps(localChannel,SelectionKey.OP_WRITE);
        }else{
            writeToRemote.offer(data);
            registerOps(remoteChannel,SelectionKey.OP_WRITE);
        }
    }


    private void updateActivity(){

        long now = Util.getCurrentTime();
        if(now - lastActive < timeout){
            return;
        }
        lastActive = now;
        this.tcpRelay.updateActivity(this);
    }

    public void destroyTimeoutHandler(){
        log.debug("remote: [{}:{}] timeout",inetSocketAddress.getHostString(),inetSocketAddress.getPort());
        destroyHandler();
    }

    private void destroyHandler(){
        try {
            if(localChannel != null){
                SelectionKey key = localChannel.keyFor(selector);
                if(key.isValid()){
                    key.cancel();
                }
                localChannel.close();
                localChannel = null;
            }
            if(remoteChannel != null){
                SelectionKey key = remoteChannel.keyFor(selector);
                if(key.isValid()){
                    key.cancel();
                }
                remoteChannel.close();
                remoteChannel = null;
            }
            tcpRelay.destroyHandler(this);
            isDestroyed = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectToRemote()
            throws IOException {

        remoteChannel = SocketChannel.open();
        remoteChannel.socket().setTcpNoDelay(true);
        remoteChannel.socket().setKeepAlive(true);
        remoteChannel.socket().setReuseAddress(true);
        remoteChannel.configureBlocking(false);
        InetSocketAddress dstAddr = null;
        if(isLocal){
            dstAddr = proxySocketAddress;
        }else{
            dstAddr = inetSocketAddress;
        }
        boolean isConnected = remoteChannel.connect(dstAddr);
        log.debug("try to connect [{}:{}]",dstAddr.getHostString(),dstAddr.getPort());
        remoteChannel.register(selector,SelectionKey.OP_READ|SelectionKey.OP_WRITE,this);
        if(!isConnected){
            registerOps(remoteChannel,SelectionKey.OP_CONNECT);
        }
    }

    public boolean isTimeout(long now){
        return (now - lastActive) > timeout;
    }


    private void registerOps(SocketChannel channel, int Ops){
        SelectionKey key = channel.keyFor(selector);
        int oldOps = key.interestOps();
        int newOps = oldOps | Ops;
        key.interestOps(newOps);
    }

    private void unregisterOps(SocketChannel channel, int Ops){
        SelectionKey key = channel.keyFor(selector);
        int oldOps = key.interestOps();
        int newOps =  oldOps & ~Ops;
        key.interestOps(newOps);
    }
}
