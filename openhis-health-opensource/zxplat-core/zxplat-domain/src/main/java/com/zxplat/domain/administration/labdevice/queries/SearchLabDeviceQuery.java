/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 检验设备关键字检索入参。
 */
@Getter
@Setter
public class SearchLabDeviceQuery {

    /** 关键字。 */
    private String keyword;

    /** 发布状态过滤；默认仅 ACTIVE。 */
    private String statusEnum;

    /** 设备分类过滤。 */
    private String categoryCode;

    /** 返回条数上限。 */
    private Integer limit;
}
