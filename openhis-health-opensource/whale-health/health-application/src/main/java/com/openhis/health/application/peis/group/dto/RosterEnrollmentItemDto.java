/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量建单/选定条目 DTO。
 */
@Getter
@Setter
public class RosterEnrollmentItemDto {

    /** 名册主键。 */
    private Long rosterId;

    /** 选定套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;
}
