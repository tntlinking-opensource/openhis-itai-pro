/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.enums.DispenseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发放列表查询模型。
 */
@Getter
@Setter
public class MedicationDispenseListQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 状态枚举编码。 */
    private DispenseStatus statusEnum;
    /** 药品请求主键。 */
    private Long medReqId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 发放数量。 */
    private BigDecimal quantity;
    /** 单位 code。 */
    private String unitCode;
    /** 位置主键。 */
    private Long locationId;
    /** planned发药时间。 */
    private LocalDateTime plannedDispenseTime;
}
