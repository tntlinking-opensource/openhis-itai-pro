/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inventory.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 科室领用审批通过后触发第三方进销存上报的命令载荷。
 *
 * <p>领用为科室药房真实出库（机构总库存减少），对应平台 3502 {@code inv_chg_type=107} 其他出库。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterDeptIssueExtensionIn {

    /** 科室领用单业务号。 */
    private String deptIssueBusNo;

    /** 已审批通过的供应行主键列表。 */
    private List<Long> supplyRequestLineIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;
}
