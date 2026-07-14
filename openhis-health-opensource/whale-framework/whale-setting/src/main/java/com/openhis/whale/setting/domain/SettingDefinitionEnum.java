/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.domain;

import java.util.Arrays;
import java.util.List;

import com.openhis.whale.core.application.exception.ValidationException;

/**
 * 配置项定义（配置源）
 *
 * <p>
 * 在此枚举中维护可用的配置项（标题/名称）。前端维护配置时以此为准。
 * </p>
 */
public enum SettingDefinitionEnum {

    /** 控制患者档案是否开放学籍维护入口；值约定：{@code 1} 开启，{@code 0} 关闭。 */
    STUDENT_SWITCH("studentSwich", "学生开关"),

    /** 控制医保相关能力是否启用；值约定：{@code 1} 开启，{@code 0} 关闭。 */
    YB_SWITCH("ybSwich", "医保开关"),

    /** 控制耗材业务是否自动扣库；值约定：{@code 1} 自动扣库，{@code 0} 不自动扣库。 */
    DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH("deviceAutoInventoryDeductSwich", "耗材是否自动扣库"),

    /** 门诊处方单张金额上限（元）；不按金额分时配置为 {@code 99999} 等大值即可。 */
    OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN("outpatientPrescriptionAmountLimitYuan", "门诊处方金额上限（元）"),

    /** 库存明细效期预警：剩余效期天数 ≤ 配置值（默认 180）。 */
    INVENTORY_EXPIRE_WARNING_DAYS("inventoryExpireWarningDays", "库存效期预警天数"),

    /** 库存明细库存预警：全院品种各库房合计包装数 &lt; 配置值（默认 30）。 */
    INVENTORY_MIN_PACKAGE_QUANTITY_WARNING("inventoryMinPackageQuantityWarning", "库存预警包装单位数量");

    private final String name;
    private final String title;

    SettingDefinitionEnum(String name, String title) {
        this.name = name;
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public static List<SettingDefinitionEnum> all() {
        return List.of(values());
    }

    public static SettingDefinitionEnum ofName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("设置名称不能为空");
        }
        String normalized = name.trim();
        return Arrays.stream(values()).filter(x -> x.name.equals(normalized)).findFirst()
            .orElseThrow(() -> new ValidationException("不支持的设置名称: " + name));
    }
}
