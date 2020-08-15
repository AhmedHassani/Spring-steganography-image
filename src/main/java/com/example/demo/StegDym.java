package com.example.demo;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

public class StegDym {
    public static final int SUCCESS = 0;
    public static final int SUCCESS_NOPASS = 10;
    public static final int ERR_BITCOUNT = 1;
    public static final int ERR_FILEREAD = 2;
    public static final int ERR_FILEWRITE = 3;
    public static final int ERR_NOSTEG = 4;
    public static final int ERR_LOWIMGSIZE = 5;
    public static final int ERR_NOTANIMAGE = 6;
    public static final int ERR_CIPHERFAILED = 7;
    public static final int ERR_WRONGPWD = 8;
    public static final int ERR_PASSREQ = 9;
    
    public static final int FILE = -1;
    public static final int MESSAGE = -2;
    
    private int error,resultType;
    private String msgResult,resultFileName;
    private byte[] fileResult;
    private BufferedImage stegImg;
    
    public StegDym(){
        clear();
    }
    
    private void clear(){
    error = resultType = -100;
    msgResult = resultFileName = null;
    fileResult = null;
    stegImg = null;
    }
    
    public void reset() {
    	clear();
    }
    
    public int getError(){
        return error;
    }
    
    public int getResultType(){
        return resultType;
    }
    
    public byte[] getFileResult(){
        return fileResult;
    }
    
    public String getMessageResult(){
        return msgResult;
    }
    
    public String getFileName(){
        return resultFileName;
    }
    
    public BufferedImage getStegImg(){
        return stegImg;
    }
    
    public void read(InputStream  topFile,char[] password){
        clear();
        if(password==null) {password=new char[0];} //avoiding NullPointerException
        BufferedImage steg;
        try {
            steg = ImageIO.read(topFile);
	    if(steg == null) {
            	error = StegDym.ERR_NOTANIMAGE;
            	return;
            }
        } catch (IOException e) {
            error = StegDym.ERR_FILEREAD;
            return;
        }
        
        int rgb0 = steg.getRGB(0,0);
        int bitCount = (rgb0>>16 & 0b1)+1;
        int type = rgb0>>8 & 0b1;
        int enc = rgb0 & 0b1;
        
        //Verifying if the image has steganographic data in it by signature iSteg0
        int bitMaxVal = (bitCount==2)? 0b11:0b1;
        Chunker sign = new Chunker(bitCount*3,Byte.SIZE);
        OneDimMaker odm = new OneDimMaker(steg.getWidth(),steg.getHeight());
        for(int i=2;i<16/bitCount+2;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            sign.add(value);
        }
        
        if(! "iSteg0".equals(new String(sign.getChunkedByteArray(),StandardCharsets.US_ASCII))){
            error = StegDym.ERR_NOSTEG;
            return;
        }
        
        if(enc==1 && password.length==0){
            error=StegDym.ERR_PASSREQ;
            return;
        }
        
        if(type==0){
            readFileSteg(steg,bitCount,enc,password,2+16/bitCount);
        }
            
        else{
            readStringSteg(steg,bitCount,enc,password,2+16/bitCount);
        }
    }
    
    //hide a file
    public void write(InputStream topFile, byte [] bottomFile, int bitCount, char[] password, String fileName){
        clear();
        if(password==null) {password=new char[0];} //avoiding NullPointerException
        if(bitCount!=1 && bitCount!=2){
            error = StegDym.ERR_BITCOUNT;
            return;
        }
            
        BufferedImage image;
        int enMode=1;
        if(password.length==0) enMode=0;
        
        Chunker fileChk = new Chunker(Byte.SIZE,bitCount*3);
        try {
            image = ImageIO.read(topFile);
	    if(image == null) {
            error = StegDym.ERR_NOTANIMAGE;
            return;
        }
	     if(enMode==0)
                fileChk.add(bottomFile);
            else
                try {
                    fileChk.add(getEncrypted(password,bottomFile));
            } catch (CypherFailedException e) {
                error =  StegDym.ERR_CIPHERFAILED;
                return;
            }
        } catch (IOException e) {
            error =  StegDym.ERR_FILEREAD;
            return;
        }
        
        Chunker sizeChk = new Chunker(30,bitCount*3);
        sizeChk.add(fileChk.getSize());
        Chunker fileNameChk = new Chunker(Byte.SIZE,bitCount*3);
        Chunker fileNameSizeChk = new Chunker(12,bitCount*3);
        if(enMode==0)
            fileNameChk.add(fileName.getBytes(StandardCharsets.UTF_8));
        else
            try {
                fileNameChk.add(getEncrypted(password,fileName.getBytes(StandardCharsets.UTF_8)));
        } catch (CypherFailedException e) {
            error =  StegDym.ERR_CIPHERFAILED;
            return;
        }
        fileNameSizeChk.add(fileNameChk.getSize());
        
        int totalPixReq = 2+ sizeChk.getSize()+ fileNameSizeChk.getSize() + fileChk.getSize()+fileNameChk.getSize()+48/(3*bitCount); //+48/(3*bitCount) for iSteg0
        if(totalPixReq > image.getHeight()*image.getWidth()) {
            error = StegDym.ERR_LOWIMGSIZE;
            return;
        }
        
        int i=init1Meta(image,bitCount,0,enMode);
        i=imageWrite(i,image,sizeChk,bitCount);
        i=imageWrite(i,image,fileNameSizeChk,bitCount);
        i=imageWrite(i,image,fileChk,bitCount);
        imageWrite(i,image,fileNameChk,bitCount);
        
        stegImg = image;
        error = StegDym.SUCCESS;
    }
    
