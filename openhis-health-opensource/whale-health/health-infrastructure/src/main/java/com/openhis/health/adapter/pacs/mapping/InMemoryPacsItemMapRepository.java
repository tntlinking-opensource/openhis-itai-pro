/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.mapping;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * activityId ↔ 现场 EXESCODE 私有对照（仅 adapter 内存；禁止进 health Flyway）。
 */
public class InMemoryPacsItemMapRepository {

    private final ConcurrentHashMap<Long, String> activityToExternal = new ConcurrentHashMap<>();

    /**
     * 写入或覆盖对照。
     *
     * @param activityId ActivityDefinition 主键
     * @param externalCode 现场码
     */
    public void put(Long activityId, String externalCode) {
        if (activityId == null || externalCode == null) {
            return;
        }
        activityToExternal.put(activityId, externalCode);
    }

    /**
     * 按 activity 查现场码。
     *
     * @param activityId ActivityDefinition 主键
     * @return 现场码
     */
    public Optional<String> findExternalCode(Long activityId) {
        if (activityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activityToExternal.get(activityId));
    }
}
