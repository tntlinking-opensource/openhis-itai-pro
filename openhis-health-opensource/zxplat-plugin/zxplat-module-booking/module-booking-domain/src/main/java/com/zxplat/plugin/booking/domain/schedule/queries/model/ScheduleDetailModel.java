/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 日程详情读模型。
 */
@Getter
@Setter
public class ScheduleDetailModel {

    /** 主键。 */
    private Long id;

    /** 状态枚举编码。 */
    private String statusEnum;

    /** actors。 */
    private List<ScheduleActorModel> actors;
}
