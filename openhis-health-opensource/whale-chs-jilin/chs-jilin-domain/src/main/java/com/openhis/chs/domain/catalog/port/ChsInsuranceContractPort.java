package com.openhis.chs.domain.catalog.port;

/**
 * 解析医保业务所绑定的费用性质契约。
 *
 * <p>embedded（HIS + CHS 同进程）：从 {@code fin_contract} 校验存在并取 {@code id}/{@code bus_no}；
 * 独立 chs-server：仅校验 {@code chs-contract.properties} 中存在对应 {@code bus_no}。</p>
 */
public interface ChsInsuranceContractPort {

    ChsContractSnapshot requireContract(String contractBusNo, Long contractId);

    String requireContractBusNo(ChsContractSnapshot contract);
}
