/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.port;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.pacsimaging.application.config.ImagingMiddlewareProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 关键图存储客户端（序号 57 · F3=K4 桶 {@code key-images}）。
 */
@Component
@ConditionalOnProperty(name = "openhis.modules.pacs.imaging.minio.enabled", havingValue = "true")
public class MinioKeyImageStoreClient implements MinioKeyImageStore {

    private final ImagingMiddlewareProperties properties;
    private MinioClient minioClient;

    public MinioKeyImageStoreClient(ImagingMiddlewareProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化客户端并确保桶存在。
     */
    @PostConstruct
    public void init() {
        ImagingMiddlewareProperties.Minio minio = properties.getMinio();
        this.minioClient = MinioClient.builder()
            .endpoint(minio.getEndpoint())
            .credentials(minio.getAccessKey(), minio.getSecretKey())
            .build();
        this.ensureBucket(minio.getBucket());
    }

    @Override
    public String put(Long workItemId, String contentType, byte[] bytes) {
        if (workItemId == null || bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "关键图写入参数非法");
        }
        String type;
        if (StringUtils.isEmpty(contentType)) {
            type = "image/jpeg";
        } else {
            type = contentType.trim();
        }
        String objectKey = "key-images/" + workItemId + "/" + UUID.randomUUID() + this.resolveExtension(type);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(type)
                .build());
            return objectKey;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "MinIO 写入失败: " + ex.getMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        if (StringUtils.isEmpty(objectKey)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey.trim())
                .build());
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "MinIO 删除失败: " + ex.getMessage());
        }
    }

    @Override
    public String presignGet(String objectKey) {
        if (StringUtils.isEmpty(objectKey)) {
            return "";
        }
        try {
            int expiry = Math.max(60, properties.getMinio().getPresignExpirySeconds());
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(properties.getMinio().getBucket())
                .object(objectKey.trim())
                .expiry(expiry, TimeUnit.SECONDS)
                .build());
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "MinIO 预签名失败: " + ex.getMessage());
        }
    }

    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 桶初始化失败: " + ex.getMessage(), ex);
        }
    }

    private String resolveExtension(String contentType) {
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        return ".jpg";
    }
}
