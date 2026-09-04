package com.tiaozhanbei.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "txt", "jpg", "jpeg", "png"
    ));

    @Value("${file.upload.dir:./uploads}")
    private String uploadDir;

    public String store(MultipartFile file, String group) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        String originalName = file.getOriginalFilename();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX、TXT、JPG、JPEG、PNG 文件");
        }

        Path root = uploadRoot();
        Path directory = root.resolve(group).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("无效的文件分类");
        }
        Files.createDirectories(directory);

        Path destination = directory.resolve(UUID.randomUUID() + "." + extension).normalize();
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toString();
    }

    public ResponseEntity<Resource> download(String storedPath, String downloadName) throws IOException {
        Path file = resolve(storedPath);
        Resource resource = new FileSystemResource(file);
        String contentType = Files.probeContentType(file);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        String safeName = isBlank(downloadName) ? file.getFileName().toString() : downloadName;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    public Path resolve(String storedPath) throws IOException {
        if (isBlank(storedPath)) {
            throw new IllegalArgumentException("文件尚未上传");
        }
        Path root = uploadRoot();
        Path file = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在或无权访问");
        }
        return file;
    }

    private Path uploadRoot() throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private String extensionOf(String fileName) {
        if (isBlank(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
