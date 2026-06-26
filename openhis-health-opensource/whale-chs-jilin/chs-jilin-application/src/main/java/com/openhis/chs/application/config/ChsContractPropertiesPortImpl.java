package com.openhis.chs.application.config;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link ContractConfigLoader} 的领域端口实现：按 {@code bus_no} 读取 {@code chs-contract.properties}。
 */
@Component
public class ChsContractPropertiesPortImpl implements ChsContractPropertiesPort {

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
        if (StringUtils.isBlank(contractBusNo)) {
            return Optional.empty();
        }
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo.trim());
        if (config == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(trimToNull(getter.apply(config)));
    }
}