    //hide a text

    
    
    //changes pixel value of the image according to chunks and bitCount
    private int imageWrite(int begIndex, BufferedImage image, Chunker chunk, int bitCount){
        OneDimMaker odm = new OneDimMaker(image.getWidth(),image.getHeight());
        int maxValue = (bitCount==2)?0b111111:0b1111111;
        int i=begIndex;
        int dataMaxValue = (bitCount==2)?0b11:0b1;
        for(;chunk.hasNext();i++){
            int rgb = image.getRGB(odm.get(i).x, odm.get(i).y);
            int data = chunk.get();
            int red = (rgb>>(16+bitCount))<<bitCount | data>>(bitCount*2);
            int green = (rgb>>(8+bitCount) & maxValue)<<bitCount | data>>bitCount & dataMaxValue;
            int blue = (rgb>>(bitCount) & maxValue)<<bitCount | data & dataMaxValue;
            rgb=(((rgb>>24 <<8) | red)<<8 | green)<<8 | blue;
            
            image.setRGB(odm.get(i).x, odm.get(i).y, rgb);
        }
        return i;
    }

    //adds metaData like file or text bitCount at 0,0 of image
    private int init1Meta(BufferedImage image, int bitCount, int type, int enc) {
        Chunker chk = new Chunker(Byte.SIZE,bitCount*3);
        chk.add("iSteg0".getBytes(StandardCharsets.US_ASCII));
        int rgb0 = image.getRGB(0,0);
        int red0 = (rgb0>>17)<<1 | (bitCount-1);
        int green0 = (rgb0>>9 & 0b1111111)<<1 | type;
        int blue0 = (rgb0>>1 & 0b1111111)<<1 | enc;
        rgb0=(((rgb0>>24 <<8) | red0)<<8 | green0)<<8 | blue0;
        image.setRGB(0, 0, rgb0);
        return imageWrite(2,image,chk,bitCount);
    }

    private void readFileSteg(BufferedImage steg,int bitCount,int enc, char[] password,int startIndex) {
        Chunker fileSizeCalc = new Chunker(bitCount*3,30);
        Chunker nameSizeCalc = new Chunker(bitCount*3,12);
        Chunker file = new Chunker(bitCount*3,Byte.SIZE);
        Chunker fileName = new Chunker(bitCount*3,Byte.SIZE);
        int bitMaxVal = (bitCount==2)? 0b11:0b1;
        int bitMaxVal3 = (bitCount==2)? 0b111111:0b111;
        int fileSizeEnd = 10/bitCount+startIndex;
        int nameSizeEnd = 4/bitCount +fileSizeEnd;
        OneDimMaker odm = new OneDimMaker(steg.getWidth(),steg.getHeight());
        for(int i=startIndex;i<fileSizeEnd;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            fileSizeCalc.add(value & bitMaxVal3);
        }
        for(int i=fileSizeEnd;i<nameSizeEnd;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            nameSizeCalc.add(value);
        }
        
        int fileEnd = nameSizeEnd + fileSizeCalc.get();
        int nameEnd = fileEnd + nameSizeCalc.get();
        
        for(int i=fileEnd;i<nameEnd;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            fileName.add(value);
        }

        String fName;
        if(enc==0)
            fName = new String(fileName.getChunkedByteArray(),StandardCharsets.UTF_8);
        else try {
            fName = new String(getDecrypted(password,fileName.getChunkedByteArray()),StandardCharsets.UTF_8);
        } catch (ErrorPasswordException e) {
            error = StegDym.ERR_WRONGPWD;
            return;
        } catch (CypherFailedException e) {
            error = StegDym.ERR_CIPHERFAILED;
            return;
        }
        
        for(int i=nameSizeEnd;i<fileEnd;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            file.add(value);
        }
        if(enc==0)
            fileResult = file.getChunkedByteArray();
        else
            try {
                fileResult = getDecrypted(password,file.getChunkedByteArray());
        } catch (Exception e) {
            error = StegDym.ERR_CIPHERFAILED;
            return;
        }
        if(password.length!=0 && enc==0)
            error = StegDym.SUCCESS_NOPASS;
        else
            error = StegDym.SUCCESS;
        resultFileName = fName;
        resultType = StegDym.FILE;
    }

