package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class TitleImageStorage {
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png", "webp");

    private final Path baseDirectory;
    private final long maximumBytes;
    private final int maximumDimension;
    private final Semaphore imageProcessingSlot = new Semaphore(1, true);

    public TitleImageStorage(AppProperties properties) {
        this.baseDirectory = Path.of(properties.titleVerification().privateUploadDir())
                .toAbsolutePath().normalize();
        this.maximumBytes = properties.titleVerification().maximumBytes();
        this.maximumDimension = properties.titleVerification().maximumDimension();
    }

    public StoredTitleImage store(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > maximumBytes) {
            throw new AppException(ErrorCode.TITLE_IMAGE_INVALID,
                    "이미지는 " + maximumSizeLabel() + " 이하인 JPG, PNG 또는 WebP 파일이어야 합니다.");
        }
        if (!imageProcessingSlot.tryAcquire()) {
            throw new AppException(
                    ErrorCode.SERVICE_BUSY,
                    "다른 인증 이미지를 처리하고 있습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        try {
            Files.createDirectories(baseDirectory);
            restrict(baseDirectory, "rwx------");
            try (InputStream input = file.getInputStream();
                 ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
                if (imageInput == null) throw invalid();
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw invalid();
                ImageReader reader = readers.next();
                try {
                    String sourceFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!ALLOWED_FORMATS.contains(sourceFormat)) throw invalid();
                    reader.setInput(imageInput, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > maximumDimension || height > maximumDimension) {
                        throw new AppException(ErrorCode.TITLE_IMAGE_INVALID,
                                "이미지 가로·세로는 각각 " + maximumDimension + "px 이하여야 합니다.");
                    }
                    BufferedImage source = reader.read(0);
                    return reencode(source);
                } finally {
                    reader.dispose();
                }
            }
        } catch (AppException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AppException(ErrorCode.TITLE_IMAGE_INVALID, "이미지를 안전하게 읽을 수 없습니다.", exception);
        } finally {
            imageProcessingSlot.release();
        }
    }

    public Resource load(String storedPath) {
        Path path = resolve(storedPath);
        if (!Files.isRegularFile(path)) throw new AppException(ErrorCode.TITLE_REQUEST_NOT_FOUND);
        return new FileSystemResource(path);
    }

    public boolean delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return true;
        Path path = resolve(storedPath);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.deleteIfExists(path);
                return true;
            } catch (IOException ignored) {
                // Retry transient local file locks without delaying the request.
            }
        }
        return false;
    }

    private StoredTitleImage reencode(BufferedImage source) throws IOException {
        boolean alpha = source.getColorModel().hasAlpha();
        String format = alpha ? "png" : "jpg";
        String mime = alpha ? "image/png" : "image/jpeg";
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage sanitized = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D graphics = sanitized.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        String filename = UUID.randomUUID() + "." + format;
        Path target = resolve(filename);
        Path temporary = Files.createTempFile(baseDirectory, "title-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                if (!ImageIO.write(sanitized, format, output)) throw invalid();
            }
            if (Files.size(temporary) > maximumBytes) {
                throw new AppException(ErrorCode.TITLE_IMAGE_INVALID,
                        "안전하게 변환한 이미지가 " + maximumSizeLabel() + "를 초과합니다.");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            restrict(target, "rw-------");
            return new StoredTitleImage(filename, mime);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path resolve(String filename) {
        Path resolved = baseDirectory.resolve(filename == null ? "" : filename).normalize();
        if (!resolved.startsWith(baseDirectory)) throw invalid();
        return resolved;
    }

    private AppException invalid() {
        return new AppException(ErrorCode.TITLE_IMAGE_INVALID);
    }

    private String maximumSizeLabel() {
        long mebibyte = 1024L * 1024L;
        if (maximumBytes % mebibyte == 0) return (maximumBytes / mebibyte) + "MB";
        return Math.max(1, maximumBytes / 1024L) + "KB";
    }

    private void restrict(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(
                    path,
                    java.nio.file.attribute.PosixFilePermissions.fromString(permissions)
            );
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows has no POSIX mode bits; access remains limited by the configured directory ACL.
        }
    }

    public record StoredTitleImage(String path, String mimeType) {
    }
}
