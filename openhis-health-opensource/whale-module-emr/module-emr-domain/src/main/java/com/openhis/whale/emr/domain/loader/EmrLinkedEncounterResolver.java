/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.loader;

import java.util.Collections;
import java.util.List;

/**
 * 解析当前就诊与门诊/住院关联对端的就诊 ID 并集（由 HIS bridge 实现）。
 *
 * <p>用于病历历史列表按 {@code amb_encounter_id} 互通查看。</p>
 */
public interface EmrLinkedEncounterResolver {

    /**
     * @param encounterId 当前就诊标识（字符串）；空则返回空列表
     * @return 当前 ∪ 关联对端的就诊 ID 字符串列表
     */
    List<String> resolveLinkedEncounterIds(String encounterId);

    /**
     * 无 HIS 桥接时的回退：仅返回当前就诊自身。
     */
    static EmrLinkedEncounterResolver noop() {
        return encounterId -> {
            if (encounterId == null || encounterId.isBlank()) {
                return Collections.emptyList();
            }
            return List.of(encounterId.trim());
        };
    }
}
