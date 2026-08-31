/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.labdevice.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 检验设备检索下拉项投影。
 */
@Getter
@Setter
public class LabDeviceSearchItemQueryModel {

    /** 主键。 */
    private Long id;

    /** 业务编码。 */
    private String busNo;

    /** 设备名称。 */
    private String name;

    /** 发布状态 code。 */
    private String statusEnum;

    /** 设备分类 code。 */
    private String categoryCode;

    /** 拼音码。 */
    private String pyStr;

    /** 五笔码。 */
    private String wbStr;
}
