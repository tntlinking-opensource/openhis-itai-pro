package com.openhis.chs.application.catalog.port;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 独立 chs-application 进程：无 {@code fin_contract}，仅以 {@code chs-contract.properties} 校验 {@code bus_no}。
 */
@Component
@ConditionalOnMissingBean(ChsInsuranceContractPort.class)
public class ChsConfigContractPort implements ChsInsuranceContractPort {

    @Override
    public ChsContractSnapshot requireContract(String contractBusNo, Long contractId) {
        ContractConfig config = resolveConfig(contractBusNo, contractId);
        return toSnapshot(config);
    }

    private static ContractConfig resolveConfig(String contractBusNo, Long contractId) {
        if (StringUtils.isNotBlank(contractBusNo)) {
            ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo.trim());
            if (config == null) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "医保契约不存在: " + contractBusNo);
            }
            return config;
        }
        if (contractId != null) {
            ContractConfig config = ContractConfigLoader.getByContractId(contractId);
            if (config == null) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "费用性质不存在: " + contractId);
            }
            return config;
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
    }

    /** 快照仅含配置键；终端参数通过 {@code busNo} 再查 properties。 */
    private static ChsContractSnapshot toSnapshot(ContractConfig config) {
        String busNo = trimToEmpty(config.getBusNo());
        if (busNo.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "chs-contract 未配置 bus_no");
        }
        return new ChsContractSnapshot(config.getContractId(), busNo);
    }

    @Override
    public String requireContractBusNo(ChsContractSnapshot contract) {
        if (contract == null || StringUtils.isBlank(contract.busNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "医保契约未配置 bus_no");
        }
        return contract.busNo().trim();
    }
}