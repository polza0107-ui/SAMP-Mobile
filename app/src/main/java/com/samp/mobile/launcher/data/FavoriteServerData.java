package com.samp.mobile.launcher.data;

public class FavoriteServerData {

    public int id = 0;

    public String ip = "188.212.158.39";
    public int port = 7777;
    public boolean queried;
    public int serverid = 0;

    public FavoriteServerData(int i, int i2, String str, int i3) {
        this.id = i;
        this.serverid = i2;
        this.ip = str;
        this.port = i3;
    }
}