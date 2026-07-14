/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.service;

import com.openhis.whale.core.application.util.StringUtils;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.domain.share.constants.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本地 1901 字典镜像查询 */
@Service
@RequiredArgsConstructor
public class YbPlatformDictLookupService {

    private final YbPlatformDictItemRepository dictItemRepository;

    /**
     * 批量查询字典 value 对应的 label。
     *
     * @param tenantId   租户
     * @param dictType   字典类型
     * @param dictValues 待查 value 集合
     * @return value→label 映射（未命中则不包含该 key）
     */
    public Map<String, String> lookupLabels(Long tenantId, String dictType, Collection<String> dictValues) {
        Map<String, String> result = new HashMap<>();
        if (tenantId == null || isEmpty(dictType) || dictValues == null || dictValues.isEmpty()) {
            return result;
        }
        String resolvedType = dictType.trim();
        List<String> normalized = dictValues.stream()
            .filter(StringUtils::isNotEmpty)
            .map(v -> v.trim())
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            return result;
        }
        List<YbPlatformDictItem> rows = dictItemRepository
            .findByTenantIdAndDictTypeAndDictValueInAndIsDeletedFalse(tenantId, resolvedType, normalized);
        for (YbPlatformDictItem row : rows) {
            if (row != null && isNotEmpty(row.getDictValue()) && isNotEmpty(row.getDictLabel())) {
                result.put(row.getDictValue().trim(), row.getDictLabel().trim());
            }
        }
        return result;
    }

    /**
     * 参保地行政区划码批量转 label（{@link PlatformDictConstants#LOOKUP_INSUPLC_ADMDVS}）。
     *
     * @param tenantId    租户
     * @param admdvsCodes 区划码集合
     * @return 区划码→名称映射
     */
    public Map<String, String> lookupInsuplcAdmdvsLabels(Long tenantId, Collection<String> admdvsCodes) {
        return lookupLabels(tenantId, PlatformDictConstants.LOOKUP_INSUPLC_ADMDVS, admdvsCodes);
    }
}
