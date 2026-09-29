/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.port;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关键图存储默认内存实现：单测与未开 MinIO 时使用。
 */
@Component
@ConditionalOnProperty(
    name = "openhis.modules.pacs.imaging.minio.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class InMemoryMinioKeyImageStore implements MinioKeyImageStore {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String put(Long workItemId, String contentType, byte[] bytes) {
        if (workItemId == null || bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_STORE_FAILED, "关键图写入参数非法");
        }
        String extension = this.resolveExtension(contentType);
        String objectKey = "key-images/" + workItemId + "/" + UUID.randomUUID() + extension;
        this.objects.put(objectKey, bytes);
        return objectKey;
    }

    @Override
    public void delete(String objectKey) {
        if (StringUtils.isEmpty(objectKey)) {
            return;
        }
        this.objects.remove(objectKey.trim());
    }

    @Override
    public String presignGet(String objectKey) {
        if (StringUtils.isEmpty(objectKey)) {
            return "";
        }
        return "memory://" + objectKey.trim();
    }

    /**
     * 按 contentType 解析对象键后缀。
     *
     * @param contentType 内容类型
     * @return 后缀（含点）
     */
    private String resolveExtension(String contentType) {
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        return ".jpg";
    }
}
