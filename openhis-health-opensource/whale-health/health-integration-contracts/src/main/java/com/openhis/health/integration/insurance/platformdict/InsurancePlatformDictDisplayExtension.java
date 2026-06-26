/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.platformdict;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 1901 平台字典展示扩展（参保地 value→label）。
 *
 * <p><b>吉林实现</b>：{@code InsurancePlatformDictDisplayExtensionImpl} → {@link com.openhis.chs.domain.platformdict.facade.ChsPlatformDictOperations}。</p>
 */
public interface InsurancePlatformDictDisplayExtension {

    default boolean isEnabled() {
        return false;
    }

    /**
     * 批量解析参保地医保区划展示名。
     *
     * @param admdvsCodes 6 位区划码列表
     * @return code → label；未命中时不包含该 key
     */
    default Map<String, String> lookupInsuplcAdmdvsLabels(Collection<String> admdvsCodes) {
        return Collections.emptyMap();
    }
}
