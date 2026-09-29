/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.media.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.pacsimaging.domain.media.command.DeleteKeyImageCommand;
import com.openhis.whale.pacsimaging.domain.media.command.SaveKeyImageCommand;
import com.openhis.whale.pacsimaging.domain.media.entity.Media;
import com.openhis.whale.pacsimaging.domain.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Media 写侧领域服务。
 *
 * <p>职责：关键图对象键索引的增删；不上传/删除对象存储字节。</p>
 */
@Service
@RequiredArgsConstructor
public class MediaDomainService {

    private final MediaRepository mediaRepository;

    /**
     * 保存关键图索引。
     *
     * <p>注释锚点：校验类型 → 按 objectKey 查 → 插入。</p>
     *
     * @param cmd 保存命令
     * @return 已有或新插入的聚合根
     */
    public Media saveKeyImage(SaveKeyImageCommand cmd) {
        this.validateSaveCommand(cmd);
        // 校验类型
        Media probe = new Media();
        probe.assertContentType(cmd.getContentType());
        probe.assertSourceEnum(cmd.getSourceEnum());
        // 按 objectKey 查（幂等）
        Optional<Media> existing = mediaRepository
            .findByObjectKeyAndTenantId(cmd.getObjectKey().trim(), cmd.getTenantId());
        if (existing.isPresent()) {
            return existing.get();
        }
        // 插入
        Media media = new Media();
        media.bindKeyImage(cmd.getWorkItemId(), cmd.getObjectKey(), cmd.getContentType(), cmd.getSourceEnum());
        media.setTenantId(cmd.getTenantId());
        media.setCreatedBy(cmd.getOperatorId());
        media.setUpdatedBy(cmd.getOperatorId());
        return mediaRepository.save(media);
    }

    /**
     * 软删关键图索引。
     *
     * @param cmd 删除命令
     */
    public void deleteKeyImage(DeleteKeyImageCommand cmd) {
        this.validateDeleteCommand(cmd);
        Media media = mediaRepository.findByIdAndWorkItemId(cmd.getMediaId(), cmd.getWorkItemId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_NOT_FOUND, "关键图索引不存在或不属于该工单"));
        media.setIsDeleted(Boolean.TRUE);
        media.setDeletedAt(LocalDateTime.now());
        media.setDeletedBy(cmd.getOperatorId());
        media.setUpdatedBy(cmd.getOperatorId());
        mediaRepository.save(media);
    }

    private void validateSaveCommand(SaveKeyImageCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "命令不能为空");
        }
        if (cmd.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "workItemId 不能为空");
        }
        if (StringUtils.isEmpty(cmd.getObjectKey())) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "objectKey 不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "tenantId 不能为空");
        }
        if (cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_TYPE_INVALID, "operatorId 不能为空");
        }
    }

    private void validateDeleteCommand(DeleteKeyImageCommand cmd) {
        if (cmd == null || cmd.getMediaId() == null || cmd.getWorkItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_NOT_FOUND, "mediaId/workItemId 不能为空");
        }
        if (cmd.getTenantId() == null || cmd.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_KEY_IMAGE_NOT_FOUND, "tenantId/operatorId 不能为空");
        }
    }
}
