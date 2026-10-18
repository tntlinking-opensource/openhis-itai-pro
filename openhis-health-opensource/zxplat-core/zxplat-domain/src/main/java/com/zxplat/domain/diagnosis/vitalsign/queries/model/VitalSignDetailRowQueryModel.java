/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生命体征明细读模型（DM-DIA-002 v2.1 §4.6.3）。
 */
@Getter
@Setter
public class VitalSignDetailRowQueryModel {

    /** 明细 id。 */
    private Long id;

    /** 主表 id。 */
    private Long vitalSignId;

    /** 记录时点（join 主表）。 */
    private LocalDateTime recordedAt;

    /** 单据类型（join 主表）。 */
    private String typeEnum;

    /** 术语体系 code。 */
    private String termCode;

    /** 字典项 code。 */
    private String itemCode;

    /** 字典项值。 */
    private String itemValue;

    /** 单位。 */
    private String units;

    /** 明细备注。 */
    private String remark;
}
