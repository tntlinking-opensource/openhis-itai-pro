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
 * 科室退领审批通过后触发第三方进销存上报的命令载荷。
 *
 * <p>退领为科室药房真实入库（机构总库存增加），对应平台 3502 {@code inv_chg_type=106} 其他入库。</p>
 */
@Data
@Accessors(chain = true)
public class InventoryUploadAfterDeptIssueReturnExtensionIn {

    /** 科室退领单业务号。 */
    private String deptIssueReturnBusNo;

    /** 已审批通过的供应行主键列表。 */
    private List<Long> supplyRequestLineIds = new ArrayList<>();

    /** 医保/第三方合同业务号。 */
    private String contractBusNo;

    /** 合同主键。 */
    private Long contractId;
}
