/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;

/**
 * 幂等确保门诊慢特病种目录后的结果投影。
 */
@Getter
public class EnsureChronicSpecOpspResult {

    /** 目录主键。 */
    private final Long conditionDefinitionId;

    /**
     * 本次是否对 {@code chronic_spec} 发生写操作（新建或启用）。
     * <p>复用已启用行时为 {@code false}。</p>
     */
    private final boolean catalogWritten;

    /**
     * @param conditionDefinitionId 目录主键
     * @param catalogWritten        是否本次写库
     */
    public EnsureChronicSpecOpspResult(Long conditionDefinitionId, boolean catalogWritten) {
        this.conditionDefinitionId = conditionDefinitionId;
        this.catalogWritten = catalogWritten;
    }
}
