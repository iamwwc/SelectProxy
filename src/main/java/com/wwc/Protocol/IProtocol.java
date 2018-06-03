package com.wwc.Protocol;


public interface IProtocol<T,M> {
    T encode(M data);
    T decode(M data);
}
