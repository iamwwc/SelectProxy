package com.wwc.Encryption;

import com.wwc.AES;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class Encryptor {
    private ICrypto<ByteBuffer,ByteBuffer> cipher;
    public Encryptor(Map<String,Object> config,boolean isLocal){
        try {
            String passwd = (String)config.get("password");
            cipher = new AES(passwd,isLocal);
        } catch (NoSuchPaddingException
                | NoSuchAlgorithmException
                | InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    public ByteBuffer encrypt(ByteBuffer data) throws Exception {
        if(!data.hasRemaining())
            return data;
                 try {
                     return cipher.encrypt(data);
                 } catch (BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException | ShortBufferException | InvalidKeyException e) {
                     e.printStackTrace();
                     throw new Exception(e);
                 }
             }

    public ByteBuffer decrypt(ByteBuffer data)
            throws Exception {

        return cipher.decrypt(data);
    }



}
