/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.contract;

import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.health.infrastructure.integration.insurance.contract.InsuranceContractTerminalExtension;
import com.openhis.health.infrastructure.integration.insurance.contract.ExtensionOut.InsuranceContractTerminalQueryExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 吉林医保契约终端参数 SPI 实现（读 {@code chs-contract.properties}）。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceContractTerminalExtension}</p>
 * <p><b>Bridge 委托</b>：{@link ChsContractPropertiesAccess}（不读 {@code fin_contract} 历史列）</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsContractPropertiesAccess.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceContractTerminalExtensionImpl implements InsuranceContractTerminalExtension {

    private final ChsContractPropertiesAccess contractPropertiesAccess;

    /**
     * 已装配 CHS 时终端参数可读。
     *
     * @return 恒 true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 按 bus_no 从 chs-contract 组装定点机构编码/名称/区划。
     *
     * @param contractBusNo 契约业务号
     * @return 至少含 fixmedinsCode 时返回；配置段或编码缺失时 empty
     */
    @Override
    public Optional<InsuranceContractTerminalQueryExtensionOut> findByContractBusNo(String contractBusNo) {
        String busNo = trimToNull(contractBusNo);
        // bus_no 为空无法对齐 properties 配置键
        if (busNo == null) {
            return Optional.empty();
        }
        Optional<String> fixmedinsOpt = contractPropertiesAccess.findFixmedinsCode(busNo);
        // 配置段不存在或 fixmedins_code 未配则不上送空壳
        if (fixmedinsOpt.isEmpty()) {
            return Optional.empty();
        }
        InsuranceContractTerminalQueryExtensionOut out = new InsuranceContractTerminalQueryExtensionOut();
        out.setContractBusNo(busNo);
        out.setFixmedinsCode(fixmedinsOpt.get());
        out.setFixmedinsName(contractPropertiesAccess.findFixmedinsName(busNo).orElse(null));
        out.setAdmVs(contractPropertiesAccess.findAdmVs(busNo).orElse(null));
        return Optional.of(out);
    }
}
