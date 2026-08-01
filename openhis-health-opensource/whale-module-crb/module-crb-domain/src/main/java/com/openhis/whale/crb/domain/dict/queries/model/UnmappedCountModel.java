/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 三表未对照计数模型（工作台 KPI）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class UnmappedCountModel {

    /** 科室未对照数。 */
    private Integer deptUnmapped;

    /** 检验未对照数。 */
    private Integer labUnmapped;

    /** 药品未对照数。 */
    private Integer drugUnmapped;
}
