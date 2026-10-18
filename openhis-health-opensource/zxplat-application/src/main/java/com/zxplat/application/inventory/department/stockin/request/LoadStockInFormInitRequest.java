/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室入库页初始化请求（IF-DEPT-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室入库页初始化请求 IF-DEPT-IN-01")
public class LoadStockInFormInitRequest {
}
