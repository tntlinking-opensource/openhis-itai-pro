/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.media.queries;

import com.openhis.whale.pacsimaging.domain.media.entity.Media;
import com.openhis.whale.pacsimaging.domain.media.queries.model.MediaQueryModel;
import com.openhis.whale.pacsimaging.domain.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Media 读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class MediaQueryService {

    private final MediaRepository mediaRepository;

    /**
     * 按工单列出关键图索引（createdAt DESC）。
     *
     * <p>注释锚点：软删过滤 → 一次仓储读取 → 组装 QueryModel。</p>
     *
     * @param workItemId 工单主键
     * @return 读模型列表
     */
    public List<MediaQueryModel> listByWorkItemId(Long workItemId) {
        if (workItemId == null) {
            return Collections.emptyList();
        }
        List<Media> rows = mediaRepository.findByWorkItemIdOrderByCreatedAtDesc(workItemId);
        List<MediaQueryModel> models = new ArrayList<>();
        for (Media media : rows) {
            models.add(this.toModel(media));
        }
        return models;
    }

    /**
     * 按工单统计关键图索引行数（软删后）。
     *
     * @param workItemId 工单主键
     * @return 行数
     */
    public long countByWorkItemId(Long workItemId) {
        if (workItemId == null) {
            return 0L;
        }
        return mediaRepository.countByWorkItemId(workItemId);
    }

    private MediaQueryModel toModel(Media media) {
        MediaQueryModel model = new MediaQueryModel();
        model.setId(media.getId());
        model.setWorkItemId(media.getWorkItemId());
        model.setObjectKey(media.getObjectKey());
        model.setContentType(media.getContentType());
        model.setSourceEnum(media.getSourceEnum());
        model.setCreatedAt(media.getCreatedAt());
        return model;
    }
}
