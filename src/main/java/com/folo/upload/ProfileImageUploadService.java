package com.folo.upload;

import com.folo.common.exception.ApiException;
import com.folo.common.exception.ErrorCode;
import com.folo.config.FileStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileImageUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final FileStorageProperties fileStorageProperties;

    public ProfileImageUploadService(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
    }

    public StoredProfileImage upload(MultipartFile file) {
        validate(file);

        String extension = resolveExtension(file);
        String fileName = UUID.randomUUID() + "." + extension;
        Path targetDirectory = Paths.get(fileStorageProperties.uploadRootDir(), "profile-images")
                .toAbsolutePath()
                .normalize();
        Path targetFile = targetDirectory.resolve(fileName);

        try {
            Files.createDirectories(targetDirectory);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    "프로필 이미지를 저장하지 못했습니다."
            );
        }

        return new StoredProfileImage(
                "/uploads/profile-images/" + fileName,
                fileName,
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize()
        );
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일이 비어 있습니다.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지는 5MB 이하만 업로드할 수 있습니다.");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            if (ALLOWED_EXTENSIONS.contains(extension)) {
                return extension;
            }
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return "jpg";
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }
}
