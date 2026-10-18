/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院自动滚费（AS-IP-NUR-013）跨层共享常量。
 */
public interface InpatientAutoRollConstants {

    /**
     * 床位滚费长期医嘱默认日频次（与 {@link MedicationCommonConstants#HERBAL_RATE_CODE} 对齐，BR-IP-ARG-019）。
     */
    String AUTO_ROLL_LONG_TERM_DAILY_RATE_CODE = MedicationCommonConstants.HERBAL_RATE_CODE;
}
