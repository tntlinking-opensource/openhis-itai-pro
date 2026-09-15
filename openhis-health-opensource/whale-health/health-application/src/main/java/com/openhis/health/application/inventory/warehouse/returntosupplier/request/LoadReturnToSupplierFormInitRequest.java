/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 药库退供应商页主数据初始化请求（IF-WHS-RT-01）。
 *
 * <p>请求体可空；机构上下文由服务端会话解析。</p>
 */
@Getter
@Setter
@Schema(description = "退供应商页主数据初始化 IF-WHS-RT-01")
public class LoadReturnToSupplierFormInitRequest {
}
