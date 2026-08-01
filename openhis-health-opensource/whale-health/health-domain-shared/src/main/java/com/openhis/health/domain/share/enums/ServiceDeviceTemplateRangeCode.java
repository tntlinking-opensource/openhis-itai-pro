/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 诊疗耗材绑定范围编码枚举。
 *
 * <p>配置与医嘱消费按门诊/住院区分模板；{@code HOSPITAL} 仅兼容历史存量数据。</p>
 */
public enum ServiceDeviceTemplateRangeCode implements EnumInterface {
    /** 门诊范围（ServiceDeviceTemplateRangeCode，编码 {@code OUTPATIENT}）。 */
    OUTPATIENT(0L, "OUTPATIENT", "门诊", "", 1L),
    /** 住院范围（ServiceDeviceTemplateRangeCode，编码 {@code INPATIENT}）。 */
    INPATIENT(0L, "INPATIENT", "住院", "", 2L),
    /**
     * 全院范围（ServiceDeviceTemplateRangeCode，编码 {@code HOSPITAL}）。
     *
     * <p>历史兼容码：新配置不再作为默认选项；存量行可在配置页查看，医嘱侧不再按此码匹配。</p>
     */
    HOSPITAL(0L, "HOSPITAL", "全院(历史)", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ServiceDeviceTemplateRangeCode(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
