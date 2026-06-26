/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.service;

import com.openhis.chs.domain.platformdict.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictItemRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本地 1901 字典镜像查询。 */
@Service
@RequiredArgsConstructor
public class YbPlatformDictLookupService {

    private final YbPlatformDictItemRepository dictItemRepository;

    public Map<String, String> lookupLabels(Long tenantId, String dictType, Collection<String> dictValues) {
        Map<String, String> result = new HashMap<>();
        if (tenantId == null || StringUtils.isBlank(dictType) || dictValues == null || dictValues.isEmpty()) {
            return result;
        }
        String resolvedType = dictType.trim();
        List<String> normalized = dictValues.stream()
            .filter(StringUtils::isNotBlank)
            .map(v -> v.trim())
            .distinct()
            .toList();
        if (normalized.isEmpty()) {
            return result;
        }
        List<YbPlatformDictItem> rows = dictItemRepository
            .findByTenantIdAndDictTypeAndDictValueInAndIsDeletedFalse(tenantId, resolvedType, normalized);
        for (YbPlatformDictItem row : rows) {
            if (row != null && StringUtils.isNotBlank(row.getDictValue()) && StringUtils.isNotBlank(row.getDictLabel())) {
                result.put(row.getDictValue().trim(), row.getDictLabel().trim());
            }
        }
        return result;
    }

    public Map<String, String> lookupInsuplcAdmdvsLabels(Long tenantId, Collection<String> admdvsCodes) {
        return lookupLabels(tenantId, PlatformDictConstants.LOOKUP_INSUPLC_ADMDVS, admdvsCodes);
    }
}
