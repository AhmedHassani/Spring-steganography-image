package com.example.demo;

import java.util.Date;

public class ResponseValue {
   private byte [] data ;
   private Date time;

    public ResponseValue(byte [] data) {
        this.data = data;
        this.time = new Date();
    }

    public byte [] getData() {
        return data;
    }

    public void setData(byte []data) {
        this.data = data;
    }

    public Date getDate() {
        return time;
    }

    public void setDate(Date time) {
        this.time = time;
    }
}
