/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.response;

import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestEnumOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * IF-IP-STK-01 皮试表单枚举初始化响应。
 */
@Getter
@Setter
public class LoadNurseStationSkinTestInitResponse {

    /** 临床状态选项。 */
    private List<NurseStationSkinTestEnumOptionDto> clinicalStatusOptions;

    /** 验证状态选项。 */
    private List<NurseStationSkinTestEnumOptionDto> verificationStatusOptions;

    /** 危险程度选项。 */
    private List<NurseStationSkinTestEnumOptionDto> criticalityOptions;

    /** 严重程度选项。 */
    private List<NurseStationSkinTestEnumOptionDto> severityOptions;
}
