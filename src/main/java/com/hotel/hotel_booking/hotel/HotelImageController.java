package com.hotel.hotel_booking.hotel;
import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.hotel.hotel_booking.hotel.HotelImageService.*;

@RestController
public class HotelImageController {
    private final HotelImageService service;
    public HotelImageController(HotelImageService service){this.service=service;}
    @GetMapping("/api/host/hotels/{hotelId}/images") public List<View> own(@PathVariable Long hotelId,@RequestParam(required=false) Long roomTypeId,Principal p){return service.own(hotelId,roomTypeId,p.getName());}
    @PostMapping(value="/api/host/hotels/{hotelId}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public View upload(@PathVariable Long hotelId,@RequestParam(required=false) Long roomTypeId,@RequestParam MultipartFile file,@RequestParam(defaultValue="") String altText,Principal p){return service.upload(hotelId,roomTypeId,p.getName(),file,altText);}
    @PutMapping("/api/host/hotels/{hotelId}/images/order") public List<View> order(@PathVariable Long hotelId,@Valid @RequestBody Order in,Principal p){return service.reorder(hotelId,p.getName(),in);}
    @PatchMapping("/api/host/hotels/{hotelId}/images/{imageId}") public View caption(@PathVariable Long hotelId,@PathVariable Long imageId,@Valid @RequestBody Caption in,Principal p){return service.caption(hotelId,imageId,p.getName(),in);}
    @DeleteMapping("/api/host/hotels/{hotelId}/images/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long hotelId,@PathVariable Long imageId,Principal p){service.delete(hotelId,imageId,p.getName());}
    @GetMapping("/api/hotels/{hotelId}/images") public List<View> published(@PathVariable Long hotelId,@RequestParam(required=false) Long roomTypeId){return service.publicImages(hotelId,roomTypeId);}
    @GetMapping("/api/media/{imageId}") public ResponseEntity<byte[]> media(@PathVariable Long imageId,Principal p){var media=service.media(imageId,p);return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.type())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").body(media.bytes());}
}
