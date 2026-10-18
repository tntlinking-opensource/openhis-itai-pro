/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.shared;

import com.zxplat.framework.core.application.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 公费领域常量与校验辅助。
 */
public final class CampusGfConstants {

    public static final String CHARGE_MODE_SELF_PAY_GF = "SELF_PAY_GF";
    public static final String CHARGE_MODE_AFTER_YB_GF = "AFTER_YB_GF";

    public static final String GF_SWITCH_KEY_CALC_MODE = "CALC_MODE";

    public static final String REQUEST_STATUS_PENDING = "PENDING";
    public static final String REQUEST_STATUS_APPROVED = "APPROVED";
    public static final String REQUEST_STATUS_REJECTED = "REJECTED";

    private static final Set<String> CHARGE_MODES = Set.of(
        CHARGE_MODE_SELF_PAY_GF,
        CHARGE_MODE_AFTER_YB_GF
    );

    private CampusGfConstants() {
    }

    public static boolean isValidChargeMode(String chargeModeEnum) {
        return chargeModeEnum != null && CHARGE_MODES.contains(chargeModeEnum);
    }

    public static void validateChargeMode(String chargeModeEnum) {
        if (!isValidChargeMode(chargeModeEnum)) {
            throw new BusinessException("GF_CHARGE_MODE_INVALID", "收费模式无效，仅支持 SELF_PAY_GF / AFTER_YB_GF");
        }
    }

    public static void validateRatio(BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("GF_RATIO_OUT_OF_RANGE", "比例须在 0～1 之间");
        }
    }

    public static String chargeModeLabel(String chargeModeEnum) {
        if (CHARGE_MODE_SELF_PAY_GF.equals(chargeModeEnum)) {
            return "自费公费";
        }
        if (CHARGE_MODE_AFTER_YB_GF.equals(chargeModeEnum)) {
            return "医保后公费";
        }
        return chargeModeEnum;
    }
}
