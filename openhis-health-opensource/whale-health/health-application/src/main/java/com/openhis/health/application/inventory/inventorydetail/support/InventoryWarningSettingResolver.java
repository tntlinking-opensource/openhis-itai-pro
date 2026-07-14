/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.support;

import com.openhis.health.domain.share.constants.InventoryWarningConstants;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 读取库存效期/低库存预警阈值（系统设置），供库存明细与工作台使用。
 */
@Component
@RequiredArgsConstructor
public class InventoryWarningSettingResolver {

    private final SettingManager settingManager;

    /**
     * 效期预警天数：剩余天数 ≤ 返回值即命中预警。
     *
     * @return 正整数；未配置或非法时回落 {@link InventoryWarningConstants#DEFAULT_EXPIRE_WARNING_DAYS}
     */
    public int resolveExpireWarningDays() {
        String raw = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.INVENTORY_EXPIRE_WARNING_DAYS.getName());
        // 未配置时回落默认 180 天
        if (StringUtils.isEmpty(raw)) {
            return InventoryWarningConstants.DEFAULT_EXPIRE_WARNING_DAYS;
        }
        String trimmed = raw.trim();
        try {
            int parsed = Integer.parseInt(trimmed);
            // 负数非法，回落默认
            if (parsed < 0) {
                return InventoryWarningConstants.DEFAULT_EXPIRE_WARNING_DAYS;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return InventoryWarningConstants.DEFAULT_EXPIRE_WARNING_DAYS;
        }
    }

    /**
     * 库存预警包装单位数量上限（不含）：全院各库房合计包装数 &lt; 返回值即命中预警。
     *
     * @return 正数；未配置或非法时回落 {@link InventoryWarningConstants#DEFAULT_MIN_PACKAGE_QUANTITY_WARNING}
     */
    public BigDecimal resolveMinPackageQuantityWarning() {
        String raw = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.INVENTORY_MIN_PACKAGE_QUANTITY_WARNING.getName());
        // 未配置时回落默认 30 包装
        if (StringUtils.isEmpty(raw)) {
            return InventoryWarningConstants.DEFAULT_MIN_PACKAGE_QUANTITY_WARNING;
        }
        String trimmed = raw.trim();
        try {
            BigDecimal parsed = new BigDecimal(trimmed);
            // ≤0 非法，回落默认
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                return InventoryWarningConstants.DEFAULT_MIN_PACKAGE_QUANTITY_WARNING;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return InventoryWarningConstants.DEFAULT_MIN_PACKAGE_QUANTITY_WARNING;
        }
    }
}
