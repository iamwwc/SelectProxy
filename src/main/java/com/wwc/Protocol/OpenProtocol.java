package com.wwc.Protocol;

import com.wwc.Protocol.IProtocol;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Random;

public class OpenProtocol implements IProtocol<ProtocolImp,ByteBuffer> {
    private int headerLengthSpace = 2;
    private int dataLengthSpace = 2;
    private boolean firstRequestSent = false;
    private int ivLength = 12;
    private byte[] iv = new byte[ivLength];
    private Random random = new Random();
    private int Authorization_Tag_Length_In_Bits = 128;

    public OpenProtocol(int authLength){

        this.Authorization_Tag_Length_In_Bits = authLength % 16 ==0
                ? authLength : Authorization_Tag_Length_In_Bits;
    }

    @Override
    public ProtocolImp encode(ByteBuffer data) {
        int headerLength = 0;
        int dataLength = data.remaining() + Authorization_Tag_Length_In_Bits / 16;
        random.nextBytes(iv);
        if(!firstRequestSent){
            firstRequestSent = true;
            headerLength = headerLengthSpace + dataLengthSpace + ivLength;


        }else{
            headerLength = dataLengthSpace + ivLength;
        }

        return null;
    }



    @Override
    public ProtocolImp decode(ByteBuffer data) {
        return null;
    }
}
