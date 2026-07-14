/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 本地 1901 字典镜像分页查询 */
@Service
@RequiredArgsConstructor
public class YbPlatformDictPageQueryService {

    private final YbPlatformDictItemRepository dictItemRepository;

    /**
     * 分页查询字典镜像；keyword 非空时匹配 dict_value / dict_label。
     *
     * @param tenantId 租户 ID
     * @param dictType 字典类型
     * @param keyword  可选关键词
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果 */
    public Page<YbPlatformDictItem> page(
            Long tenantId,
            String dictType,
            String keyword,
            int pageNo,
            int pageSize) {
        Pageable pageable = PageRequest.of(
            Math.max(pageNo - 1, 0),
            Math.max(pageSize, 1),
            Sort.by(Sort.Order.asc("dictValue")));
        String resolvedType = trimToEmpty(dictType);
        String kw = trimToNull(keyword);
        if (kw == null) {
            return dictItemRepository.findByTenantIdAndDictTypeAndIsDeletedFalse(
                tenantId, resolvedType, pageable);
        }
        String likePattern = "%" + kw + "%";
        Specification<YbPlatformDictItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("dictType"), resolvedType));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.or(
                cb.like(root.get("dictValue"), likePattern),
                cb.like(root.get("dictLabel"), likePattern)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return dictItemRepository.findAll(spec, pageable);
    }
}
