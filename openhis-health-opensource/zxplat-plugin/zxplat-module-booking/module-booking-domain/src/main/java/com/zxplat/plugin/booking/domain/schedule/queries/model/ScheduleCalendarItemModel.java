/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日历视图日程读模型。
 */
@Getter
@Setter
public class ScheduleCalendarItemModel {

    /** 日程主键。 */
    private Long id;

    /** 机构主键。 */
    private Long organizationId;

    /** 显示名称。 */
    private String name;

    /** 业务状态编码。 */
    private String statusEnum;

    /** 规划窗口起。 */
    private LocalDateTime planningHorizonStart;

    /** 规划窗口止。 */
    private LocalDateTime planningHorizonEnd;

    /** 关联医疗服务主键。 */
    private Long healthcareServiceId;

    /** 参与者列表。 */
    private List<ScheduleActorModel> actors;

    /** 替诊医师主键。 */
    private Long substitutePractitionerId;

    /** 替诊原因。 */
    private String substituteReason;

    /** 来源 T2 日排班模板主键。 */
    private Long sourceScheduleTemplateId;

    /** 关联 T1 放号时段模板主键。 */
    private Long slotProvisionTemplateId;
}
