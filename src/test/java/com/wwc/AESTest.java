package com.wwc;

import org.junit.Before;
import org.junit.jupiter.api.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AESTest {
    private AES server = null;
    private AES client = null;

    @Before
    public void init(){
        try {
            server = new AES("hello",false);
            client = new AES("hello",true);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }



    @Test
    public void aesEncryptDecrypt_test(){
         init();
        String plainText = "ILoveYou";
         byte[] data = (plainText).getBytes(Charset.forName("UTF-8"));
         ByteBuffer buf = ByteBuffer.wrap(data);
         ByteBuffer again = null;
        try {
            buf = client.encrypt(buf);
            buf = server.decrypt(buf);
            byte[] decrypted = Util.getSubArrayFromByteBuffer(buf,0,buf.remaining());
            assert plainText.equals(new String(decrypted,Charset.forName("UTF-8")));

            again = ByteBuffer.wrap(plainText.getBytes(Charset.forName("UTF-8")));

            again = client.encrypt(again);
            assert again != null;
            again = server.decrypt(again);
            assert again != null;
        } catch (Exception e ){
            e.printStackTrace();
        }
            byte[] decryptedAgain = Util.getSubArrayFromByteBuffer(again,0,again.remaining());
            assert plainText.equals(new String(decryptedAgain));


    }

}
