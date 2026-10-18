/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 导出门诊日结患者分项响应（POI 真 xlsx；对齐门诊费用明细导出响应形态）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "导出门诊日结患者分项响应")
public class ExportOutpatientDailySettlementPatientResponse {

    /** 建议下载文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx MIME 类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** xlsx 二进制内容。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 导出的患者分项行数。 */
    @Schema(description = "导出行数")
    private Integer rowCount;
}
