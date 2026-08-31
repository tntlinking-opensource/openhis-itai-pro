/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院费用明细单批上传结果（Extension ← 2301）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeUploadFeeBatchExtensionOut {

    /** 本批实际上传行数。 */
    private int batchUploaded;
    /** 本批后仍待上传行数。 */
    private int remainingAfter;
    /** 是否已无待传。 */
    private boolean done;
    /** 平台就诊 ID（后续批次可复用）。 */
    private String mdtrtId;
}
