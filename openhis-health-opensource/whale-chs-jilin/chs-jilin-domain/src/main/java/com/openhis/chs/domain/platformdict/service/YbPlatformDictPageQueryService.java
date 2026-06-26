/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.service;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictItemRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** 本地 1901 字典镜像分页查询。 */
@Service
@RequiredArgsConstructor
public class YbPlatformDictPageQueryService {

    private final YbPlatformDictItemRepository dictItemRepository;

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
        String resolvedType = StringUtils.trimToEmpty(dictType);
        String kw = StringUtils.trimToNull(keyword);
        if (kw == null) {
            return dictItemRepository.findByTenantIdAndDictTypeAndIsDeletedFalse(
                tenantId, resolvedType, pageable);
        }
        return dictItemRepository.findByTenantIdAndDictTypeAndKeywordAndIsDeletedFalse(
            tenantId, resolvedType, "%" + kw + "%", pageable);
    }
}
