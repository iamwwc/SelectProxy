package com.wwc;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;

public class Util {
    private static Logger log = LoggerFactory.getLogger(Util.class);
    public static MessageDigest messageDigest;

    public static HashMap<String,Object> config;

    static {
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static long getCurrentTime(){
        return Instant.now().getEpochSecond();
    }

    public static byte[] getMD5(byte[] v,int length){
        byte[] k = messageDigest.digest(v);
        byte [] r = new byte[length];
        System.arraycopy(k,0,r,0,length);
        return r;
    }

    public static byte[] getSubArrayFromByteBuffer(ByteBuffer data,int startPosition,int length){

        byte[] buf = new byte[length];
        int position = data.position();
        data.position(startPosition);
        data.get(buf,0,length);
        data.position(position);
        return buf;
    }

    public static byte[] getArrayFromByteBuffer(ByteBuffer data){
        int position = data.position();
        byte[] buf = new byte[data.remaining()];
        data.get(buf);
        data.position(position);
        return buf;
    }

    //Test----------

    public static byte[] getSubArrayFromByteBuffer_Test(ByteBuffer data,int start,int length){
        return getSubArrayFromByteBuffer(data,start,length);

    }

    public static byte[] getArrayFromByteBuffer_Test(ByteBuffer data){
        return getArrayFromByteBuffer(data);
    }



    public static void main(String[] argv){
        byte[] d1 = new byte[]{1,2,3,4,5,6,7};
        byte[] d2 = new byte[]{7,6,5,4,3,2,1};
        ByteBuffer b1 = ByteBuffer.wrap(d1);
        ByteBuffer b2 = ByteBuffer.wrap(d2);
        assert Arrays.equals(getArrayFromByteBuffer_Test(b2),d2);
        assert Arrays.equals(d1,getSubArrayFromByteBuffer_Test(b1,0,7));
    }

    public static HashMap<String,Object> getConfig(String path) {
        try {
            byte[] config = Files.readAllBytes(Paths.get(path));
            JSONObject jsonObject = new JSONObject(new String(config, Charset.forName("UTF-8")));
            return (Util.config = (HashMap<String, Object>)jsonObject.toMap());
        } catch (IOException e) {
            log.error("cannot find config path,{}",e.getMessage());
        }
        return null;
    }
}
