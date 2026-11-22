package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores question images on disk under random names; the file type is decided from the bytes, not the filename. */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(png|jpg|gif|webp|pdf|docx)$");

    private final Path root;
    private final long maxBytes;
    private final long maxDocBytes;
    private final int maxDimension;

    public FileStorageService(AppProperties props) throws IOException {
        this.root = Paths.get(props.getStorage().getDir()).toAbsolutePath().normalize();
        this.maxBytes = props.getStorage().getMaxImageBytes();
        this.maxDocBytes = props.getStorage().getMaxDocumentBytes();
        this.maxDimension = props.getStorage().getMaxImageDimension();
        Files.createDirectories(root);
    }

    /**
     * Validates and stores an uploaded attachment (image, PDF or Word .docx); returns the generated file name.
     * The type is decided from the file's bytes, never from its name or declared content type.
     */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxDocBytes) {
            throw AppException.bad("File is too large (max " + (maxDocBytes / 1024 / 1024) + " MB).");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            String ext = detectExtension(head);
            if (ext == null) {
                throw AppException.bad("Unsupported file. Upload a PNG, JPEG, GIF, WebP image, a PDF or a Word (.docx) file.");
            }
            if (isImage(ext) && file.getSize() > maxBytes) {
                throw AppException.bad("Image is too large (max " + (maxBytes / 1024 / 1024) + " MB).");
            }
            if ("docx".equals(ext) && !looksLikeDocx(file)) {
                throw AppException.bad("That file is not a valid Word (.docx) document.");
            }
            if (isImage(ext)) {
                String problem = imageProblem(file.getBytes(), ext);
                if (problem != null) {
                    throw AppException.bad(problem);
                }
            }
            String name = UUID.randomUUID() + "." + ext;
            try (InputStream again = file.getInputStream()) {
                Files.copy(again, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return name;
        } catch (IOException e) {
            log.error("Could not store file", e);
            throw AppException.bad("The file could not be saved.");
        }
    }

    /** Like {@link #storeImage} but only pictures are accepted (used for option images). */
    public String storeImageOnly(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            String ext = detectExtension(head);
            if (ext == null || !isImage(ext)) {
                throw AppException.bad("Option images must be PNG, JPEG, GIF or WebP pictures.");
            }
        } catch (IOException e) {
            throw AppException.bad("The file could not be read.");
        }
        return storeImage(file);
    }

    /** Stores picture bytes (for example taken out of a Word file). Returns null if the bytes are not a supported picture. */
    public String storeImageBytes(byte[] data) {
        if (data == null || data.length == 0 || data.length > maxBytes) {
            return null;
        }
        String ext = detectExtension(java.util.Arrays.copyOf(data, Math.min(data.length, 12)));
        if (ext == null || !isImage(ext) || imageProblem(data, ext) != null) {
            return null;
        }
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.write(root.resolve(name), data);
            return name;
        } catch (IOException e) {
            log.error("Could not store image", e);
            return null;
        }
    }

    /**
     * Looks at the picture's header (not the whole decode): a PNG, JPEG or GIF whose header cannot be read is corrupted, and one whose
     * longest side exceeds the configured limit is refused. WebP has no reader in the JDK, so it is accepted on its signature alone.
     * Returns a teacher-readable message, or null when the picture is acceptable.
     */
    String imageProblem(byte[] data, String ext) {
        if ("webp".equals(ext)) {
            return null;
        }
        try (javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return "The picture is corrupted or not a valid " + ext.toUpperCase(java.util.Locale.ROOT) + " image.";
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) {
                    return "The picture is corrupted or empty.";
                }
                if (Math.max(w, h) > maxDimension) {
                    return "The picture is too large (" + w + " x " + h + " pixels; the longest side may be at most " + maxDimension + " pixels).";
                }
                return null;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return "The picture is corrupted or not a valid " + ext.toUpperCase(java.util.Locale.ROOT) + " image.";
        }
    }

    /** SHA-256 of a stored file, hex; null if the name is unknown or unreadable. */
    public String sha256(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(Files.readAllBytes(root.resolve(name).normalize())));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isImage(String ext) {
        return !"pdf".equals(ext) && !"docx".equals(ext);
    }

    /** A .docx is a zip containing word/document.xml; a plain zip, .xlsx or .pptx is rejected. */
    private static boolean looksLikeDocx(MultipartFile file) throws IOException {
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry e;
            int seen = 0;
            while ((e = zin.getNextEntry()) != null && seen++ < 500) {
                if ("word/document.xml".equals(e.getName())) {
                    return true;
                }
            }
        } catch (java.util.zip.ZipException ex) {
            return false;
        }
        return false;
    }

    public Resource load(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw AppException.notFound("Image");
        }
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root) || !Files.isReadable(p)) {
            throw AppException.notFound("Image");
        }
        return new FileSystemResource(p);
    }

    public MediaType mediaType(String name) {
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (name.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (name.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        return MediaType.IMAGE_JPEG;
    }

    public void deleteQuietly(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException e) {
            log.warn("Could not delete image {}", name);
        }
    }

    private static String detectExtension(byte[] h) {
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return "gif";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') {
            return "pdf";
        }
        if (h.length >= 4 && h[0] == 'P' && h[1] == 'K' && h[2] == 3 && h[3] == 4) {
            return "docx"; // confirmed by looksLikeDocx()
        }
        return null;
    }
}
