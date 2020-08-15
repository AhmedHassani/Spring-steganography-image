package com.example.demo;

import java.util.Date;

public class ResponseValue {
   private String data ;
   private Date time;

    public ResponseValue(String data) {
        this.data = data;
        this.time = new Date();
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Date getDate() {
        return time;
    }

    public void setDate(Date time) {
        this.time = time;
    }
}
