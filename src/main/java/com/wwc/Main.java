package com.wwc;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.HashMap;

public class Main {
    public static void main(String[] args){
        Logger log = LoggerFactory.getLogger(Main.class);
        PropertyConfigurator.configure("resources/log4j.properties");
        HashMap<String, Object> config = new HashMap<>();
        boolean isLocal = true;
        boolean notIsLocal = true;
        for(int i = 0 ; i < args.length ; ++i) {
            if(args[i].equals("--config") ) {
                config = Util.getConfig(args[i + 1]);
                if(config == null) {
                    log.error("config is null, JSONObject is failed");
                    System.exit(-1);
                }
            }else if(args[i].equals("--isLocal") ) {
                notIsLocal = false;
                if(args[i + 1].equals("true")) {
                    isLocal = true;
                }else if(args[i + 1].equals("false")) {
                    isLocal = false;
                }else {
                    log.error("isLocal is invalid");
                    System.exit(-1);
                }
            }
        }
        config.put("isLocal", new Boolean(isLocal));
        Selector selector = null;
        try {
            selector = getSelector();
        } catch (IOException e) {
            e.printStackTrace();

            log.error("cannot create selector, system exit");
            System.exit(-1);
        }
        EventLoop eventLoop = new EventLoop(config,selector);
        TCPRelay tcpRelay = new TCPRelay(config,eventLoop,selector);
        eventLoop.run();
    }

    private static Selector getSelector() throws IOException {
        return Selector.open();
    }
}
