package com.starrailhearing.profile.client;

public interface GameProfileClient {
    PublicGameProfile fetch(String uid, boolean forceUpdate);
}
