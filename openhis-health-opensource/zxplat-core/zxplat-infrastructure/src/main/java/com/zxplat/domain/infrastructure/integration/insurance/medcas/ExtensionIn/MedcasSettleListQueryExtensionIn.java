/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 【4103】结算清单信息查询入参。
 *
 * <p>平台 input.data：{@code psn_no} + {@code setl_id}（结算 ID，非 setl_list_id）。</p>
 */
@Getter
@Setter
public class MedcasSettleListQueryExtensionIn {

    /** 费用性质合同业务号。 */
    private String contractBusNo;

    /** 人员编号 psn_no。 */
    private String psnNo;

    /** 结算 ID setl_id。 */
    private String setlId;

    /** 操作人主键。 */
    private Long operatorId;
}
