/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 汇总发药单状态（读模型 {@code status_enum} 持久化码，与历史整数码字符串一致）。
 */
public enum MedicationSummaryStatus implements EnumInterface {

/** 已汇总待发药（MedicationSummaryStatus，持久化编码 {@code 10}）。 */
    SUMMARIZED(0L, "10", "已汇总待发药", "", 10L),

/** 汇总发药完成（MedicationSummaryStatus，持久化编码 {@code 20}）。 */
    COMPLETED(0L, "20", "汇总发药完成", "", 20L),

/** 已取消（MedicationSummaryStatus，持久化编码 {@code 90}）。 */
    CANCELLED(0L, "90", "已取消", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    MedicationSummaryStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
