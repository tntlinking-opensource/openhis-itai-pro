/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.dict;

import com.zxplat.plugin.chs.domain.share.constants.PlatformDictConstants;
import com.zxplat.plugin.chs.domain.platformdict.service.YbPlatformDictLookupService;
import com.zxplat.domain.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 吉林医保平台字典展示 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link InsurancePlatformDictDisplayExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientRegistrationAppService}</p>
 * <p><b>Bridge 委托</b>：{@link YbPlatformDictLookupService}（1901 字典本地镜像 lookup，无实时网关出站）</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(YbPlatformDictLookupService.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InsurancePlatformDictDisplayExtensionImpl implements InsurancePlatformDictDisplayExtension {

    private final YbPlatformDictLookupService lookupService;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 本地：从 1901 字典镜像批量查询参保地名称，无网关出站。 */
    @Override
    public Map<String, String> lookupInsuplcAdmdvsLabels(Collection<String> admdvsCodes) {
        if (admdvsCodes == null || admdvsCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> values = admdvsCodes.stream()
            .filter(StringUtils::isNotEmpty)
            .map(String::trim)
            .distinct()
            .toList();
        if (values.isEmpty()) {
            return Collections.emptyMap();
        }
        return lookupService.lookupLabels(
            com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils.getCurrentTenantId(),
            PlatformDictConstants.LOOKUP_INSUPLC_ADMDVS,
            values);
    }

    /** 本地：从 1901 字典镜像批量查询险种类型名称，无网关出站。 */
    @Override
    public Map<String, String> lookupInsutypeLabels(Collection<String> insutypeCodes) {
        if (insutypeCodes == null || insutypeCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> values = insutypeCodes.stream()
            .filter(StringUtils::isNotEmpty)
            .map(String::trim)
            .distinct()
            .toList();
        if (values.isEmpty()) {
            return Collections.emptyMap();
        }
        return lookupService.lookupLabels(
            com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils.getCurrentTenantId(),
            PlatformDictConstants.DICT_TYPE_INSUTYPE,
            values);
    }
}
