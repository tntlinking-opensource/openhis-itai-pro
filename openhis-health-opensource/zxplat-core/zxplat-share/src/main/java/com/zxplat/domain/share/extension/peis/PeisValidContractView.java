/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.peis;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者有效团检合同视图（miniapp / 窗口进池手选）。
 */
@Getter
@Setter
public class PeisValidContractView {

    /** 合同主键。 */
    private Long contractId;

    /** 合同名称。 */
    private String contractName;

    /** 合同类型编码。 */
    private String contractTypeCode;

    /** 合同类型名称。 */
    private String contractTypeName;

    /** 有效起。 */
    private LocalDate validFrom;

    /** 有效止。 */
    private LocalDate validTo;
}
