/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.calendarexception.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 从模板按日生成时的 T3 解析结果。
 */
@Getter
@Setter
public class DayGeneratePlanModel {

    /** 跳过常规模板生成（CLOSED）。 */
    private boolean skipNormal;

    /** 替换为指定 T2（OVERRIDE/REDUCED）。 */
    private Long overrideTemplateId;

    /** REDUCED 时放号容量减半。 */
    private boolean reducedCapacity;

    /** 额外生成一条 Schedule（EXTRA）。 */
    private boolean generateExtra;

    /** EXTRA 使用的 T2。 */
    private Long extraTemplateId;
}
