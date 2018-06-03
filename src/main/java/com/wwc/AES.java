package com.wwc;

import com.wwc.Encryption.ICrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class AES implements ICrypto<ByteBuffer,ByteBuffer> {
    private static Logger log = LoggerFactory.getLogger(AES.class);
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH_BITS = 128;
    private static final int AUTHORIZATION_TAG_LENGTH = 16;
    private static final int HEADER_LENGTH_SPACE = 2;
    private static final int DATA_LENGTH_SPACE = 2;

    private Cipher encrypt;
    private Cipher decrypt;
    private SecretKey secretKey;
    private boolean firstRequestSent = false;
    private boolean firstRequestReceived = false;
    private byte[] iv = new byte[IV_LENGTH];
    private Random random = new Random();
    private boolean isLocal;

    public AES(String password,boolean isLocal)
            throws NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidAlgorithmParameterException,
            InvalidKeyException {
        this.isLocal = isLocal;
        byte[] key = password.getBytes(Charset.forName("UTF-8"));
        byte[] r = Util.getMD5(key,KEY_LENGTH_BITS/8);
        secretKey = new SecretKeySpec(r,"AES");
        encrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE,secretKey,new GCMParameterSpec(KEY_LENGTH_BITS,iv));
        decrypt.init(Cipher.DECRYPT_MODE,secretKey,new GCMParameterSpec(KEY_LENGTH_BITS,iv));
    }


    @Override
    public ByteBuffer encrypt(ByteBuffer data) throws Exception {
        try{
            if(!isLocal){
                return encryptRunning(data);
            }else {
                if(!firstRequestSent){
                    firstRequestSent = true;
                    return sendFirstRequest(data);
                }else{
                    return encryptRunning(data);
                }
            }
        } catch (ShortBufferException
                | InvalidKeyException
                | InvalidAlgorithmParameterException
                | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new Exception(e);
        }

            }


    private ByteBuffer sendFirstRequest(ByteBuffer data)
            throws BadPaddingException,
            IllegalBlockSizeException, ShortBufferException, InvalidAlgorithmParameterException, InvalidKeyException {

        int dataLength = data.remaining() + AUTHORIZATION_TAG_LENGTH;
        int headerLength = IV_LENGTH + HEADER_LENGTH_SPACE + DATA_LENGTH_SPACE;
        ByteBuffer buf = ByteBuffer.allocate(headerLength + dataLength );
        buf.putShort((short)headerLength);
        buf.putShort((short)dataLength);
        random.nextBytes(iv);
        encrypt.init(Cipher.ENCRYPT_MODE,secretKey,new GCMParameterSpec(AUTHORIZATION_TAG_LENGTH * 8,iv));
        buf.put(iv);
        buf.flip();
        encrypt.updateAAD(buf);
        buf.limit(buf.capacity());
        encrypt.doFinal(data,buf);
        buf.flip();
        return buf;
    }

    private ByteBuffer encryptRunning(ByteBuffer data)
            throws BadPaddingException,
            IllegalBlockSizeException, ShortBufferException, InvalidAlgorithmParameterException, InvalidKeyException {

        int dataLength = data.remaining() + AUTHORIZATION_TAG_LENGTH;
        int headerLength = IV_LENGTH + DATA_LENGTH_SPACE;
        int total = headerLength  + dataLength;
        ByteBuffer buf = ByteBuffer.allocate(total);
        random.nextBytes(iv);
        encrypt.init(Cipher.ENCRYPT_MODE,secretKey,new GCMParameterSpec(AUTHORIZATION_TAG_LENGTH * 8,iv));
        buf.putShort((short)total);
        buf.put(iv);
        buf.flip();
        encrypt.updateAAD(buf);
        buf.limit(buf.capacity());
        encrypt.doFinal(data,buf);
        buf.flip();
        return buf;
    }

    @Override
    public ByteBuffer decrypt(ByteBuffer data)
            throws Exception{

        if(isLocal){
            return decryptRunning(data);
        }else {
            if(!firstRequestReceived){
                firstRequestReceived = true;
                return decryptInit(data);
            }else{
                return decryptRunning(data);
            }
        }
    }

    private ByteBuffer decryptRunning(ByteBuffer data)
            throws BadPaddingException,
            IllegalBlockSizeException,
            InvalidAlgorithmParameterException,
            InvalidKeyException {

        int length = data.remaining();
        byte[] ad = Util.getSubArrayFromByteBuffer(data,0,HEADER_LENGTH_SPACE + IV_LENGTH);

        data.position(2);
        data.get(iv,0,IV_LENGTH);
        int dataLength = length - 14;
        byte[] ae = new byte[dataLength];
        data.get(ae,0,dataLength);

        decrypt.init(Cipher.DECRYPT_MODE,secretKey,new GCMParameterSpec(AUTHORIZATION_TAG_LENGTH * 8,iv));
        decrypt.updateAAD(ad);
        byte[] decrypted = decrypt.doFinal(ae);
        return ByteBuffer.wrap(decrypted);
    }

    private ByteBuffer decryptInit(ByteBuffer data)
            throws BadPaddingException,
            IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {

        int headerLength = data.getShort(0) & 0xffff;
        data.rewind();
        byte[] ad = new byte[headerLength];
        data.get(ad,0,headerLength);
        data.position(4);
        data.get(iv,0,IV_LENGTH);

        decrypt.init(Cipher.DECRYPT_MODE,secretKey,new GCMParameterSpec(AUTHORIZATION_TAG_LENGTH * 8,iv));
        decrypt.updateAAD(ad);
        byte[] array = Util.getArrayFromByteBuffer(data);
        byte[] decrypted = decrypt.doFinal(array);
        return ByteBuffer.wrap(decrypted);
    }
}
