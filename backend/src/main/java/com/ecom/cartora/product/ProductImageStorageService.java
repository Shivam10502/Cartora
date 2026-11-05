package com.ecom.cartora.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ProductImageStorageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private final Path storageDirectory;

    public ProductImageStorageService(@Value("${app.images.storage-dir:uploads}") String directory) {
        this.storageDirectory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image to upload");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images must be 5 MB or smaller");
        }
        String contentType = file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image could not be read", ex);
        }
        String extension = verifiedExtension(contentType, bytes);
        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(storageDirectory);
            Files.write(storageDirectory.resolve(fileName), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image could not be stored", ex);
        }
        return "/uploads/" + fileName;
    }

    public void deleteManagedImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/")) return;
        String name = imageUrl.substring("/uploads/".length());
        if (!name.matches("[A-Za-z0-9-]+\\.(jpg|png|webp)")) return;
        try {
            Files.deleteIfExists(storageDirectory.resolve(name).normalize());
        } catch (IOException ignored) {
            // Stale image cleanup is best-effort; it must not roll back a product update.
        }
    }

    private String verifiedExtension(String contentType, byte[] bytes) {
        if (contentType == null) throw invalidImage();
        if (contentType.equalsIgnoreCase("image/jpeg") && bytes.length >= 3
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) return ".jpg";
        if (contentType.equalsIgnoreCase("image/png") && bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) return ".png";
        if (contentType.equalsIgnoreCase("image/webp") && bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return ".webp";
        throw invalidImage();
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid JPEG, PNG, or WebP image");
    }
}
