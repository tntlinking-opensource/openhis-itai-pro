/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.contract;

import com.openhis.health.infrastructure.integration.insurance.contract.ExtensionOut.InsuranceContractTerminalQueryExtensionOut;

import java.util.Optional;

/**
 * 医保契约终端参数只读扩展（定点机构编码/名称/统筹区划）。
 *
 * <p><b>数据源</b>：{@code chs-contract.properties} 的 {@code contract.{bus_no}.*}，
 * <strong>不</strong>读 {@code fin_contract.fixmedins_code}/{@code adm_vs} 等历史列。</p>
 *
 * <p><b>吉林实现</b>：{@code InsuranceContractTerminalExtensionImpl} →
 * {@code ChsContractPropertiesAccess}。</p>
 */
public interface InsuranceContractTerminalExtension {

    /**
     * 扩展是否已装配（未装 CHS Module 时为 false）。
     *
     * @return true 表示可从 properties 解析终端参数
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 按契约业务号解析定点机构终端参数。
     *
     * @param contractBusNo {@code fin_contract.bus_no}（与 chs-contract 配置键对齐）
     * @return 终端参数；bus_no 空、未装配或配置段缺失时 empty
     */
    default Optional<InsuranceContractTerminalQueryExtensionOut> findByContractBusNo(String contractBusNo) {
        return Optional.empty();
    }
}
