/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院滚费护理级别读模型（AS-IP-NUR-013 BR-IP-ARG-009；DM-WKF-004 §6.2.25）。
 *
 * <p>表达 billDate 当日最后一条有效校对护理级别医嘱；定价解析由应用层经 ActivityDefinition 完成。</p>
 */
@Getter
@Setter
public class InpatientAutoRollNursingLevelQueryModel {

    /** 源护理级别医嘱主键。 */
    private Long serviceRequestId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 医嘱数量（通常 1）。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 开立人主键。 */
    private Long requesterId;

    /** 护士校对时刻（billDate 口径下取最大值）。 */
    private LocalDateTime checkTime;

    /** 医嘱状态持久化码（VERIFIED / STOPPED）。 */
    private String statusEnum;
}
