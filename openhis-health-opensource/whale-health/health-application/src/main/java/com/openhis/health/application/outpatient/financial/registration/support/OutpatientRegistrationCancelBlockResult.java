/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退号临床/收费门禁判定结果（IF-REG-09 · BR-REG-015）。
 */
@Getter
@Setter
public class OutpatientRegistrationCancelBlockResult {

    /** 是否被临床或收费事实阻断。 */
    private boolean blocked;

    /** 阻断原因码；未阻断时为 {@code null}。 */
    private String reasonCode;

    /** 阻断原因说明；未阻断时为 {@code null}。 */
    private String reasonText;

    /**
     * 构造未阻断结果。
     *
     * @return 可继续后续就诊态门禁判定的结果
     */
    public static OutpatientRegistrationCancelBlockResult notBlocked() {
        OutpatientRegistrationCancelBlockResult result = new OutpatientRegistrationCancelBlockResult();
        result.setBlocked(false);
        return result;
    }

    /**
     * 构造阻断结果。
     *
     * @param reasonCode 原因码
     * @param reasonText 用户提示
     * @return 阻断结果
     */
    public static OutpatientRegistrationCancelBlockResult blocked(String reasonCode, String reasonText) {
        OutpatientRegistrationCancelBlockResult result = new OutpatientRegistrationCancelBlockResult();
        result.setBlocked(true);
        result.setReasonCode(reasonCode);
        result.setReasonText(reasonText);
        return result;
    }
}
