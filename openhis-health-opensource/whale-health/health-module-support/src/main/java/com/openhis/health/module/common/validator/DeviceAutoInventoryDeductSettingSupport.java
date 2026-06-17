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

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 读取「耗材是否自动扣库」全局开关（{@link SettingDefinitionEnum#DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH}）。
 */
@Component
@RequiredArgsConstructor
public class DeviceAutoInventoryDeductSettingSupport {

    private final SettingManager settingManager;

    /**
     * 当前租户/会话上下文下是否启用耗材自动扣库。
     *
     * @return {@code true} 表示值为 {@link GeneralSharedConstants#WHALE_SETTING_SWITCH_ON}
     */
    public boolean isEnabled() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH.getName());
        if (value == null) {
            return false;
        }
        return GeneralSharedConstants.WHALE_SETTING_SWITCH_ON.equals(value.trim());
    }
}
