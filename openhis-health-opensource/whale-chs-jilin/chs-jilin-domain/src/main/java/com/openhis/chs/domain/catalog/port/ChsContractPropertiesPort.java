package com.openhis.chs.domain.catalog.port;

import java.util.Optional;

/**
 * 医保对接终端配置（唯一数据源：{@code chs-contract.properties}，键为 {@code contract.{bus_no}.*}）。
 *
 * <p>与 {@code fin_contract} 解耦：主数据表仅提供 {@link ChsContractSnapshot#busNo()} 作为配置键，
 * 不读取表内的 {@code fixmedins_code}、{@code adm_vs}、密钥等列。</p>
 */
public interface ChsContractPropertiesPort {

    /** 定点医疗机构编码（9102 等文件下载入参、msgid 前缀等）。 */
    Optional<String> findFixmedinsCode(String contractBusNo);

    /** 就医地医保区划（1312 等 {@code insu_admdvs}、TransBody.mdtrtarea_admvs 等）。 */
    Optional<String> findAdmVs(String contractBusNo);
}
