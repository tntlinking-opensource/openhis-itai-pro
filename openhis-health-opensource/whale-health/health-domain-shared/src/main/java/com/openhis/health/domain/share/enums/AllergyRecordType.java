/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 过敏记录来源类型（{@code cln_allergy_intolerance.type_enum}）。
 *
 * <p>皮试行绑申请；主诉与「无已知过敏」按患者长期有效，不绑申请。</p>
 */
@Getter
public enum AllergyRecordType implements EnumInterface {

    /** 皮试登记（须 {@code request_id}）。 */
    SKIN_TEST(0L, "skin_test", "皮试", "皮试执行结果沉淀，阳性计入患者过敏史", 1L),
    /** 医生或住院护士主诉登记。 */
    DECLARED(0L, "declared", "主诉登记", "医生或住院护士采集的过敏原事实", 2L),
    /** 已确认无已知过敏（与有效过敏原互斥）。 */
    NKDA(0L, "nkda", "无已知过敏", "已确认当前无已知过敏原", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergyRecordType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
