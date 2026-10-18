/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询耗材批号维真实进价/售价请求。
 *
 * <p>{@code lotNumber} 可空：空则返回该耗材全部批号价行；有值则仅返回匹配批号。</p>
 */
@Data
@Schema(description = "查询耗材批号进价/售价请求")
public class ListDeviceLotPriceRequest {

    /**
     * 器材定义主键（网关字符串，解析见 {@link com.zxplat.domain.share.utils.GatewayIdParseUtil}）。
     */
    @Schema(description = "耗材定义主键（字符串）", example = "10001")
    private String id;

    /**
     * 批号过滤（可空）。
     *
     * <p>非空时仅返回该批号的进价/售价；空白则返回该耗材全部批号价。</p>
     */
    @Schema(description = "批号过滤（可空）", example = "20250101")
    private String lotNumber;
}
