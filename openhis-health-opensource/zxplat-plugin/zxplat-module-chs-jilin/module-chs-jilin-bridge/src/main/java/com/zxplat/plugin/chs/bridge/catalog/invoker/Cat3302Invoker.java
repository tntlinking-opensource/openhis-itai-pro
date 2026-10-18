/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.catalog.command.RevokeCatalog3302Command;
import com.zxplat.plugin.chs.domain.catalog.command.RevokeCatalog3302RowCommand;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3302RevokeResult;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3302RevokeRowResult;
import com.zxplat.plugin.chs.domain.catalog.service.YbCatalogUploadRecordDomainService;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3302Input;
import com.zxplat.plugin.chs.gateway.scheme.catalog.assembler.Catalog3302InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3302】目录对照撤销出站（gateway invoke → DB 撤销审计）。
 * <p>逐行调用平台撤销，并写入 {@code yb_catalog_upload_record} 撤销审计。</p>
 */
@Component
@RequiredArgsConstructor
public class Cat3302Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog3302InputAssembler catalog3302InputAssembler;
    private final ChsInsuranceContractReader contractReader;
    private final YbCatalogUploadRecordDomainService uploadRecordDomainService;
    private final CatalogPlatformSupport catalogPlatformSupport;

    /**
     * 调用 3302 撤销院内目录对照，并逐行落库撤销审计。
     */
    public Catalog3302RevokeResult invoke(RevokeCatalog3302Command command) {
        if (command == null || command.getRows() == null || command.getRows().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "3302 撤销行不能为空");
        }
        ChsContractSnapshot contract = contractReader.requireContract(command.getContractBusNo(), null);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录后再撤销医保目录对照");
        String fixmedinsCode = catalogPlatformSupport.requireFixmedinsCode(command.getContractBusNo());
        InvokeContext ctx = catalogPlatformSupport.buildInvokeContext(operatorId);

        List<Catalog3302RevokeRowResult> rowResults = new ArrayList<>();
        int success = 0;
        int fail = 0;
        for (RevokeCatalog3302RowCommand row : command.getRows()) {
            catalog3302InputAssembler.validateRow(row);
            Cat3302Input input = catalog3302InputAssembler.assemble(fixmedinsCode, row);
            ResultBody body = gatewayClient.invoke(
                command.getContractBusNo(),
                ChsInfNo.CATALOG_MAPPING_REVOKE,
                input,
                ctx);
            boolean ok = body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
            String err = ok ? null : resolveErrMsg(body);
            JSONObject inputSnapshot = ChsSchemeJsonSupport.toJsonObject(input);
            rowResults.add(new Catalog3302RevokeRowResult()
                .setProductId(row.getProductId())
                .setProductTable(row.getProductTable())
                .setFixmedinsHilistId(row.getFixmedinsHilistId())
                .setSuccess(ok)
                .setErrorMsg(err));
            uploadRecordDomainService.saveRevoke3302Audit(
                contractId, tenantId, operatorId, row, ok, inputSnapshot, toResponseSnapshot(body), err);
            if (ok) {
                success++;
            } else {
                fail++;
            }
        }
        Catalog3302RevokeResult result = new Catalog3302RevokeResult()
            .setAllSuccess(fail == 0)
            .setSuccessCount(success)
            .setFailCount(fail)
            .setRows(rowResults);
        if (fail == 0) {
            result.setSummary("3302 撤销成功，共 " + success + " 条");
        } else if (success == 0) {
            result.setSummary("3302 撤销失败：" + firstRevokeError(rowResults));
        } else {
            result.setSummary("3302 部分失败：成功 " + success + " 条，失败 " + fail + " 条");
        }
        return result;
    }

    private static String toResponseSnapshot(ResultBody body) {
        if (body == null) {
            return null;
        }
        return JSON.toJSONString(body);
    }

    private static String resolveErrMsg(ResultBody body) {
        if (body == null) {
            return "平台无应答";
        }
        if (isNotEmpty(body.getErrMsg())) {
            return body.getErrMsg().trim();
        }
        return "infcode=" + body.getInfcode();
    }

    private static String firstRevokeError(List<Catalog3302RevokeRowResult> rows) {
        for (Catalog3302RevokeRowResult row : rows) {
            if (!row.isSuccess() && isNotEmpty(row.getErrorMsg())) {
                return row.getErrorMsg();
            }
        }
        return "未知错误";
    }
}
