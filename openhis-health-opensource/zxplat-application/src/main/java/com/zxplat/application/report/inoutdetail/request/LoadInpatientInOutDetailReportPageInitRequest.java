/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院患者入出明细报表页初始化请求（IF-RPT-IOE-01；空体占位）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表页初始化请求")
public class LoadInpatientInOutDetailReportPageInitRequest {
}
