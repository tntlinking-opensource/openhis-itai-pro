/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 【4102】结算清单状态修改入参。
 */
@Getter
@Setter
public class MedcasSettleListStatusExtensionIn {

    /** 费用性质合同业务号。 */
    private String contractBusNo;

    /** 人员编号 psn_no。 */
    private String psnNo;

    /** 结算 ID setl_id。 */
    private String setlId;

    /** 审核状态：0 未提交 / 1 已提交。 */
    private String stasType;

    /** 操作人主键。 */
    private Long operatorId;
}
