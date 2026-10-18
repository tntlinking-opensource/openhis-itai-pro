/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.nursestation;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.setting.domain.SettingDefinitionEnum;
import com.zxplat.framework.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取「耗材是否自动扣库」全局开关（{@link SettingDefinitionEnum#DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH}）。
 */
@Component
@RequiredArgsConstructor
public class DeviceAutoInventoryDeductSettingResolver {

    /** 全局设置管理器：读取耗材自动扣库开关。 */
    private final SettingManager settingManager;

    /**
     * 当前租户/会话上下文下是否启用耗材自动扣库。
     *
     * @return {@code true} 表示值为 {@link GeneralSharedConstants#SETTING_SWITCH_ON}
     */
    public boolean isEnabled() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH.getName());
        if (value == null) {
            return false;
        }
        return GeneralSharedConstants.SETTING_SWITCH_ON.equals(value.trim());
    }
}
