/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.service;

import com.openhis.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 1901 平台字典同步日志只读查询 */
@Service
@RequiredArgsConstructor
public class YbPlatformDictSyncLogQueryService {

    private final YbPlatformDictSyncLogRepository syncLogRepository;

    /**
     * 查询指定字典类型最近一次同步日志。
     *
     * @param tenantId 租户
     * @param dictType 字典类型
     * @return 最新同步日志，参数无效或无记录时 empty
     */
    public Optional<YbPlatformDictSyncLog> findLatestByDictType(Long tenantId, String dictType) {
        if (tenantId == null || dictType == null || dictType.isBlank()) {
            return Optional.empty();
        }
        return syncLogRepository.findFirstByTenantIdAndDictTypeAndIsDeletedFalseOrderByStartedAtDesc(
            tenantId, dictType.trim());
    }
}
