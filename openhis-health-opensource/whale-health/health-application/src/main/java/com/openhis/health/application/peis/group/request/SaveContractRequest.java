/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 保存团检合同请求（GRP-02）。
 */
@Getter
@Setter
public class SaveContractRequest {

    /** 合同主键（空则新建）。 */
    private Long contractId;

    /** 单位主键。 */
    private Long companyId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 合同名称（可选）。 */
    private String contractName;

    /** 有效期起。 */
    private LocalDate validFrom;

    /** 有效期止。 */
    private LocalDate validTo;

    /** 体检中心机构主键（可选）。 */
    private Long orgId;
}
