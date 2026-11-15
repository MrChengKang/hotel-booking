package com.hotel.hotel_booking.media;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CloudinarySettings {
    final String cloudName, apiKey, apiSecret, prefix;
    private final String provider;

    public CloudinarySettings(Environment env) {
        provider = env.getProperty("app.media.provider", "local").strip();
        if (!provider.equals("local") && !provider.equals("cloudinary"))
            throw new IllegalStateException("STAY_MEDIA_PROVIDER must be local or cloudinary.");
        cloudName = env.getProperty("app.media.cloudinary.cloud-name", "").strip();
        apiKey = env.getProperty("app.media.cloudinary.api-key", "").strip();
        apiSecret = env.getProperty("app.media.cloudinary.api-secret", "").strip();
        prefix = env.getProperty("app.media.cloudinary.prefix", "stay").strip();
        if (enabled()) requireReady();
    }

    public boolean enabled() { return provider.equals("cloudinary"); }

    public void requireReady() {
        if (!cloudName.matches("[A-Za-z0-9_-]{1,100}") || apiKey.isBlank() || apiSecret.isBlank()
                || !prefix.matches("[A-Za-z0-9_-]{1,60}"))
            throw new IllegalStateException("Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET and a simple STAY_CLOUDINARY_PREFIX on the backend.");
    }
}
