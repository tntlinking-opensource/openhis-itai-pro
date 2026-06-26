/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.platformdict;

import com.openhis.chs.domain.platformdict.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.dto.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.platformdict.facade.ChsPlatformDictOperations;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 1901 平台字典展示 ↔ 本地镜像 lookup。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsurancePlatformDictDisplayExtensionImpl implements InsurancePlatformDictDisplayExtension {

    private final ChsPlatformDictOperations platformDictOperations;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<String, String> lookupInsuplcAdmdvsLabels(Collection<String> admdvsCodes) {
        if (admdvsCodes == null || admdvsCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> values = admdvsCodes.stream()
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .distinct()
            .toList();
        if (values.isEmpty()) {
            return Collections.emptyMap();
        }
        LookupPlatformDictLabelsRequest request = new LookupPlatformDictLabelsRequest()
            .setDictType(PlatformDictConstants.LOOKUP_INSUPLC_ADMDVS)
            .setDictValues(values);
        return platformDictOperations.lookupDictLabels(request).getLabelsByValue();
    }
}
