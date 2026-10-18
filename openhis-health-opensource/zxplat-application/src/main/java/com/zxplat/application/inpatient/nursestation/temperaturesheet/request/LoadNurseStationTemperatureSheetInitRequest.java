/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TMP-01：体温单 Tab 页初始化请求（无业务字段）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单页初始化请求")
public class LoadNurseStationTemperatureSheetInitRequest {
}
