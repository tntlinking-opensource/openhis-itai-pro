/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 分诊评估类型枚举。
 */
public enum AssessmentType implements EnumInterface {
    /** NRS 疼痛评分（AssessmentType，编码 {@code PAIN_NRS}）。 */
    PAIN_NRS(0L, "PAIN_NRS", "", "", 1L),
    /** RTS 创伤评分（AssessmentType，编码 {@code TRAUMA_RTS}）。 */
    TRAUMA_RTS(0L, "TRAUMA_RTS", "", "", 2L),
    /** GCS 意识评分（AssessmentType，编码 {@code CONSCIOUSNESS_GCS}）。 */
    CONSCIOUSNESS_GCS(0L, "CONSCIOUSNESS_GCS", "", "", 3L),
    /** MEWS 早期预警评分（AssessmentType，编码 {@code EARLY_WARNING_MEWS}）。 */
    EARLY_WARNING_MEWS(0L, "EARLY_WARNING_MEWS", "", "", 4L),
    /** 其他（AssessmentType，编码 {@code OTHER}）。 */
    OTHER(0L, "OTHER", "", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    AssessmentType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

}
