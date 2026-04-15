package com.ycy.aiapplication.knowledge.toolkit;

import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.ycy.aiapplication.knowledge.config.OSSConfiguration;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OSS storage helper.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public final class AliOSSUtils {

    private static final long NO_LIMIT = -1L;
    private static final long GET_OBJECT_TIMEOUT_SECONDS = 5L;

    private final OSSConfiguration ossConfig;
    private final OSS ossClient;

    public StoredObject upload(MultipartFile file, String kbId, long maxBytes) {
        if (maxBytes == NO_LIMIT) {
            return doUpload(file, kbId);
        }
        return doUploadWithLimit(file, kbId, maxBytes);
    }

    /**
     * 无限制上传
     */
    public StoredObject doUpload(MultipartFile file, String kbId) {
        return doUploadInternal(file, kbId, null);
    }

    /**
     * 带有字节数量限制上传
     */
    public StoredObject doUploadWithLimit(MultipartFile file, String kbId, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("文件上传限制大于0字节");
        }
        return doUploadInternal(file, kbId, maxBytes);
    }

    /**
     * 内部上传方法
     */
    private StoredObject doUploadInternal(MultipartFile file, String kbId, Long maxBytes) {
        validateFile(file, kbId, maxBytes);
        try (InputStream inputStream = file.getInputStream()) {
            String fileName = buildFileName(file, kbId);
            ossClient.putObject(ossConfig.getBucketName(), fileName, inputStream);
            return StoredObject.builder()
                    .objectKey(fileName)
                    .fileUrl(buildFileUrl(fileName))
                    .build();
        } catch (IOException e) {
            throw new ClientException("上传文件失败", e);
        }
    }

    /**
     * 校验参数
     * 为了保障文件完整性，这里文件超过最大字节数量限制则抛出异常
     * @param file 文件
     * @param kbId 知识库id
     * @param maxBytes  最大字节数
     */
    private void validateFile(MultipartFile file, String kbId, Long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!StringUtils.hasText(kbId)) {
            throw new IllegalArgumentException("知识库id不能为空");
        }
        if (maxBytes != null && file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件大小大于设定的最大上传限制：" + maxBytes + " 字节");
        }
    }

    private String buildFileName(MultipartFile file, String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new IllegalArgumentException("知识库id不能为空");
        }
        String originalFileName = file.getOriginalFilename();
        String extension = extractExtension(originalFileName);
        return kbId + "_" + IdUtil.getSnowflakeNextIdStr() + extension;
    }

    private String extractExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            return "";
        }
        return originalFileName.substring(dotIndex);
    }

    public InputStream getObjectStream(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        try {
            return CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            OSSObject object = ossClient.getObject(ossConfig.getBucketName(), objectKey);
                            return object.getObjectContent();
                        } catch (RuntimeException ex) {
                            throw new CompletionException(ex);
                        }
                    })
                    .get(GET_OBJECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            log.error("Read OSS object timed out, bucketName={}, objectKey={}, timeoutSeconds={}",
                    ossConfig.getBucketName(), objectKey, GET_OBJECT_TIMEOUT_SECONDS, ex);
            throw new ClientException("Read OSS object timed out: " + objectKey, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Read OSS object interrupted, bucketName={}, objectKey={}",
                    ossConfig.getBucketName(), objectKey, ex);
            throw new ClientException("Read OSS object interrupted: " + objectKey, ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ClientException("读取 OSS 文件失败", ex);
        }
    }

    private String buildFileUrl(String fileName) {
        String endPoint = ossConfig.getEndPoint();
        String[] endPointParts = endPoint.split("//", 2);
        if (endPointParts.length != 2) {
            return endPoint + "/" + fileName;
        }
        return endPointParts[0] + "//" + ossConfig.getBucketName() + "." + endPointParts[1] + "/" + fileName;
    }

    @Getter
    @Builder
    public static class StoredObject {
        private final String objectKey;
        private final String fileUrl;
    }

}
