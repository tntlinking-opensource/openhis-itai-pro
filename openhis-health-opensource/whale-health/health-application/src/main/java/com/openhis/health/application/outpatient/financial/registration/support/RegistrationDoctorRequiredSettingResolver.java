/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取「挂号时医生是否必选」全局开关（{@link SettingDefinitionEnum#REGISTRATION_DOCTOR_REQUIRED}）。
 *
 * <p>种子为 {@code provider_name=G}、默认 {@code 1}（必选）；开启后 IF-REG-06 须传
 * {@code practitionerId}；非必选且未指定则挂到科室并由医生站 BR-DOR-009 按科室共享可见。</p>
 */
@Component
@RequiredArgsConstructor
public class RegistrationDoctorRequiredSettingResolver {

    /** 全局设置管理器：按会话租户解析生效值（U/R/T/G 优先级由 SettingManager 收口）。 */
    private final SettingManager settingManager;

    /**
     * 当前上下文下挂号是否必须选择接诊医生。
     *
     * @return {@code true} 表示值为 {@link GeneralSharedConstants#WHALE_SETTING_SWITCH_ON}；
     *         无配置行时与种子默认「必选」一致
     */
    public boolean isRequired() {
        String value = this.settingManager.getEffectiveValue(
            SettingDefinitionEnum.REGISTRATION_DOCTOR_REQUIRED.getName());
        if (value == null) {
            return true;
        }
        return GeneralSharedConstants.WHALE_SETTING_SWITCH_ON.equals(value.trim());
    }
}
