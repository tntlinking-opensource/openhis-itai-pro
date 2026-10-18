/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** 第三方待遇享受检查命令（平台 2001 语义的中性封装）。 */
@Data
@Accessors(chain = true)
public class InsuranceTreatmentCheckExtensionIn {

    /** 费用性质 {@code fin_contract.id}。 */
    private Long contractId;
    /** 人员编号。 */
    private String psnNo;
    /** 险种类型。 */
    private String insutype;
    /** 参保地医保区划。 */
    @JsonProperty("insuplcAdmdvs")
    private String insurancePoolAreaCode;
    /** 医疗类别；缺省时 adapter 使用 11（普通门诊）。 */
    private String medType;
}
