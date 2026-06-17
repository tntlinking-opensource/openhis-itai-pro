/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.validator;

import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 读取系统设置「门诊处方金额上限（元）」，供门诊签发分方装箱使用。
 */
@Component
@RequiredArgsConstructor
public class OutpatientPrescriptionAmountLimitSettingSupport {

    private final SettingManager settingManager;

    /**
     * 当前租户/会话上下文下的门诊处方金额上限（元）。
     *
     * @return 配置值；未配置或非法时回落 {@link PrescriptionSplitConstants#DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN}
     */
    public BigDecimal resolveAmountLimitYuan() {
        String raw = settingManager.getEffectiveValue(
            SettingDefinitionEnum.OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN.getName());
        // 未配置时回落默认 500 元
        if (StringUtils.isBlank(raw)) {
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
