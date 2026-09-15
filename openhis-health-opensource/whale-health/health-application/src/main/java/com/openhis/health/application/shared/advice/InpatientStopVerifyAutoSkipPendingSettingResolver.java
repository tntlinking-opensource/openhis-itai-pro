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
 * 读取「住院停嘱校对后是否自动不执行剩余 pending 时点」开关
 * （{@link SettingDefinitionEnum#INPATIENT_STOP_VERIFY_AUTO_SKIP_PENDING}）。
 *
 * <p>种子默认 {@code 0}（关闭）。值为 {@code 1} 时：IF-IP-AUD-02 停嘱校对成功后，
 * 对同批长期已停嘱剩余 pending 槽位调用与 IF-IP-EXE-04 相同的不执行写路径。
 * 出院门禁 {@code BR-IP-ATD-011①′} 不因本开关放宽。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientStopVerifyAutoSkipPendingSettingResolver {

    /** 全局设置管理器：按会话租户解析生效值。 */
    private final SettingManager settingManager;

    /**
     * 当前上下文是否在停嘱校对成功后自动 skip 剩余 pending。
     *
     * @return {@code true} 仅当生效值为 {@link GeneralSharedConstants#WHALE_SETTING_SWITCH_ON}；
     *         无配置行或其它值视为关闭
     */
    public boolean isEnabled() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.INPATIENT_STOP_VERIFY_AUTO_SKIP_PENDING.getName());
        if (value == null) {
            // 无配置行：与种子默认关闭一致，避免未确认院点误跳过待执行点
            return false;
        }
        return GeneralSharedConstants.WHALE_SETTING_SWITCH_ON.equals(value.trim());
    }
}
