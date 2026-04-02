package com.ycy.javis.knowledge.toolkit;

import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.ycy.javis.knowledge.config.OSSConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * OSS storage helper.
 */
@Component
@RequiredArgsConstructor
public final class AliOSSUtils {

    private static final long NO_LIMIT = -1L;

    private final OSSConfig ossConfig;
    private final OSS ossClient;

    public String upload(MultipartFile file, String kbId, long maxBytes) {
        if (maxBytes == NO_LIMIT) {
            return doUpload(file, kbId);
        }
        return doUploadWithLimit(file, kbId, maxBytes);
    }

    /**
     * 无限制上传
     */
    public String doUpload(MultipartFile file, String kbId) {
        return doUploadInternal(file, kbId, null);
    }

    /**
     * 带有字节数量限制上传
     */
    public String doUploadWithLimit(MultipartFile file, String kbId, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("文件上传限制大于0字节");
        }
        return doUploadInternal(file, kbId, maxBytes);
    }

    /**
     * 内部上传方法
     */
    private String doUploadInternal(MultipartFile file, String kbId, Long maxBytes) {
        validateFile(file, kbId, maxBytes);
        try (InputStream inputStream = file.getInputStream()) {
            String fileName = buildFileName(file, kbId);
            ossClient.putObject(ossConfig.getBucketName(), fileName, inputStream);
            return buildFileUrl(fileName);
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

    private String buildFileUrl(String fileName) {
        String endPoint = ossConfig.getEndPoint();
        String[] endPointParts = endPoint.split("//", 2);
        if (endPointParts.length != 2) {
            return endPoint + "/" + fileName;
        }
        return endPointParts[0] + "//" + ossConfig.getBucketName() + "." + endPointParts[1] + "/" + fileName;
    }

}
