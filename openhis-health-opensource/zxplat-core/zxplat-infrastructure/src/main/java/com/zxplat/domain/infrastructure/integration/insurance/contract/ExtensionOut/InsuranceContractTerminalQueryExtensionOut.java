/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.contract.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医保契约终端参数（4401/4101A 组装用定点机构与区划）。
 *
 * <p>字段对齐 {@code chs-contract.properties}：{@code fixmedins_code}、{@code fixmedins_name}、
 * {@code adm_vs}；不暴露 CHS 内部 {@code ContractConfig}。</p>
 */
@Data
@Accessors(chain = true)
public class InsuranceContractTerminalQueryExtensionOut {

    /** 契约业务号（回显，便于排障）。 */
    private String contractBusNo;

    /** 定点医药机构编号（4401 {@code fixmedins_code}）。 */
    private String fixmedinsCode;

    /** 定点医药机构名称。 */
    private String fixmedinsName;

    /** 就医地/统筹医保区划（{@code adm_vs}）。 */
    private String admVs;
}
