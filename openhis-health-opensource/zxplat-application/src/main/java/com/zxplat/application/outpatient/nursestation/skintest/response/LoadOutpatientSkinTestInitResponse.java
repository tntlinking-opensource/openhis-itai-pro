/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.response;

import com.zxplat.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestEnumOptionDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-OP-STK-01 皮试表单枚举初始化响应。
 */
@Getter
@Setter
public class LoadOutpatientSkinTestInitResponse {

    /** 临床状态枚举下拉。 */
    private List<OutpatientSkinTestEnumOptionDto> clinicalStatusOptions;

    /** 核实状态枚举下拉。 */
    private List<OutpatientSkinTestEnumOptionDto> verificationStatusOptions;

    /** 危险程度枚举下拉。 */
    private List<OutpatientSkinTestEnumOptionDto> criticalityOptions;

    /** 严重程度枚举下拉。 */
    private List<OutpatientSkinTestEnumOptionDto> severityOptions;

    /** 执行/核对护士下拉（平台护士岗在职人员）。 */
    private List<OptionDto> nurseOptions;
}
