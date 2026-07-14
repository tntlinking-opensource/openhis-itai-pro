/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.channel.service;

import com.openhis.whale.booking.domain.channel.entity.ChannelConfig;
import com.openhis.whale.booking.domain.channel.repository.ChannelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 预约渠道配置读侧查询。
 */
@Service
@RequiredArgsConstructor
public class ChannelQueryService {

    private final ChannelConfigRepository repository;

    /**
     * 按机构分页查询有效渠道配置。
     *
     * @param organizationId 机构主键（可选）
     * @param pageable 分页
     * @return 配置分页
     */
    public Page<ChannelConfig> pageByOrganization(Long organizationId, Pageable pageable) {
        Specification<ChannelConfig> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates.getExpressions().add(cb.equal(root.get("isDeleted"), false));
            if (organizationId != null) {
                predicates.getExpressions().add(cb.equal(root.get("organizationId"), organizationId));
            }
            return predicates;
        };
        return repository.findAll(spec, pageable);
    }

    /**
     * 按机构与渠道编码查有效配置。
     *
     * @param organizationId 机构主键
     * @param channelCode 渠道编码
     * @return 配置；无则 empty
     */
    public Optional<ChannelConfig> findByOrganizationAndCode(Long organizationId, String channelCode) {
        return repository.findByOrganizationIdAndChannelCodeAndIsDeletedFalse(organizationId, channelCode);
    }

    /**
     * 按主键加载有效渠道配置。
     *
     * @param id 配置主键
     * @return 有效配置或 null
     */
    public ChannelConfig loadById(Long id) {
        ChannelConfig config = repository.findById(id).orElse(null);
        // 已软删视为不存在
        if (config == null || Boolean.TRUE.equals(config.getIsDeleted())) {
            return null;
        }
        return config;
    }
}
