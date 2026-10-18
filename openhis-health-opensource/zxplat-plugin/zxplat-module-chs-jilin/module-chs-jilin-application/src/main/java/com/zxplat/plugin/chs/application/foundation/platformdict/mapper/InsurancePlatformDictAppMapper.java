/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.platformdict.mapper;

import com.zxplat.plugin.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.zxplat.plugin.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import com.zxplat.plugin.chs.domain.platformdict.result.PlatformDictItemResult;
import com.zxplat.plugin.chs.domain.platformdict.result.PlatformDictSyncLogResult;

/**
 * 平台字典管理页：领域实体与 UI 结果映射。
 */
public final class InsurancePlatformDictAppMapper {

    private InsurancePlatformDictAppMapper() {
    }

    /** 将字典镜像实体映射为分页行结果。 */
    public static PlatformDictItemResult toItemResult(YbPlatformDictItem item) {
        return new PlatformDictItemResult()
            .setId(item.getId())
            .setDictType(item.getDictType())
            .setDictValue(item.getDictValue())
            .setDictLabel(item.getDictLabel())
            .setParentValue(item.getParentValue())
            .setSortOrder(item.getSortOrder())
            .setValiFlag(item.getValiFlag())
            .setQueryAdmdvs(item.getQueryAdmdvs())
            .setSyncedAt(item.getSyncedAt());
    }

    /** 将同步日志实体映射为 UI 出参。 */
    public static PlatformDictSyncLogResult toSyncLogResult(YbPlatformDictSyncLog log) {
        return new PlatformDictSyncLogResult()
            .setDictType(log.getDictType())
            .setQueryAdmdvs(log.getQueryAdmdvs())
            .setRowsUpserted(log.getRowsUpserted())
            .setStatus(log.getStatus())
            .setErrorMessage(log.getErrorMessage())
            .setStartedAt(log.getStartedAt())
            .setFinishedAt(log.getFinishedAt());
    }
}