    private void readStringSteg(BufferedImage steg,int bitCount,int enc, char[] password,int startIndex) {
        Chunker textLenCalc = new Chunker(bitCount*3,18);
        int bitMaxVal = (bitCount==2)? 0b11:0b1;
        int textStart = 6/bitCount+startIndex;
        OneDimMaker odm = new OneDimMaker(steg.getWidth(),steg.getHeight());
        for(int i=startIndex;i<textStart;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            textLenCalc.add(value);
        }
        int textEnd = textLenCalc.get() + textStart;
        Chunker toByte = new Chunker(bitCount*3,Byte.SIZE);
        for(int i=textStart;i<textEnd;i++){
            int rgb = steg.getRGB(odm.get(i).x, odm.get(i).y);
            int value= rgb>>16 & bitMaxVal;
            value = value<<bitCount | (rgb>>8 & bitMaxVal);
            value = value<<bitCount | (rgb & bitMaxVal);
            toByte.add(value);
        }
        
        
        if(enc==0)
            msgResult = new String(toByte.getChunkedByteArray(),StandardCharsets.UTF_8);
        else
            try {
                msgResult = new String(getDecrypted(password,toByte.getChunkedByteArray()),StandardCharsets.UTF_8);
        } catch (ErrorPasswordException e) {
            error = StegDym.ERR_WRONGPWD;
            return;
        } catch (CypherFailedException e) {
            error = StegDym.ERR_CIPHERFAILED;
            return;
        }
        
        resultType = StegDym.MESSAGE;
        
        if(password.length!=0 && enc==0)
            error = StegDym.SUCCESS_NOPASS;
        else
            error = StegDym.SUCCESS;
    }

    private byte[] getEncrypted(char[] password, byte[] plain) throws CypherFailedException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] iv;
        byte[] enc;
        byte[] ret;
        try {
            SecretKey key = new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password,salt,4096,256)).getEncoded(),"AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            iv = cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();
            enc = cipher.doFinal(plain);
            
        } catch (Exception e) {
            throw new CypherFailedException();
        }
        
        ret = new byte[enc.length+salt.length+iv.length];
        System.arraycopy(iv,0, ret, 0,iv.length);
        System.arraycopy(salt,0, ret, iv.length, salt.length);
        System.arraycopy(enc, 0, ret, iv.length+salt.length,enc.length);
        
        return ret;
    }
    
    private byte[] getDecrypted(char[] password,byte[] cyp) throws CypherFailedException,ErrorPasswordException{
        byte[] iv = new byte[16];
        byte[] salt = new byte[16];
        byte[] enc = new byte[cyp.length-32];
        System.arraycopy(cyp,0,iv,0,iv.length);
        System.arraycopy(cyp,iv.length, salt,0, salt.length);
        System.arraycopy(cyp,iv.length+salt.length, enc, 0, enc.length);
        
        try {
            SecretKey key = new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password,salt,4096,256)).getEncoded(),"AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return cipher.doFinal(enc);
        } 
        catch (BadPaddingException e) {
                throw new ErrorPasswordException();
        }
        catch (Exception e) {
            throw new CypherFailedException();
        }
    }

    private class CypherFailedException extends Exception {

        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public CypherFailedException() {
        }
    }

    private class ErrorPasswordException extends Exception{

        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ErrorPasswordException() {
        }
    }
}

