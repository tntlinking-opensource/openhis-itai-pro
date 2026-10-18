/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.platformdict;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 1901 平台字典展示扩展（参保地 value→label）。
 *
 * <p><b>吉林实现</b>：{@code InsurancePlatformDictDisplayExtensionImpl} → {@link com.zxplat.chs.domain.platformdict.facade.ChsPlatformDictOperations}。</p>
 */
public interface InsurancePlatformDictDisplayExtension {

    /**
     * 平台字典展示扩展是否已装配。
     *
     * @return false 时参保地等仅展示原始 code
     */
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

    /**
     * 批量解析险种类型（insutype）展示名。
     *
     * @param insutypeCodes 平台险种码列表
     * @return code → label；未命中时不包含该 key
     */
    default Map<String, String> lookupInsutypeLabels(Collection<String> insutypeCodes) {
        return Collections.emptyMap();
    }
}
