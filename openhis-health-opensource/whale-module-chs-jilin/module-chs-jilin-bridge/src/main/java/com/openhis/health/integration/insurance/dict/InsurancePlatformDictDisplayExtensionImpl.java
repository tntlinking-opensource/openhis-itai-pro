/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.dict;

import com.openhis.chs.domain.share.constants.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictLookupService;
import com.openhis.health.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.whale.core.application.util.StringUtils;
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
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
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
            com.openhis.whale.core.infrastructure.multitenancy.TenantUtils.getCurrentTenantId(),
            PlatformDictConstants.LOOKUP_INSUPLC_ADMDVS,
            values);
    }
}
