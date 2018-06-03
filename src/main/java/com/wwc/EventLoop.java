package com.wwc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

public class EventLoop {
    private static Logger log = LoggerFactory.getLogger(EventLoop.class.getName());
    private static final int CLEAN_TIMEOUT = 10000;

    private Selector selector;
    private HashMap<String,Object> config;

    private List relay = new ArrayList<Object>();
    private List periodicCallback = new ArrayList<Handler<Void>>();
    private long lastTime = Util.getCurrentTime();

    public EventLoop(HashMap<String,Object> config,Selector selector){
        this.selector = selector;
        this.config = config;
    }

    public void run(){
        while(true){
            try {
                selector.select(CLEAN_TIMEOUT);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();
            while(keyIterator.hasNext()){
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                Handler handler = (Handler) relay.get(0);
                handler.handleEvent(key);
            }

            long now = Util.getCurrentTime();
            if(now - lastTime > CLEAN_TIMEOUT){
                Iterator<Handler<Integer>> iterator = periodicCallback.iterator();
                while(iterator.hasNext()){
                    Handler handler = iterator.next();
                    handler.handleEvent(new Object());
                }
            }



        }


    }

    private void dispactch(){

    }

    public <T> void addToEventLoop(Handler handler){
        relay.add(handler);

    }

    public <T> void addToPeriodic(Handler handler){
        periodicCallback.add(handler);
    }
}
