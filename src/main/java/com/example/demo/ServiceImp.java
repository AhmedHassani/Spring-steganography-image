package com.example.demo;


import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class ServiceImp {
    private  StegDym stegDym;

    public ServiceImp(){
        stegDym=new StegDym();
    }


}
