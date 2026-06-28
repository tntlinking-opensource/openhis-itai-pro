/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.emr;

import java.util.Map;

/**
 * 从 EMR 文书解析「诊断编码 → 入院病情码」（EMR Module 提供能力）。
 */
public interface EmrAdmissionConditionExtension {

    default boolean isEnabled() {
        return false;
    }

    /**
     * 按就诊解析入院病情映射。
     *
     * @param encounterId 就诊主键
     * @return ICD 编码（大写）→ 入院病情码；无数据时空 Map
     */
    default Map<String, String> resolveByEncounterId(Long encounterId) {
        return Map.of();
    }
}
