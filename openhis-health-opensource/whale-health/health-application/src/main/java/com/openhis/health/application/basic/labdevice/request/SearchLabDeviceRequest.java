/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.request;

import lombok.Data;

/**
 * 检验设备关键字检索请求。
 */
@Data
public class SearchLabDeviceRequest {

    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;

    /** 发布状态过滤；默认 ACTIVE。 */
    private String statusEnum;

    /** 设备分类过滤。 */
    private String categoryCode;

    /** 返回条数上限。 */
    private Integer limit;
}
