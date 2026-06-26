package com.openhis.health.integration.chs.adapter;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.health.domain.financial.contract.entity.Contract;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 将 HIS {@code fin_contract} 适配为医保契约关联键。
 *
 * <p><b>仅使用</b> {@link Contract#getId()}、{@link Contract#getBusNo()}：校验费用性质存在且
 * {@code bus_no} 非空。{@code fixmedins_code}、{@code adm_vs}、OAuth 密钥等<strong>不读库表</strong>，
 * 由 chs 模块 {@code chs-contract.properties}（键 {@code contract.{bus_no}.*}）提供。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInsuranceContractPortAdapter implements ChsInsuranceContractPort {

    private final InsuranceContractResolver insuranceContractResolver;

    @Override
    public ChsContractSnapshot requireContract(String contractBusNo, Long contractId) {
        Contract contract = insuranceContractResolver.requireContract(contractBusNo, contractId);
        String busNo = trimToEmpty(contract.getBusNo());
        if (busNo.isEmpty()) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    ChsErrorCodes.CONTRACT_INVALID, "费用性质未配置 bus_no，无法对接医保");
        }
        return new ChsContractSnapshot(contract.getId(), busNo);
    }

    @Override
    public String requireContractBusNo(ChsContractSnapshot contract) {
        if (contract == null || contract.busNo() == null || contract.busNo().isBlank()) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    ChsErrorCodes.CONTRACT_INVALID, "医保契约未配置 bus_no");
        }
        return contract.busNo().trim();
    }
}