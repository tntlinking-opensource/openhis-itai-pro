package com.openhis.whale.mrms.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 病案首页诊断行类型，持久化 {@link #code}。
 */
@Getter
@RequiredArgsConstructor
public enum HomepageDiagType {
    ADMISSION_MAIN("admission_main", "入院主诊断"),
    ADMISSION_OTHER("admission_other", "入院其他诊断"),
    DISCHARGE_MAIN("discharge_main", "出院主诊断"),
    DISCHARGE_OTHER("discharge_other", "出院其他诊断"),
    PATHOLOGY("pathology", "病理诊断"),
    INJURY_POISON("injury_poison", "损伤中毒外部原因");

    private final String code;
    private final String display;
}
