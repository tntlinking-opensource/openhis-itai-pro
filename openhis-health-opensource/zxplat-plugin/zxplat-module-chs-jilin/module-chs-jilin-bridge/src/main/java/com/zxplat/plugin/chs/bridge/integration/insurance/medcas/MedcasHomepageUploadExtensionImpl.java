/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.medcas;

import com.zxplat.plugin.chs.bridge.medcas.invoker.Med4101AInvoker;
import com.zxplat.plugin.chs.bridge.medcas.invoker.Med4102Invoker;
import com.zxplat.plugin.chs.bridge.medcas.invoker.Med4103Invoker;
import com.zxplat.plugin.chs.bridge.medcas.invoker.Med4401Invoker;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.MedcasHomepageUploadExtension;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListStatusExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListUploadExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林医保病案上报 SPI 实现（4401 / 4101A / 4102 / 4103）。
 *
 * <p><b>SPI 接口</b>：{@link MedcasHomepageUploadExtension}</p>
 * <p><b>Health 调用方</b>：{@code MrmsCaseRecordUploadAppService}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(Med4401Invoker.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class MedcasHomepageUploadExtensionImpl implements MedcasHomepageUploadExtension {

    private final Med4401Invoker med4401Invoker;
    private final Med4101AInvoker med4101AInvoker;
    private final Med4102Invoker med4102Invoker;
    private final Med4103Invoker med4103Invoker;

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

    /** 【4101A】上传结算清单。 */
    @Override
    public MedcasUploadExtensionOut uploadSettleList(MedcasSettleListUploadExtensionIn command) {
        return med4101AInvoker.invoke(command);
    }

    /** 【4102】修改结算清单状态。 */
    @Override
    public MedcasUploadExtensionOut modifySettleListStatus(MedcasSettleListStatusExtensionIn command) {
        return med4102Invoker.invoke(command);
    }

    /** 【4103】查询结算清单信息。 */
    @Override
    public MedcasUploadExtensionOut querySettleList(MedcasSettleListQueryExtensionIn command) {
        return med4103Invoker.invoke(command);
    }
}
