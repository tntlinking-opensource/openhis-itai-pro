/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.service;

import com.zxplat.plugin.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.zxplat.plugin.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import com.zxplat.plugin.chs.domain.platformdict.repository.YbPlatformDictBulkUpsertRepository;
import com.zxplat.plugin.chs.domain.platformdict.repository.YbPlatformDictSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 1901 平台字典写侧（批量 upsert + 同步日志） */
@Service
@RequiredArgsConstructor
public class YbPlatformDictDomainService {

    private final YbPlatformDictBulkUpsertRepository bulkUpsertRepository;
    private final YbPlatformDictSyncLogRepository syncLogRepository;

    /**
     * 1901 拉取结果批量 upsert 至本地镜像。
     *
     * @param tenantId         租户
     * @param operatorId       操作人
     * @param sourceContractId 来源契约
     * @param queryAdmdvs      查询区划
     * @param syncedAt         同步时间戳
     * @param rows             字典行
     * @return 实际 upsert 行数
     */
    public int bulkUpsert(
            Long tenantId,
            Long operatorId,
            Long sourceContractId,
            String queryAdmdvs,
            LocalDateTime syncedAt,
            List<UpsertPlatformDictRowCommand> rows) {
        return bulkUpsertRepository.bulkUpsert(
            tenantId, operatorId, sourceContractId, queryAdmdvs, syncedAt, rows);
    }

    /**
     * 持久化 1901 手动同步日志。
     *
     * @param syncLog 同步日志实体
     * @return 保存后的实体
     */
    public YbPlatformDictSyncLog saveSyncLog(YbPlatformDictSyncLog syncLog) {
        return syncLogRepository.save(syncLog);
    }
}
