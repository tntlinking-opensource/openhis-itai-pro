/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.publicexpenseprofile.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 公费资格校验结果（ADR D9）。
 */
@Getter
@RequiredArgsConstructor
public class EligibilityResult {

    private final boolean passed;
    private final String reasonCode;
    private final String reasonMessage;
    private final String gfStatusEnum;
    private final String cardNo;
    private final String medicalCode;
    private final String campusCode;

    public static EligibilityResult pass(
        String gfStatusEnum,
        String cardNo,
        String medicalCode,
        String campusCode) {
        return new EligibilityResult(true, null, null, gfStatusEnum, cardNo, medicalCode, campusCode);
    }

    public static EligibilityResult fail(
        String reasonCode,
        String reasonMessage,
        String gfStatusEnum,
        String cardNo,
        String medicalCode,
        String campusCode) {
        return new EligibilityResult(false, reasonCode, reasonMessage, gfStatusEnum, cardNo, medicalCode, campusCode);
    }
}
