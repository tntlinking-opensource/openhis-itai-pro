/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.person.mapper;

import com.zxplat.plugin.chs.application.foundation.person.request.YbPersonInfoRequest;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.outpatient.command.YbPersonQueryCommand;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.experimental.UtilityClass;

/**
 * 1101 读卡：UI 入参与领域 {@link YbPersonQueryCommand} 映射。
 */
@UtilityClass
public class InsurancePersonAppMapper {

    /** 将读卡入参转为 1101 领域查询命令。 */
    public static YbPersonQueryCommand toPersonQueryCommand(
            YbPersonInfoRequest command,
            ChsInsuranceContractReader contractReader,
            Long operatorId) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        YbPersonQueryCommand platformCommand = new YbPersonQueryCommand();
        platformCommand.setContractId(contract.id());
        platformCommand.setContractBusNo(contractReader.requireContractBusNo(contract));
        platformCommand.setMdtrtCertType(command.getMdtrtCertType());
        platformCommand.setMdtrtCertNo(command.getMdtrtCertNo());
        platformCommand.setCardSn(command.getCardSn());
        platformCommand.setCertno(command.getCertno());
        platformCommand.setPsnCertType(command.getPsnCertType());
        platformCommand.setPsnName(command.getPsnName());
        platformCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        platformCommand.setOperatorId(operatorId);
        platformCommand.setTenantId(TenantUtils.getCurrentTenantId());
        // UI 显式读卡须取平台最新 balc，跳过当日人员缓存。
        platformCommand.setForceRefresh(true);
        return platformCommand;
    }
}
