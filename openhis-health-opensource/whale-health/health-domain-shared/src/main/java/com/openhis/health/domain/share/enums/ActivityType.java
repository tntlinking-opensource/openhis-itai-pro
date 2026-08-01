/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 诊疗活动类型（{@code ActivityDefinition.type_enum} / 目录维护下拉）。
 *
 * <p>持久化存 {@link #getCode()} 字符串；展示名见 {@link #getDisplay()}。
 * 与 {@code DM-WKF-001} §4.2 对齐；初始化接口 {@code typeEnumOptions} 由本枚举直接生成。</p>
 *
 * <p>住院嘱托医嘱<strong>不</strong>使用本枚举新增值，而以
 * {@code ActivityDefinition.category_code=52}（{@code ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST}）识别。</p>
 */
public enum ActivityType implements EnumInterface {
    /** 检验（ActivityType，编码 {@code 1}）。 */
    PROOF(0L, "1", "检验", "", 1L),
    /** 检查（ActivityType，编码 {@code 2}）。 */
    TEST(0L, "2", "检查", "", 2L),
    /** 护理（ActivityType，编码 {@code 3}）。 */
    CARE(0L, "3", "护理", "", 3L),
    /** 手术（ActivityType，编码 {@code 4}）。 */
    OPERATION(0L, "4", "手术", "", 4L),
    /** 其他（ActivityType，编码 {@code 5}）。 */
    OTHER(0L, "5", "其他", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    ActivityType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
