/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.media.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.MediaSource;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 关键图/截图对象键索引聚合根（{@code pacs_media}）。
 *
 * <p>不保存字节；仅保存对象键与内容类型。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_media")
public class Media extends BaseAuditedEntity {

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";

    /** 所属检查工单。 */
    @Column(name = "work_item_id", nullable = false)
    private Long workItemId;

    /** 对象存储键；租户内唯一。 */
    @Column(name = "object_key", nullable = false)
    private String objectKey;

    /** 内容类型；仅 image/jpeg 或 image/png。 */
    @Column(name = "content_type", nullable = false)
    private String contentType;

    /** 来源枚举 code。 */
    @Column(name = "source_enum", nullable = false)
    private String sourceEnum;

    /**
     * 校验并写入关键图索引字段（BR-IMG-03）。
     *
     * @param workItemId 工单主键
     * @param objectKey 对象键
     * @param contentType 内容类型
     * @param sourceEnum 来源枚举 code
     */
    public void bindKeyImage(Long workItemId, String objectKey, String contentType, String sourceEnum) {
        if (workItemId == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(objectKey)) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "objectKey 不能为空");
        }
        this.assertContentType(contentType);
        this.assertSourceEnum(sourceEnum);
        this.workItemId = workItemId;
        this.objectKey = objectKey.trim();
        this.contentType = contentType.trim();
        this.sourceEnum = sourceEnum.trim();
    }

    /**
     * 校验 contentType 仅 jpeg/png。
     *
     * @param contentType 内容类型
     */
    public void assertContentType(String contentType) {
        if (StringUtils.isEmpty(contentType)) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "contentType 不能为空");
        }
        String normalized = contentType.trim();
        if (CONTENT_TYPE_JPEG.equals(normalized) || CONTENT_TYPE_PNG.equals(normalized)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "contentType 仅支持 image/jpeg、image/png");
    }

    /**
     * 校验 sourceEnum 属于 {@link MediaSource}。
     *
     * @param sourceEnum 来源枚举 code
     */
    public void assertSourceEnum(String sourceEnum) {
        if (StringUtils.isEmpty(sourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "sourceEnum 不能为空");
        }
        MediaSource source = EnumUtil.fromCode(MediaSource.class, sourceEnum.trim());
        if (source == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "sourceEnum 非法");
        }
    }
}
