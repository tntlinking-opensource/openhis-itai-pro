/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方上传命令。
 *
 * <p>职责：将 HIS 已提交处方上传至第三方/医保电子处方平台，获取 {@code hi_rxno}。</p>
 */
@Data
@Accessors(chain = true)
public class ElepUploadExtensionIn {

    /** 就诊主键，处方须归属该次接诊。 */
    private Long encounterId;

    /** 医保合同主键。 */
    private Long contractId;

    /** HIS 处方业务号 {@code prescription_no}。 */
    private String prescriptionNo;

    /** 院内处方号（平台侧 hosp_rxno，可与 prescriptionNo 相同或映射）。 */
    private String hospRxno;
}
