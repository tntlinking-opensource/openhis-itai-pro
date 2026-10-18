/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.channel.service;

import com.zxplat.plugin.booking.domain.channel.entity.ChannelConfig;
import com.zxplat.plugin.booking.domain.channel.repository.ChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 预约渠道配置写侧。
 */
@Service
@RequiredArgsConstructor
public class ChannelDomainService {

    private final ChannelConfigRepository repository;

    /**
     * 保存渠道配置。
     *
     * @param config 渠道实体
     * @param operatorId 操作人
     * @return 落库后的配置
     */
    public ChannelConfig save(ChannelConfig config, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        // 新建时补齐审计与软删标记
        if (config.getId() == null) {
            config.setCreatedAt(now);
            config.setCreatedBy(operatorId);
            config.setIsDeleted(Boolean.FALSE);
        }
        config.setUpdatedAt(now);
        config.setUpdatedBy(operatorId);
        // 持久化
        return repository.save(config);
    }

    /**
     * 软删渠道配置。
     *
     * @param id 配置主键
     * @param operatorId 操作人
     */
    public void softDelete(Long id, Long operatorId) {
        repository.findById(id).ifPresent(config -> {
            config.setIsDeleted(Boolean.TRUE);
            config.setDeletedAt(LocalDateTime.now());
            config.setDeletedBy(operatorId);
            repository.save(config);
        });
    }
}
