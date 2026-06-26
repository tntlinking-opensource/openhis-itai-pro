/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.response;

import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestEnumOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-OP-STK-01 皮试表单枚举初始化响应。
 */
@Getter
@Setter
public class LoadOutpatientSkinTestInitResponse {

    private List<OutpatientSkinTestEnumOptionDto> clinicalStatusOptions;
    private List<OutpatientSkinTestEnumOptionDto> verificationStatusOptions;
    private List<OutpatientSkinTestEnumOptionDto> criticalityOptions;
    private List<OutpatientSkinTestEnumOptionDto> severityOptions;

    /** 执行/核对护士下拉（平台护士岗在职人员）。 */
    private List<OptionDto> nurseOptions;
}
