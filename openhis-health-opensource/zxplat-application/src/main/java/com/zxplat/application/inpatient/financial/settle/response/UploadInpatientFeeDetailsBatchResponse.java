/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院费用明细单批上传响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细单批上传响应")
public class UploadInpatientFeeDetailsBatchResponse {

    @Schema(description = "本批实际上传行数")
    private int batchUploaded;

    @Schema(description = "本批后仍待上传行数")
    private int remainingAfter;

    @Schema(description = "是否已无待传")
    private boolean done;

    @Schema(description = "平台就诊 ID（后续批次可复用）")
    private String mdtrtId;
}
