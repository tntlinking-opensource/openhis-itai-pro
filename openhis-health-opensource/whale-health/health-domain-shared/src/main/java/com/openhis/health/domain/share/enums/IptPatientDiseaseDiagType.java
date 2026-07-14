/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 患者疾病诊断类型（住院患者疾病诊断类型代码口径）。
 *
 * <p>与术语编码体系名 {@code ipt_dise_type_code} 及库内种子 {@code ter_code_system.id = 384}
 * 下概念 {@code code}/{@code display} 对齐，供就诊诊断快照行将持久化码解析为展示文案。</p>
 *
 */
public enum IptPatientDiseaseDiagType implements EnumInterface {

/** 出院诊断（IptPatientDiseaseDiagType，持久化编码 {@code 1}）。 */
    DISCHARGE(384L, "1", "出院诊断", "", 1L),
/** 入院诊断（IptPatientDiseaseDiagType，持久化编码 {@code 31}）。 */
    ADMISSION(384L, "31", "入院诊断", "", 2L),
/** 并发症诊断（IptPatientDiseaseDiagType，持久化编码 {@code 10}）。 */
    COMPLICATION(384L, "10", "并发症诊断", "", 3L),
/** 术前诊断（IptPatientDiseaseDiagType，持久化编码 {@code 4}）。 */
    PRE_OP(384L, "4", "术前诊断", "", 4L),
/** 院内感染诊断（IptPatientDiseaseDiagType，持久化编码 {@code 11}）。 */
    NOSOCOMIAL(384L, "11", "院内感染诊断", "", 5L),
/** 术后诊断（IptPatientDiseaseDiagType，持久化编码 {@code 5}）。 */
    POST_OP(384L, "5", "术后诊断", "", 6L),
/** 主要诊断（IptPatientDiseaseDiagType，持久化编码 {@code 12}）。 */
    PRIMARY(384L, "12", "主要诊断", "", 7L),
/** 尸检诊断（IptPatientDiseaseDiagType，持久化编码 {@code 6}）。 */
    AUTOPSY(384L, "6", "尸检诊断", "", 8L),
/** 次要诊断（IptPatientDiseaseDiagType，持久化编码 {@code 13}）。 */
    SECONDARY(384L, "13", "次要诊断", "", 9L),
/** 放射诊断（IptPatientDiseaseDiagType，持久化编码 {@code 7}）。 */
    RADIOLOGY(384L, "7", "放射诊断", "", 10L),
    /** 中医出院诊断病名。 */
    TCM_DISCHARGE_NAME(384L, "14", "中医出院诊断病名", "", 11L),
/** 超声诊断（IptPatientDiseaseDiagType，持久化编码 {@code 8}）。 */
    ULTRASOUND(384L, "8", "超声诊断", "", 12L),
/** 中医出院主病（IptPatientDiseaseDiagType，持久化编码 {@code 141}）。 */
    TCM_DISCHARGE_MAIN_DISEASE(384L, "141", "中医出院主病", "", 13L),
/** 病理诊断（IptPatientDiseaseDiagType，持久化编码 {@code 9}）。 */
    PATHOLOGY(384L, "9", "病理诊断", "", 14L),
/** 中医出院主证（IptPatientDiseaseDiagType，持久化编码 {@code 142}）。 */
    TCM_DISCHARGE_MAIN_PATTERN(384L, "142", "中医出院主证", "", 15L),
    /** 损伤、中毒的外部原因。 */
    EXTERNAL_CAUSE(384L, "98", "损伤、中毒的外部原因", "", 16L),
/** 门诊诊断（IptPatientDiseaseDiagType，持久化编码 {@code 2}）。 */
    OUTPATIENT(384L, "2", "门诊诊断", "", 17L),
/** 其他（IptPatientDiseaseDiagType，持久化编码 {@code 99}）。 */
    OTHER(384L, "99", "其他", "", 18L),
/** 入院初步诊断（IptPatientDiseaseDiagType，持久化编码 {@code 3}）。 */
    ADMISSION_PRELIMINARY(384L, "3", "入院初步诊断", "", 19L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    IptPatientDiseaseDiagType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
