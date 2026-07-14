/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新耗材目录请求：在创建字段基础上携带网关字符串主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateDeviceCatalogRequest extends CreateDeviceCatalogRequest {
    /** 设备定义主键（网关字符串，解析见 {@link com.openhis.health.domain.share.utils.GatewayIdParseUtil}）。 */
    private String id;
    /** 编辑业务码（由上游业务判定并透传，示例：OK）。 */
    private String editabilityCode;
}
