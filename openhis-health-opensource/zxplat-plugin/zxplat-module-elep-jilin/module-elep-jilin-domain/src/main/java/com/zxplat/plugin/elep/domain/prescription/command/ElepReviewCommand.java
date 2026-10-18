/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 院内医保药师审方（3-03 上传门禁；不写 HIS 发药审方）。
 */
@Data
@Accessors(chain = true)
public class ElepReviewCommand {

    /** 医保契约 bus_no。 */
    private String contractBusNo;

    /** 电子处方主键。 */
    private Long prescriptionId;
}
