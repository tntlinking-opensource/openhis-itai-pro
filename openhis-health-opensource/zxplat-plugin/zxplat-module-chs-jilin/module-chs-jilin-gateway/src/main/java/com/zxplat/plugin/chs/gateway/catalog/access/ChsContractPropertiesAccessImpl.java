/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.catalog.access;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import com.zxplat.plugin.chs.gateway.config.ContractConfigLoader;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link ContractConfigLoader} 的领域侧实现：按 {@code bus_no} 读取 {@code chs-contract.properties}。
 */
@Component
public class ChsContractPropertiesAccessImpl implements ChsContractPropertiesAccess {

    @Override
    public Optional<String> findFixmedinsCode(String contractBusNo) {
        return findField(contractBusNo, ContractConfig::getFixmedinsCode);
    }

    /**
     * 定点医疗机构名称（{@code contract.{bus_no}.fixmedins_name}）。
     *
     * @param contractBusNo 契约 bus_no
     * @return 非空名称
     */
    @Override
    public Optional<String> findFixmedinsName(String contractBusNo) {
        return findField(contractBusNo, ContractConfig::getFixmedinsName);
    }

    @Override
    public Optional<String> findAdmVs(String contractBusNo) {
        return findField(contractBusNo, ContractConfig::getAdmVs);
    }

    private static Optional<String> findField(
            String contractBusNo,
            java.util.function.Function<ContractConfig, String> getter) {
        if (isEmpty(contractBusNo)) {
            return Optional.empty();
        }
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo.trim());
        if (config == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(trimToNull(getter.apply(config)));
    }
}
