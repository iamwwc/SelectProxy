package com.wwc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class TCPRelay {
    private static Logger log = LoggerFactory.getLogger(TCPRelay.class);
    private static final int MAX_HANDLERS_NUMBERS = 1024;

    private ServerSocketChannel serverSocketChannel;
    private HashMap<String, Object> config;
    private EventLoop eventLoop;
    private int listenPort;
    private Selector selector = null;
    private boolean isLocal;
    private int lastCleanPosition = 0;

    private List<TCPRelayHandler> handlers = new ArrayList<TCPRelayHandler>();
    private Map<Integer, Integer> indexMap = new LinkedHashMap<>();

    private Handler<SelectionKey> handler = (key) -> {
        if (!key.isValid()) {
            log.debug("invalid key");
            return;
        }

        if (key.isAcceptable()) {
            try {
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);
                socketChannel.socket().setReuseAddress(true);
                socketChannel.socket().setKeepAlive(true);
                socketChannel.socket().setTcpNoDelay(true);
                TCPRelayHandler tcpHandler = new TCPRelayHandler(socketChannel, config, selector, eventLoop, this, isLocal);
                handlers.add(tcpHandler);
                indexMap.put(tcpHandler.hashCode(), handlers.size() - 1);
                socketChannel.register(selector, SelectionKey.OP_READ, tcpHandler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else  {
            TCPRelayHandler handler = (TCPRelayHandler) key.attachment();
            handler.handleEvent(key);
        }
    };

    private Handler<Void> handlePeriodic = (v) -> {
        sweepTimeoutHandlers();
    };

    public TCPRelay(HashMap<String, Object> config, EventLoop eventLoop, Selector selector) {
        this.config = config;
        this.isLocal = (Boolean) config.get("isLocal");
        this.eventLoop = eventLoop;
        this.selector = selector;
        if(isLocal){
            this.listenPort = (Integer) config.get("local_port");
        }else{
            this.listenPort = (Integer) config.get("server_port");
        }

        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.bind(new InetSocketAddress("0.0.0.0", listenPort));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (IOException e) {
            e.printStackTrace();
        }
        addToEventLoop();

        log.info("listen on [{}]",listenPort);
    }

    private void addToEventLoop() {
        eventLoop.addToEventLoop(handler);
        eventLoop.addToPeriodic(handlePeriodic);
    }

    public void updateActivity(TCPRelayHandler handler) {
        int hashCode = handler.hashCode();
        int index = indexMap.get(hashCode);
        assert indexMap.containsKey(hashCode) : "indexMap not have special key";
        assert handlers.get(index) == handler : "handler not equal";

        handlers.set(index, null);
        handlers.add(handler);
        indexMap.put(hashCode, handlers.size() - 1);
    }

    public void destroyHandler(TCPRelayHandler handler) {
        int hashCode = handler.hashCode();
        Integer index = indexMap.get(hashCode);
        if(index == null){
            return;
        }

        assert indexMap.containsKey(hashCode) : "indexMap not have special key";
        assert handlers.get(index) == handler : "handler not equal";

        indexMap.remove(hashCode);
        handlers.set(index, null);

    }

    public void sweepTimeoutHandlers() {
        int position = lastCleanPosition;
        int handlersSize = handlers.size();
        long now = Util.getCurrentTime();
        for (int i = position; i < handlersSize; ++i) {
            TCPRelayHandler handler = handlers.get(i);
            if (handler != null) {
                if (handler.isTimeout(now)) {
                    handler.destroyTimeoutHandler();
                } else {
                    break;
                }
            }
        }
    }

    public void close(){
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
