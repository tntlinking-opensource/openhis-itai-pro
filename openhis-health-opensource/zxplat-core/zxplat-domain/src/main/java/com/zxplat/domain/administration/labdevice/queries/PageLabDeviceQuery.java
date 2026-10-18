/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 检验设备分页查询入参。
 */
@Getter
@Setter
public class PageLabDeviceQuery {

    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;

    /** 发布状态过滤。 */
    private String statusEnum;

    /** 设备分类过滤。 */
    private String categoryCode;

    /** 归属科室过滤。 */
    private Long orgId;
}
