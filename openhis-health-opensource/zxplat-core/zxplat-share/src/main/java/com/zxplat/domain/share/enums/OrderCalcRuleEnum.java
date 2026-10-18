/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 门诊发药/计价取整规则：药品目录中「门诊拆分属性」与「住院临时医嘱拆分属性」共用同一套编码。
 *
 * <p>持久化与下拉选项的 {@link #getCode()} 使用数字字符串 {@code "1"}～{@code "4"}，与业务序号一致。</p>
 */
public enum OrderCalcRuleEnum implements EnumInterface {

    /**
     * 拆零单位 - 单次取整（可拆）
     */
    SINGLE_SPLIT_CEIL(0L, "1", "拆零单位 - 单次取整（可拆）", "", 1L),

    /**
     * 包装单位 - 整包不拆
     */
    PACKAGE_NO_SPLIT(0L, "2", "包装单位 - 整包不拆", "", 2L),

    /**
     * 拆零单位 - 总量取整（可拆）
     */
    TOTAL_SPLIT_CEIL(0L, "3", "拆零单位 - 总量取整（可拆）", "", 3L),

    /**
     * 包装单位 - 单次取整
     */
    PACKAGE_SINGLE_CEIL(0L, "4", "包装单位 - 单次取整", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    OrderCalcRuleEnum(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
