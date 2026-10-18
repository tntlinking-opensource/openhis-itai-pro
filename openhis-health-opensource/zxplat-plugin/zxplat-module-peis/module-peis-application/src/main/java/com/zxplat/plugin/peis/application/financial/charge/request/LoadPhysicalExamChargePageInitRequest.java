/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体检收费页初始化请求（IF-PE-CHG-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收费页初始化请求")
public class LoadPhysicalExamChargePageInitRequest {
}
