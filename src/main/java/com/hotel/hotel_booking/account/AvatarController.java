package com.hotel.hotel_booking.account;

import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/account/avatar")
public class AvatarController {
    private final AvatarService avatars;
    public AvatarController(AvatarService avatars){this.avatars=avatars;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarService.View upload(Principal p,@RequestParam MultipartFile file){return avatars.upload(p.getName(),file);}
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal p){avatars.remove(p.getName());}
    @GetMapping("/{token}") public ResponseEntity<byte[]> image(Principal p,@PathVariable String token){
        var media=avatars.media(p.getName(),token);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.type())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").body(media.bytes());
    }
}
