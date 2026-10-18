/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 点评记录页初始化请求（IF-PRV-01；空体占位）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录页初始化请求")
public class LoadPageInitRequest {
}
