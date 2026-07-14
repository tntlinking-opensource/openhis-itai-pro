/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按就诊查询生命体征条件（主表/明细读侧入参，DM-DIA-002 v2.0 §6.2）。
 */
@Getter
@Setter
public class VitalSignEncounterQuery {

    /** 就诊标识（必填）。 */
    private Long encounterId;

    /** 记录时点起始（可空）。 */
    private LocalDateTime recordedAtFrom;

    /** 记录时点结束（可空）。 */
    private LocalDateTime recordedAtTo;

    /** 单据类型 {@code type_enum}（可空）。 */
    private String typeEnum;

    /** 明细字典项 code 过滤（可空，仅 listDetailsByEncounterId 使用）。 */
    private List<String> itemCodes;

    /** 婴儿序号（可空）。 */
    private Integer babyNo;
}
