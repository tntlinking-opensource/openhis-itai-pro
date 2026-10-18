/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 首页诊断行类型（院内 diag_type_code）。
 */
public enum HomepageDiagType implements EnumInterface {
/** 出院主诊断（HomepageDiagType，持久化编码 {@code discharge_main}）。 */
    DISCHARGE_MAIN(0L, "discharge_main", "出院主诊断", "", 0L),
/** 出院其他诊断（HomepageDiagType，持久化编码 {@code discharge_other}）。 */
    DISCHARGE_OTHER(0L, "discharge_other", "出院其他诊断", "", 1L),
/** 入院主诊断（HomepageDiagType，持久化编码 {@code admission_main}）。 */
    ADMISSION_MAIN(0L, "admission_main", "入院主诊断", "", 2L),
/** 入院其他诊断（HomepageDiagType，持久化编码 {@code admission_other}）。 */
    ADMISSION_OTHER(0L, "admission_other", "入院其他诊断", "", 3L),
/** 病理诊断（HomepageDiagType，持久化编码 {@code pathology}）。 */
    PATHOLOGY(0L, "pathology", "病理诊断", "", 4L),
    /**
     * 损伤中毒外部原因。
     */
    INJURY_POISON(0L, "injury_poison", "损伤中毒", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    HomepageDiagType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
