/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院 2301 单批费用上传结果（供进度条：已上传/剩余）。
 */
@Data
@Accessors(chain = true)
public class IpFeeUploadBatchResult {

    /** 本批实际上传行数（出站 feedetail 行数）。 */
    private int batchUploaded;
    /** 本批后仍待上传行数。 */
    private int remainingAfter;
    /** 是否已无待传。 */
    private boolean done;
    /** 平台就诊 ID（后续批次可复用，避免重复 2401）。 */
    private String mdtrtId;
}
