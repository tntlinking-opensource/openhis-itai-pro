/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 【4101A】结算清单上传入参。
 */
@Getter
@Setter
public class MedcasSettleListUploadExtensionIn {

    /** 费用性质合同业务号。 */
    private String contractBusNo;

    /** 4101A input JSON（含 setlinfo 等节点）。 */
    private String inputJson;

    /** 操作人主键。 */
    private Long operatorId;
}
