package com.example.demo;

import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.Base64;

@RestController
public class Controller {


    @RequestMapping(value = {"/get1"}, method = RequestMethod.POST)
    public ResponseValue uploadingPost(@RequestParam("file") MultipartFile[] uploadingFiles,@RequestPart(value = "password", required = true) String password) throws IOException {
        System.out.println("Encode....");
        byte [] bytesFiles=null;
        InputStream inputStream = null;
        String nameFile=null;
        char [] pass=password.toCharArray();
        StegDym stegDym=new StegDym();
        for(MultipartFile uploadedFile : uploadingFiles) {
           if (uploadedFile.getResource().getFilename().contains(".mp3")){
               bytesFiles= StreamUtils.copyToByteArray(uploadedFile.getInputStream());
               nameFile=uploadedFile.getResource().getFilename();
           }else if (uploadedFile.getResource().getFilename().contains(".png")||uploadedFile.getResource().getFilename().contains(".jpg")){
               inputStream=uploadedFile.getInputStream();
           }
        }
        stegDym.write(inputStream,bytesFiles,2,pass,nameFile);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(stegDym.getStegImg(), "png", baos);
        String en=Base64.getEncoder().encodeToString(baos.toByteArray());
        return new ResponseValue(en);
    }

    @RequestMapping(value = {"/get",""}, method = RequestMethod.POST)
    public byte[] decodeing(@RequestParam("file") MultipartFile image,@RequestPart(value = "pass", required = true) String password) throws IOException {
        System.out.println("Decoder....");
        InputStream inputStream =image.getInputStream();
        char [] pass=password.toCharArray();
        StegDym stegDym=new StegDym();
        stegDym.read(inputStream,pass);
        return  stegDym.getFileResult();

    }

    @RequestMapping(value = {"","/"})
    public String Start(){
        return "Ok";
    }

}
