/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IF-IP-ARG-08：单条滚费执行行上下文（锚定已有长期 SR，走 IF-IP-EXE-02）。
 */
@Getter
@Setter
public class AutoRollLineContext {

    /** 长期诊疗申请主键。 */
    private Long serviceRequestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 个人账户主键。 */
    private Long accountId;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 收费项定价主键。 */
    private Long chargeDefinitionId;

    /** 落账数量。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 诊疗类目 code（配置选品类目 / 护理分支 26）。 */
    private String categoryEnum;

    /** 开立人主键。 */
    private Long requesterId;

    /** 滚费业务日。 */
    private LocalDate billDate;

    /** 费用发生时刻（配置业务时刻或护理日末；与 Procedure.recorded_time 对齐）。 */
    private LocalDateTime occurrenceTime;

    /** 单价（入账权威）。 */
    private BigDecimal unitPrice;

    /** 行总价。 */
    private BigDecimal totalPrice;
}
