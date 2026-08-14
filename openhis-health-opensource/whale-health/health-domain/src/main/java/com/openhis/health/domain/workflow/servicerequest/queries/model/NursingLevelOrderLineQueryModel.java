/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 护理级别医嘱行（病案首页护理天数汇总用）。
 *
 * <p>含校对时间与停嘱时间，便于按级别累计日历天。</p>
 */
@Getter
@Setter
public class NursingLevelOrderLineQueryModel {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 诊疗活动定义主键（解析级别名称）。 */
    private Long activityId;

    /** 状态枚举 code（VERIFIED/STOPPED 等）。 */
    private String statusEnum;

    /** 校对时间（护理级别生效日起点，记进）。 */
    private LocalDateTime checkTime;

    /** 停嘱时间（级别结束日，记进不计出口径下作止界）。 */
    private LocalDateTime stopTime;
}
