package com.hotel.hotel_booking.hotel;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.hotel.hotel_booking.media.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImageStorage {
    private final Path root;
    private final CloudinarySettings settings;
    private final CloudinaryGateway cloud;
    public record Stored(String key,String type,long size,int width,int height) {}
    public ImageStorage(@Value("${app.media.directory:./uploads/hotel-images}") String directory, CloudinarySettings settings, CloudinaryGateway cloud){
        root=Path.of(directory).toAbsolutePath().normalize();this.settings=settings;this.cloud=cloud;
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid JPG or PNG, up to 5 MB and 6000 × 6000 pixels (25 megapixels).");}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Photo storage is unavailable. Please try again.");}
    private Path path(String key) throws IOException {
        if(!key.matches("[a-f0-9-]{36}\\.(jpg|png)")) throw new IOException("Invalid key");
        Files.createDirectories(root);
        if(Files.isSymbolicLink(root)) throw new IOException("Invalid directory");
        Path target=root.resolve(key).normalize();
        if(!target.startsWith(root)||Files.isSymbolicLink(target)) throw new IOException("Invalid path");
        return target;
    }
    public Stored store(MultipartFile file) {
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw invalid();
        byte[] bytes;
        try(var input=file.getInputStream()){bytes=input.readNBytes(5*1024*1024+1);}catch(IOException e){throw invalid();}
        if(bytes.length>5*1024*1024) throw invalid();
        byte[] encoded; String extension; int width,height;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw invalid();
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","png").contains(format)) throw invalid();
                width=reader.getWidth(0);height=reader.getHeight(0);
                if(width<1||height<1||width>6000||height>6000||(long)width*height>25_000_000) throw invalid();
                extension=format.equals("jpeg")?"jpg":"png";
                var image=reader.read(0);var output=new ByteArrayOutputStream();
                if(!ImageIO.write(image,extension,output)) throw invalid();
                encoded=output.toByteArray();if(encoded.length>8*1024*1024) throw invalid();
            } finally {reader.dispose();}
        } catch(IOException|IllegalArgumentException e){throw invalid();}
        String key=(settings.enabled()?"cld-":"")+UUID.randomUUID()+"."+extension;
        if(settings.enabled()) {
            try {cloud.upload(key,encoded);}catch(RuntimeException e){delete(key);throw unavailable();}
        } else {
            try {Files.write(path(key),encoded,StandardOpenOption.CREATE_NEW);}catch(IOException e){delete(key);throw unavailable();}
        }
        return new Stored(key,extension.equals("jpg")?"image/jpeg":"image/png",encoded.length,width,height);
    }
    public byte[] read(String key) {
        if(CloudinaryGateway.cloudKey(key))return cloud.read(key);
        return readLocal(key);
    }
    public byte[] readLocal(String key) {
        try(var input=Files.newInputStream(path(key))){byte[] bytes=input.readNBytes(8*1024*1024+1);if(bytes.length>8*1024*1024)throw new IOException();return bytes;}
        catch(IOException e){throw HotelManagementService.missing();}
    }
    public void delete(String key){
        try {if(CloudinaryGateway.cloudKey(key))cloud.delete(key);else Files.deleteIfExists(path(key));}
        catch(IOException|RuntimeException e){org.slf4j.LoggerFactory.getLogger(ImageStorage.class).warn("Photo cleanup failed; storage maintenance is required.");}
    }
}
