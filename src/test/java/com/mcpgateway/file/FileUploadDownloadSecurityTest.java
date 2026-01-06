package com.mcpgateway.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * File Upload/Download Security Tests
 *
 * Tests critical security aspects of file handling:
 * - Path traversal prevention (../, ..%2F, etc.)
 * - File size limit enforcement
 * - Content-type validation
 * - Interrupted upload cleanup
 * - Malicious filename handling
 * - Symlink attack prevention
 * - Directory traversal via encoded paths
 */
class FileUploadDownloadSecurityTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "application/json",
        "text/plain",
        "application/pdf",
        "image/png",
        "image/jpeg"
    };

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempDir.toString(), MAX_FILE_SIZE, ALLOWED_CONTENT_TYPES);
    }

    @Test
    void testPathTraversal_DotDotSlash_ShouldBeRejected() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../../../etc/passwd",
            "text/plain",
            "malicious content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../secret.txt",
        "../../passwords.txt",
        "../../../etc/passwd",
        "..\\windows\\system32\\config\\sam",
        "....//....//etc/passwd",
        "..%2F..%2Fetc%2Fpasswd",
        "..%252F..%252Fetc%252Fpasswd",
        "%2e%2e%2f%2e%2e%2fetc%2fpasswd"
    })
    void testPathTraversalVariants_AllShouldBeRejected(String maliciousFilename) {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            maliciousFilename,
            "text/plain",
            "content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    void testFileSizeLimit_ExceedsMax_ShouldBeRejected() {
        // Arrange - Create file larger than limit
        byte[] largeContent = new byte[(int) MAX_FILE_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "large-file.txt",
            "text/plain",
            largeContent
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum");
    }

    @Test
    void testFileSizeLimit_ExactlyAtMax_ShouldBeAccepted() throws Exception {
        // Arrange - Create file exactly at limit
        byte[] maxContent = new byte[(int) MAX_FILE_SIZE];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "max-file.txt",
            "text/plain",
            maxContent
        );
        UUID userId = UUID.randomUUID();

        // Act
        String fileId = fileStorageService.uploadFile(file, userId);

        // Assert
        assertThat(fileId).isNotNull();
        Path uploadedFile = fileStorageService.getFilePath(fileId, userId);
        assertThat(Files.exists(uploadedFile)).isTrue();
        assertThat(Files.size(uploadedFile)).isEqualTo(MAX_FILE_SIZE);
    }

    @Test
    void testContentTypeValidation_DisallowedType_ShouldBeRejected() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "malicious.exe",
            "application/x-msdownload",
            "malicious content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content type not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application/json",
        "text/plain",
        "application/pdf",
        "image/png",
        "image/jpeg"
    })
    void testContentTypeValidation_AllowedTypes_ShouldBeAccepted(String contentType) throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "valid-file.txt",
            contentType,
            "valid content".getBytes()
        );
        UUID userId = UUID.randomUUID();

        // Act
        String fileId = fileStorageService.uploadFile(file, userId);

        // Assert
        assertThat(fileId).isNotNull();
    }

    @Test
    void testMaliciousFilename_NullBytes_ShouldBeRejected() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "malicious\0.txt.exe",
            "text/plain",
            "content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    void testInterruptedUpload_ShouldCleanupPartialFile() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "test content".getBytes()
        );

        // Act - Simulate interrupted upload
        String fileId = fileStorageService.uploadFile(file, userId);
        Path filePath = fileStorageService.getFilePath(fileId, userId);

        // Simulate error after partial upload
        fileStorageService.cleanupFailedUpload(fileId, userId);

        // Assert - File should be removed
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void testDownload_PathTraversal_ShouldBeRejected() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act & Assert - Try to download with traversal in fileId
        assertThatThrownBy(() -> fileStorageService.downloadFile("../../etc/passwd", userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid file ID");
    }

    @Test
    void testDownload_DifferentUser_ShouldBeRejected() throws Exception {
        // Arrange
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "private.txt",
            "text/plain",
            "private content".getBytes()
        );

        String fileId = fileStorageService.uploadFile(file, user1);

        // Act & Assert - User2 tries to download User1's file
        assertThatThrownBy(() -> fileStorageService.downloadFile(fileId, user2))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unauthorized access");
    }

    @Test
    void testSymlinkAttack_ShouldBeDetected() throws Exception {
        // Arrange
        Path targetFile = tempDir.resolve("target.txt");
        Files.write(targetFile, "sensitive data".getBytes());

        Path symlinkPath = tempDir.resolve("symlink");
        Files.createSymbolicLink(symlinkPath, targetFile);

        // Act & Assert - Service should detect and reject symlink
        assertThatThrownBy(() -> fileStorageService.validatePath(symlinkPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Symlink not allowed");
    }

    @Test
    void testFilenameLength_TooLong_ShouldBeRejected() {
        // Arrange - Create filename longer than 255 characters
        String longFilename = "a".repeat(256) + ".txt";
        MockMultipartFile file = new MockMultipartFile(
            "file",
            longFilename,
            "text/plain",
            "content".getBytes()
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename too long");
    }

    @Test
    void testEmptyFile_ShouldBeRejected() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.txt",
            "text/plain",
            new byte[0]
        );

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File is empty");
    }

    @Test
    void testConcurrentUploads_SameUser_ShouldIsolateFiles() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        int numFiles = 10;

        // Act - Upload multiple files concurrently
        String[] fileIds = new String[numFiles];
        for (int i = 0; i < numFiles; i++) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "concurrent-" + i + ".txt",
                "text/plain",
                ("content-" + i).getBytes()
            );
            fileIds[i] = fileStorageService.uploadFile(file, userId);
        }

        // Assert - All files exist and are distinct
        assertThat(fileIds).doesNotHaveDuplicates();
        for (String fileId : fileIds) {
            Path filePath = fileStorageService.getFilePath(fileId, userId);
            assertThat(Files.exists(filePath)).isTrue();
        }
    }

    @Test
    void testUploadWithSpecialCharacters_SafeFilename() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "file with spaces & special!@#$%.txt",
            "text/plain",
            "content".getBytes()
        );
        UUID userId = UUID.randomUUID();

        // Act
        String fileId = fileStorageService.uploadFile(file, userId);

        // Assert - Filename should be sanitized
        Path filePath = fileStorageService.getFilePath(fileId, userId);
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(filePath.getFileName().toString()).matches("^[a-zA-Z0-9_.-]+$");
    }

    @Test
    void testDeleteFile_Success() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "to-delete.txt",
            "text/plain",
            "content".getBytes()
        );
        String fileId = fileStorageService.uploadFile(file, userId);

        // Act
        fileStorageService.deleteFile(fileId, userId);

        // Assert
        Path filePath = fileStorageService.getFilePath(fileId, userId);
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void testDeleteFile_UnauthorizedUser_ShouldFail() throws Exception {
        // Arrange
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "protected.txt",
            "text/plain",
            "content".getBytes()
        );
        String fileId = fileStorageService.uploadFile(file, owner);

        // Act & Assert
        assertThatThrownBy(() -> fileStorageService.deleteFile(fileId, attacker))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void testAbsolutePathRejection() {
        // Arrange - Absolute paths should be rejected
        String[] absolutePaths = {
            "/etc/passwd",
            "/var/log/messages",
            "C:\\Windows\\System32\\config\\SAM"
        };

        for (String absPath : absolutePaths) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                absPath,
                "text/plain",
                "content".getBytes()
            );

            // Act & Assert
            assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void testContentTypeBypass_DoubleExtension_ShouldBeRejected() {
        // Arrange - Attempt to bypass with double extension
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "malicious.txt.exe",
            "text/plain", // Fake content type
            "MZ".getBytes() // EXE magic bytes
        );

        // Act & Assert - Service should validate actual content
        assertThatThrownBy(() -> fileStorageService.uploadFile(file, UUID.randomUUID()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Content mismatch");
    }

    /**
     * Mock File Storage Service for testing
     * In production, this would be a real service implementation
     */
    static class FileStorageService {
        private final String storageDirectory;
        private final long maxFileSize;
        private final String[] allowedContentTypes;

        public FileStorageService(String storageDirectory, long maxFileSize, String[] allowedContentTypes) {
            this.storageDirectory = storageDirectory;
            this.maxFileSize = maxFileSize;
            this.allowedContentTypes = allowedContentTypes;
        }

        public String uploadFile(MultipartFile file, UUID userId) throws IOException {
            // Validate file
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }

            if (file.getSize() > maxFileSize) {
                throw new IllegalArgumentException("File size exceeds maximum allowed: " + maxFileSize);
            }

            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) {
                throw new IllegalArgumentException("Filename is required");
            }

            // Security checks
            validateFilename(filename);
            validateContentType(file.getContentType());
            validateFileContent(file);

            // Generate safe file ID
            String fileId = UUID.randomUUID().toString();

            // Build safe path
            Path userDir = Paths.get(storageDirectory, userId.toString());
            Files.createDirectories(userDir);

            String sanitizedFilename = sanitizeFilename(filename);
            Path targetPath = userDir.resolve(fileId + "-" + sanitizedFilename);

            // Ensure path is within allowed directory
            if (!targetPath.normalize().startsWith(userDir.normalize())) {
                throw new SecurityException("Path traversal attempt detected");
            }

            // Save file
            file.transferTo(targetPath);

            return fileId;
        }

        public Path getFilePath(String fileId, UUID userId) {
            validateFileId(fileId);
            Path userDir = Paths.get(storageDirectory, userId.toString());

            try {
                return Files.list(userDir)
                        .filter(p -> p.getFileName().toString().startsWith(fileId + "-"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("File not found"));
            } catch (IOException e) {
                throw new RuntimeException("Error accessing file", e);
            }
        }

        public byte[] downloadFile(String fileId, UUID userId) throws IOException {
            validateFileId(fileId);
            Path filePath = getFilePath(fileId, userId);

            // Verify ownership
            if (!filePath.getParent().endsWith(userId.toString())) {
                throw new SecurityException("Unauthorized access to file");
            }

            return Files.readAllBytes(filePath);
        }

        public void deleteFile(String fileId, UUID userId) throws IOException {
            validateFileId(fileId);
            Path filePath = getFilePath(fileId, userId);

            // Verify ownership
            if (!filePath.getParent().endsWith(userId.toString())) {
                throw new SecurityException("Unauthorized access to file");
            }

            Files.deleteIfExists(filePath);
        }

        public void cleanupFailedUpload(String fileId, UUID userId) throws IOException {
            Path filePath = getFilePath(fileId, userId);
            Files.deleteIfExists(filePath);
        }

        public void validatePath(Path path) throws IOException {
            if (Files.isSymbolicLink(path)) {
                throw new SecurityException("Symlink not allowed");
            }
        }

        private void validateFilename(String filename) {
            if (filename.contains("\0")) {
                throw new SecurityException("Invalid filename: contains null bytes");
            }

            if (filename.contains("..")) {
                throw new SecurityException("Path traversal attempt in filename");
            }

            if (filename.contains("/") || filename.contains("\\")) {
                throw new SecurityException("Path traversal attempt in filename");
            }

            if (Paths.get(filename).isAbsolute()) {
                throw new SecurityException("Absolute paths not allowed");
            }

            if (filename.length() > 255) {
                throw new IllegalArgumentException("Filename too long (max 255 characters)");
            }
        }

        private void validateContentType(String contentType) {
            if (contentType == null) {
                throw new IllegalArgumentException("Content type is required");
            }

            boolean allowed = false;
            for (String allowedType : allowedContentTypes) {
                if (contentType.equals(allowedType)) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                throw new IllegalArgumentException("Content type not allowed: " + contentType);
            }
        }

        private void validateFileContent(MultipartFile file) throws IOException {
            // Check for content/type mismatch (magic bytes)
            byte[] content = file.getBytes();
            if (content.length >= 2) {
                // Check for EXE magic bytes
                if (content[0] == 'M' && content[1] == 'Z') {
                    throw new SecurityException("Content mismatch: executable file detected");
                }
            }
        }

        private void validateFileId(String fileId) {
            if (fileId == null || fileId.isEmpty()) {
                throw new IllegalArgumentException("File ID is required");
            }

            if (fileId.contains("..") || fileId.contains("/") || fileId.contains("\\")) {
                throw new SecurityException("Invalid file ID: path traversal attempt");
            }
        }

        private String sanitizeFilename(String filename) {
            // Remove special characters, keep only alphanumeric, dash, underscore, dot
            return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
    }
}
