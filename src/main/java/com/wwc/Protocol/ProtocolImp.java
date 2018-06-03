package com.wwc.Protocol;

import java.nio.ByteBuffer;

public class ProtocolImp {
    private ByteBuffer ad;
    private ByteBuffer ae;
    private byte[] iv ;

    public ProtocolImp(ByteBuffer ad,ByteBuffer ae,byte[] iv){
        this.ad = ad;
        this.ae = ae;
        this.iv = iv;
    }

    public byte[] getIv(){
        return this.iv;
    }

    public ByteBuffer getAe(){
        return this.ae;
    }

    public ByteBuffer getAd(){
        return this.ad;
    }


}
