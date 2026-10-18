/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 参保地 vs 就医地对比提示。 */
@Data
@Accessors(chain = true)
public class YbRegisterBenefitAreaHintDto {

    /** LOCAL / IN_PROVINCE / CROSS_PROVINCE / UNKNOWN */
    private String areaKind;
    private String insuplcAdmdvs;
    private String mdtrtareaAdmdvs;
    private String hintMessage;
}
