/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表页初始化请求（IF-RPT-OCU-01；空壳占位）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表页初始化请求")
public class LoadOutpatientConsumableUsageReportPageInitRequest {
}
