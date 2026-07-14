/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 退库入库页主数据初始化请求（IF-WHS-RSI-01）。
 *
 * <p>{@code organizationId} 可选；机构上下文通常由服务端会话解析。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库页主数据初始化 IF-WHS-RSI-01")
public class LoadReturnStockInFormInitRequest {

    /** 机构 organizationId。 */
    @Schema(description = "机构 organizationId")
    private Long organizationId;
}
