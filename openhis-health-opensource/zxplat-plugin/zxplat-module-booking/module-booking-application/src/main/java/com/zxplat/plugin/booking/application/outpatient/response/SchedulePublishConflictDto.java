/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 日程发布冲突明细（BR-EXT-005/006）。
 */
@Getter
@Setter
public class SchedulePublishConflictDto {

    /** 冲突类型（如 PRACTITIONER_OVERLAP / LOCATION_OVERLAP）。 */
    private String conflictType;

    /** 冲突资源主键（医师/诊室等）。 */
    private Long resourceId;

    /** 已占用冲突的日程主键。 */
    private Long conflictScheduleId;

    /** 冲突日程显示名称。 */
    private String conflictScheduleName;

    /** 面向用户的冲突说明。 */
    private String message;
}
