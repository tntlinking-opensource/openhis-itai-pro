/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import com.zxplat.domain.share.constants.PrescriptionSplitConstants;
import com.zxplat.framework.setting.domain.SettingDefinitionEnum;
import com.zxplat.framework.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 读取系统设置「门诊处方金额上限（元）」，供门诊签发分方装箱使用。
 */
@Component
@RequiredArgsConstructor
public class OutpatientPrescriptionAmountLimitSettingResolver {

    private final SettingManager settingManager;

    /**
     * 当前租户/会话上下文下的门诊处方金额上限（元）。
     *
     * @return 配置值；未配置或非法时回落 {@link PrescriptionSplitConstants#DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN}
     */
    public BigDecimal resolveAmountLimitYuan() {
        String raw = settingManager.getEffectiveValue(
            SettingDefinitionEnum.OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN.getName());
        // 未配置时回落默认 1000 元
        if (StringUtils.isEmpty(raw)) {
            return PrescriptionSplitConstants.DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN;
        }
        String trimmed = raw.trim();
        try {
            BigDecimal parsed = new BigDecimal(trimmed);
            // 非法或 ≤0 时回落默认；99999 等极大值由产品配置表示「不按金额分方」
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                return PrescriptionSplitConstants.DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return PrescriptionSplitConstants.DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN;
        }
    }
}
