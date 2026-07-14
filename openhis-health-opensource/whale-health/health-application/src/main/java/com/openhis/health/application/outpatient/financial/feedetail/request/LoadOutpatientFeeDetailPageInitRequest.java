/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊费用明细页初始化请求（IF-OP-FDT-01）；空体占位。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细页初始化请求")
public class LoadOutpatientFeeDetailPageInitRequest {
}
