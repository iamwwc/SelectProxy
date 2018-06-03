package com.wwc.Encryption;

public interface ICrypto<T,M> {
    public T encrypt(M data) throws Exception;
    public T decrypt(M data) throws Exception;
}

