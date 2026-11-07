package com.englow3.assessment.helper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.storage.ImageFileHeader;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.PresignedUrlResolver;

/**
 * A Writing Task 1's chart in object storage: storing an upload, and signing it for the screen that is about to show
 * it. The object key itself never reaches a browser.
 */
@Component
public class TaskImages {

    private static final Map<String, String> EXTENSIONS = Map.of("image/png", "png", "image/jpeg", "jpg");

    private final PresignedUrlResolver presignedUrls;
    private final ObjectStorageClient storage;
    private final String bucket;
    private final Duration ttl;

    public TaskImages(PresignedUrlResolver presignedUrls, ObjectStorageClient storage,
            @Value("${app.storage.learning-bucket}") String bucket,
            @Value("${app.storage.learning-media-url-ttl:PT3H}") Duration ttl) {
        this.presignedUrls = presignedUrls;
        this.storage = storage;
        this.bucket = bucket;
        this.ttl = ttl;
    }

    public AssessmentTaskResult signed(AssessmentTaskResult task) {
        return task == null || task.imageObjectKey() == null ? task
                : task.withImageUrl(presignedUrls.resolve(bucket, task.imageObjectKey(), ttl));
    }

    /**
     * Stores a PNG or JPEG for the task and returns its key. The bytes are checked, not the browser's word for the
     * type. Network I/O: call it outside a transaction.
     */
    public String upload(UUID taskId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("TASK_IMAGE_REQUIRED", "No image was uploaded");
        }
        try {
            return store(taskId, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException unreadable) {
            throw new BadRequestException("TASK_IMAGE_UNREADABLE", "The uploaded image could not be read");
        }
    }

    /** The same check and storage, for an image the server drew itself (a generated Task 1 chart). */
    public String upload(UUID taskId, byte[] image, String contentType) {
        if (image == null || image.length == 0) {
            throw new BadRequestException("TASK_IMAGE_REQUIRED", "No image was uploaded");
        }
        return store(taskId, new java.io.ByteArrayInputStream(image), image.length, contentType);
    }

    private String store(UUID taskId, java.io.InputStream input, long size, String contentType) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BadRequestException("TASK_IMAGE_TYPE_NOT_SUPPORTED", "Only PNG and JPEG charts are accepted");
        }
        String key = "assessment-tasks/%s/%s.%s".formatted(taskId, UUID.randomUUID(), extension);
        try {
            var content = new BufferedInputStream(input);
            content.mark(ImageFileHeader.LENGTH);
            byte[] header = content.readNBytes(ImageFileHeader.LENGTH);
            content.reset();
            if (!ImageFileHeader.matches(header, contentType)) {
                throw new BadRequestException("TASK_IMAGE_CONTENT_MISMATCH",
                        "The file does not match its declared format");
            }
            storage.upload(bucket, key, content, size, contentType);
        } catch (IOException unreadable) {
            throw new BadRequestException("TASK_IMAGE_UNREADABLE", "The uploaded image could not be read");
        }
        return key;
    }
}
