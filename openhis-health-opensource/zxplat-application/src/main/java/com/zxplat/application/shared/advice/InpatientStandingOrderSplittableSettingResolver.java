/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.setting.domain.SettingDefinitionEnum;
import com.zxplat.framework.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取「住院长嘱是否可拆」全局开关（{@link SettingDefinitionEnum#INPATIENT_STANDING_ORDER_SPLITTABLE}）。
 *
 * <p>种子为 {@code provider_name=G}、默认 {@code 1}（可拆）。值为 {@code 0} 时：</p>
 * <ul>
 *   <li>护士站待执行列表（IF-IP-EXE-01）、患者队列待执行计数（BR-IP-NUR-005）、出院门禁待执行槽位判定
 *       （BR-IP-ATD-011①′ / ATD-03/05）：长期 + 包装单位≠拆零 + 口服类用法
 *       → 按包装切盒后<strong>一盒一个时点</strong>（**BR-IP-EXE-035**）；执行写路径落<strong>一整包装</strong>账单/发放（临嘱同构）</li>
 *   <li>汇总领药（IF-IP-SUM-01）：执行已按整包装落一行，列表仅按截止过滤，不再做「整盒展多点」补齐</li>
 * </ul>
 * <p>值为 {@code 1}/缺省：按频次一天（槽）一天执行，与产品可拆设计一致（仍可命中拆零下限 BR-IP-EXE-036）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientStandingOrderSplittableSettingResolver {

    /** 全局设置管理器：按会话租户解析生效值（U/R/T/G 优先级由 SettingManager 收口）。 */
    private final SettingManager settingManager;

    /**
     * 当前上下文下住院长嘱是否按可拆口径展开待执行时点。
     *
     * @return {@code true} 表示值为 {@link GeneralSharedConstants#SETTING_SWITCH_ON} 或无配置行（与种子默认可拆一致）；
     *         {@code false} 表示不可拆，须走包装批次展槽
     */
    public boolean isSplittable() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.INPATIENT_STANDING_ORDER_SPLITTABLE.getName());
        if (value == null) {
            // 无配置行：与 V10 种子默认可拆一致
            return true;
        }
        return GeneralSharedConstants.SETTING_SWITCH_ON.equals(value.trim());
    }
}
