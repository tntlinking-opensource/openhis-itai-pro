/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.enums;

/**
 * 首页诊断行类型（院内 diag_type_code）。
 */
public enum HomepageDiagType {

    /** 出院主诊断。 */
    DISCHARGE_MAIN("discharge_main", "出院主诊断"),
    /** 出院其他诊断。 */
    DISCHARGE_OTHER("discharge_other", "出院其他诊断"),
    /** 入院主诊断。 */
    ADMISSION_MAIN("admission_main", "入院主诊断"),
    /** 入院其他诊断。 */
    ADMISSION_OTHER("admission_other", "入院其他诊断"),
    /** 病理诊断。 */
    PATHOLOGY("pathology", "病理诊断"),
    /** 损伤中毒外部原因。 */
    INJURY_POISON("injury_poison", "损伤中毒");

    private final String code;
    private final String label;

    HomepageDiagType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 持久化类型码。
     *
     * @return 类型 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 中文展示名。
     *
     * @return 展示标签
     */
    public String getLabel() {
        return label;
    }
}
