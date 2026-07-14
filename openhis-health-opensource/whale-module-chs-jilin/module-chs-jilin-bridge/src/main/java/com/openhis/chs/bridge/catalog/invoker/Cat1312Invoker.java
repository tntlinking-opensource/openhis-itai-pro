/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.catalog.mapper.CatalogPlatformMapper;
import com.openhis.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.Refresh1312Command;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogDomainService;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.catalog.assembler.Catalog1312InputAssembler;
import com.openhis.chs.gateway.scheme.catalog.model.Cat1312Input;
import com.openhis.chs.gateway.scheme.catalog.parser.Cat1312OutputParser;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【1312】国家目录分页拉取出站（gateway invoke → domain upsert）。 */
@Component
@RequiredArgsConstructor
public class Cat1312Invoker implements com.openhis.chs.domain.catalog.access.Cat1312Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog1312InputAssembler catalog1312InputAssembler;
    private final YbNationalCatalogDomainService catalogDomainService;
    private final YbNationalCatalogQueryService catalogQueryService;
    private final CatalogPlatformMapper catalogPlatformMapper;
    private final ChsInsuranceContractReader contractReader;
    private final CatalogPlatformSupport catalogPlatformSupport;

    @Override
    public int invoke(Refresh1312Command command) {
        ChsContractSnapshot contract = contractReader.requireContract(
            command.getContractBusNo(), command.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录后再刷新医保目录");
        String insuAdmdvs = catalogPlatformSupport.requireAdmVs(contractBusNo);

        Cat1312Input input = catalog1312InputAssembler.assemble(
            insuAdmdvs,
            command,
            resolveUpdtTime(contractId, command.getUpdtTime()),
            command.getPlatformPageNum(),
            command.getPlatformPageSize());

        ResultBody body = gatewayClient.invoke(
            contractBusNo,
            ChsInfNo.CATALOG_QUERY,
            input,
            catalogPlatformSupport.buildInvokeContext(operatorId));

        assertPlatformSuccess(body);
        return catalogDomainService.upsertRows(
            contractId,
            TenantUtils.getCurrentTenantId(),
            operatorId,
            catalogPlatformMapper.toUpsertCommands(Cat1312OutputParser.parseRows(body)));
    }

    private String resolveUpdtTime(Long contractId, String requestUpdtTime) {
        if (isNotEmpty(requestUpdtTime)) {
            return requestUpdtTime.trim();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return catalogQueryService.findMaxUpdtTime(contractId, tenantId)
            .map(ChsCatalogConstants.UPDT_TIME_DATE_FORMATTER::format)
            .orElse(ChsCatalogConstants.DEFAULT_CATALOG_UPDT_TIME);
    }

    private static void assertPlatformSuccess(ResultBody body) {
        if (body == null || !ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            String msg = "empty response";
            if (body != null && body.getErrMsg() != null) {
                msg = body.getErrMsg();
            }
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "1312 平台失败: " + msg);
        }
    }
}
