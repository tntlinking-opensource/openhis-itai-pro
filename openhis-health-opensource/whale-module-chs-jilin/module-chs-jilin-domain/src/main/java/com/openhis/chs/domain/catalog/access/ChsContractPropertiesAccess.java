/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.access;

import java.util.Optional;

/** * 医保对接终端配置（唯一数据源：{@code chs-contract.properties}，键 {@code contract.{bus_no}.*}）。
 *
 * <p>{@code fin_contract} 解耦：主数据表仅提供
 * {@link com.openhis.chs.domain.contract.ChsContractSnapshot#busNo()} 作为配置键，
 * 不读取表内的 {@code fixmedins_code}、{@code adm_vs}、密钥等列。</p>
 */
public interface ChsContractPropertiesAccess {

    /** 定点医疗机构编码（9102 文件下载、msgid 前缀等） */
    Optional<String> findFixmedinsCode(String contractBusNo);

    /** 定点医疗机构名称（3101 {@code medins_name} 等） */
    Optional<String> findFixmedinsName(String contractBusNo);

    /** 就医地医保区划（1312 {@code insu_admdvs}、TransBody {@code mdtrtarea_admvs} 等） */
    Optional<String> findAdmVs(String contractBusNo);
}
