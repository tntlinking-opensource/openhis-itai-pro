/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 院内审方结果。
 */
@Data
@Accessors(chain = true)
public class ElepReviewResult {

    /** 是否成功。 */
    private boolean success;

    /** 提示。 */
    private String message;

    /** 电子处方主键。 */
    private Long prescriptionId;

    /** 审方药师医保码。 */
    private String pharCode;

    /** 审方药师姓名。 */
    private String pharName;
}
