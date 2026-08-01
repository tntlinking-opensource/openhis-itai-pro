/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取「住院长嘱是否可拆」全局开关（{@link SettingDefinitionEnum#INPATIENT_STANDING_ORDER_SPLITTABLE}）。
 *
 * <p>种子为 {@code provider_name=G}、默认 {@code 1}（可拆）。值为 {@code 0} 时，护士站待执行列表
 * （IF-IP-EXE-01）对长期药品按包装单位切盒展槽；写执行/取消等其它链路不受本开关影响。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientStandingOrderSplittableSettingResolver {

    /** 全局设置管理器：按会话租户解析生效值（U/R/T/G 优先级由 SettingManager 收口）。 */
    private final SettingManager settingManager;

    /**
     * 当前上下文下住院长嘱是否按可拆口径展开待执行时点。
     *
     * @return {@code true} 表示值为 {@link GeneralSharedConstants#WHALE_SETTING_SWITCH_ON} 或无配置行（与种子默认可拆一致）；
     *         {@code false} 表示不可拆，须走包装批次展槽
     */
    public boolean isSplittable() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.INPATIENT_STANDING_ORDER_SPLITTABLE.getName());
        if (value == null) {
            // 无配置行：与 V10 种子默认可拆一致
            return true;
        }
        return GeneralSharedConstants.WHALE_SETTING_SWITCH_ON.equals(value.trim());
    }
}
