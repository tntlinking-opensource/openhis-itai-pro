/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.medcas;

import com.openhis.chs.bridge.medcas.invoker.Med4401Invoker;
import com.openhis.health.infrastructure.integration.insurance.medcas.MedcasHomepageUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林医保病案首页上传 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link MedcasHomepageUploadExtension}</p>
 * <p><b>Health 调用方</b>：{@code MrmsCaseRecordUploadAppService}</p>
 * <p><b>Bridge 委托</b>：{@link Med4401Invoker}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(Med4401Invoker.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class MedcasHomepageUploadExtensionImpl implements MedcasHomepageUploadExtension {

    private final Med4401Invoker med4401Invoker;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 【4401】上传住院病案首页信息至医保平台。 */
    @Override
    public MedcasUploadExtensionOut upload(MedcasHomepageUploadExtensionIn command) {
        return med4401Invoker.invoke(command);
    }
}
