package com.wwc;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class WrappedSocket {
    private SocketChannel channel;

    {
        try {
            channel = SocketChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
