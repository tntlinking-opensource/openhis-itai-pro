/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

/* (C)2026 */
package com.openhis.health.domain.share.enums;

/**
 * 患者疾病诊断类型（住院患者疾病诊断类型代码口径）。
 *
 * <p>与术语编码体系名 {@code ipt_dise_type_code} 及库内种子 {@code ter_code_system.id = 384}
 * 下概念 {@code code}/{@code display} 对齐，供就诊诊断快照行将持久化码解析为展示文案。</p>
 *
 */
public enum IptPatientDiseaseDiagType implements EnumInterface {

    /** 出院诊断。 */
    DISCHARGE(384L, "1", "出院诊断", "", 1L),
    /** 入院诊断。 */
    ADMISSION(384L, "31", "入院诊断", "", 2L),
    /** 并发症诊断。 */
    COMPLICATION(384L, "10", "并发症诊断", "", 3L),
    /** 术前诊断。 */
    PRE_OP(384L, "4", "术前诊断", "", 4L),
    /** 院内感染诊断。 */
    NOSOCOMIAL(384L, "11", "院内感染诊断", "", 5L),
    /** 术后诊断。 */
    POST_OP(384L, "5", "术后诊断", "", 6L),
    /** 主要诊断。 */
    PRIMARY(384L, "12", "主要诊断", "", 7L),
    /** 尸检诊断。 */
    AUTOPSY(384L, "6", "尸检诊断", "", 8L),
    /** 次要诊断。 */
    SECONDARY(384L, "13", "次要诊断", "", 9L),
    /** 放射诊断。 */
    RADIOLOGY(384L, "7", "放射诊断", "", 10L),
    /** 中医出院诊断病名。 */
    TCM_DISCHARGE_NAME(384L, "14", "中医出院诊断病名", "", 11L),
    /** 超声诊断。 */
    ULTRASOUND(384L, "8", "超声诊断", "", 12L),
    /** 中医出院主病。 */
    TCM_DISCHARGE_MAIN_DISEASE(384L, "141", "中医出院主病", "", 13L),
    /** 病理诊断。 */
    PATHOLOGY(384L, "9", "病理诊断", "", 14L),
    /** 中医出院主证。 */
    TCM_DISCHARGE_MAIN_PATTERN(384L, "142", "中医出院主证", "", 15L),
    /** 损伤、中毒的外部原因。 */
    EXTERNAL_CAUSE(384L, "98", "损伤、中毒的外部原因", "", 16L),
    /** 门诊诊断。 */
    OUTPATIENT(384L, "2", "门诊诊断", "", 17L),
    /** 其他。 */
    OTHER(384L, "99", "其他", "", 18L),
    /** 入院初步诊断。 */
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
