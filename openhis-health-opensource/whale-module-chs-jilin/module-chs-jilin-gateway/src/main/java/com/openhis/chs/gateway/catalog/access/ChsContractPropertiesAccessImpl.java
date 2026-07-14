/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.catalog.access;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
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
