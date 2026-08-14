/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 参保人信息查询结果（平台 1101 语义的中性封装）。
 */
@Data
@Accessors(chain = true)
public class InsurancePersonQueryExtensionOut {

    /** 人员编号 psn_no。 */
    private String psnNo;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 当前主险种类型 code。 */
    private String insutype;

    /** 个人账户余额汇总。 */
    private BigDecimal balc;

    /** 姓名。 */
    private String psnName;

    /** 证件号码。 */
    private String certno;

    /** 性别 code。 */
    private String gend;

    /** 出生日期。 */
    private String brdy;

    /** 参保险种明细行。 */
    private List<InsuranceCoverageLineSnapshot> coverageLines = new ArrayList<>();

    /** 身份认定明细行。 */
    private List<InsuranceIdentityLineSnapshot> identityLines = new ArrayList<>();

    /**
     * 1101 异地失败切省通道后的实际合同 bus_no；未切换时为 null。
     */
    private String resolvedContractBusNo;

    /**
     * 1101 异地失败切省通道后的实际费用性质 id；未切换时为 null。
     */
    private Long resolvedContractId;
}
