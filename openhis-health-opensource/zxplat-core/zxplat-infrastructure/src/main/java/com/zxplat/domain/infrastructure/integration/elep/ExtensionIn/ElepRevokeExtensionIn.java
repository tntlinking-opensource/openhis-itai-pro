/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方撤销命令。
 *
 * <p>职责：向第三方平台申请撤销已上传处方，撤销后不可再用于医保结算取药。</p>
 */
@Data
@Accessors(chain = true)
public class ElepRevokeExtensionIn {

    /** HIS 处方主键。 */
    private Long prescriptionId;

    /** 医保合同主键。 */
    private Long contractId;

    /** 医保电子处方号。 */
    private String hiRxno;

    /** 撤销原因（平台审计/展示）。 */
    private String revokeReason;
}
