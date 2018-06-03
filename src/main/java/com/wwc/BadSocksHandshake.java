package com.wwc;

public class BadSocksHandshake extends Exception{

    public BadSocksHandshake(String message){
        super(message);
    }

    public BadSocksHandshake(String message, Throwable cause){
        super(message, cause);
    }
}
