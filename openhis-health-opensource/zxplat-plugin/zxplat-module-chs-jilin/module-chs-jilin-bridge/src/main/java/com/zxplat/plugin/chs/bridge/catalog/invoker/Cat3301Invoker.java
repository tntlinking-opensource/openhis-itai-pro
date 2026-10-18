/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.catalog.command.UploadCatalog3301Command;
import com.zxplat.plugin.chs.domain.catalog.command.UploadCatalog3301RowCommand;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3301UploadResult;
import com.zxplat.plugin.chs.domain.catalog.result.Catalog3301UploadRowResult;
import com.zxplat.plugin.chs.domain.catalog.service.YbCatalogUploadRecordDomainService;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3301Input;
import com.zxplat.plugin.chs.gateway.scheme.catalog.assembler.Catalog3301InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3301】目录对照上传出站（gateway invoke → DB 上传审计）。
 * <p>按 {@link ChsCatalogConstants#CATALOG_3301_BATCH_SIZE} 分批调用平台，逐行写入 {@code yb_catalog_upload_record}。</p>
 */
@Component
@RequiredArgsConstructor
public class Cat3301Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog3301InputAssembler catalog3301InputAssembler;
    private final ChsInsuranceContractReader contractReader;
    private final YbCatalogUploadRecordDomainService uploadRecordDomainService;
    private final CatalogPlatformSupport catalogPlatformSupport;

    /**
     * 调用 3301 上传院内目录对照，并逐行落库上传审计。
     */
    public Catalog3301UploadResult invoke(UploadCatalog3301Command command) {
        if (command == null || command.getRows() == null || command.getRows().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "3301 上传行不能为空");
        }
        ChsContractSnapshot contract = contractReader.requireContract(command.getContractBusNo(), null);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录后再上传医保目录对照");
        InvokeContext ctx = catalogPlatformSupport.buildInvokeContext(operatorId);

        List<Catalog3301UploadRowResult> rowResults = new ArrayList<>();
        int success = 0;
        int fail = 0;
        List<UploadCatalog3301RowCommand> rows = command.getRows();
        for (int offset = 0; offset < rows.size(); offset += ChsCatalogConstants.CATALOG_3301_BATCH_SIZE) {
            int end = Math.min(offset + ChsCatalogConstants.CATALOG_3301_BATCH_SIZE, rows.size());
            List<UploadCatalog3301RowCommand> batch = rows.subList(offset, end);
            Cat3301Input input = catalog3301InputAssembler.assemble(batch);
            ResultBody body = gatewayClient.invoke(
                command.getContractBusNo(),
                ChsInfNo.CATALOG_MAPPING_UPLOAD,
                input,
                ctx);
            boolean batchOk = body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
            String batchErr = batchOk ? null : resolveErrMsg(body);
            JSONObject inputSnapshot = ChsSchemeJsonSupport.toJsonObject(input);
            for (UploadCatalog3301RowCommand row : batch) {
                Catalog3301UploadRowResult rowResult = new Catalog3301UploadRowResult()
                    .setProductId(row.getProductId())
                    .setProductTable(row.getProductTable())
                    .setFixmedinsHilistId(row.getFixmedinsHilistId())
                    .setSuccess(batchOk)
                    .setErrorMsg(batchErr);
                rowResults.add(rowResult);
                uploadRecordDomainService.saveUpload3301Audit(
                    contractId, tenantId, operatorId, row, batchOk, null,
                    inputSnapshot, toResponseSnapshot(body), batchErr);
                if (batchOk) {
                    success++;
                } else {
                    fail++;
                }
            }
        }
        Catalog3301UploadResult result = new Catalog3301UploadResult()
            .setAllSuccess(fail == 0)
            .setSuccessCount(success)
            .setFailCount(fail)
            .setRows(rowResults);
        if (fail == 0) {
            result.setSummary("3301 上传成功，共 " + success + " 条");
        } else if (success == 0) {
            result.setSummary("3301 上传失败：" + firstError(rowResults));
        } else {
            result.setSummary("3301 部分失败：成功 " + success + " 条，失败 " + fail + " 条");
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

    private static String firstError(List<Catalog3301UploadRowResult> rows) {
        for (Catalog3301UploadRowResult row : rows) {
            if (!row.isSuccess() && isNotEmpty(row.getErrorMsg())) {
                return row.getErrorMsg();
            }
        }
        return "未知错误";
    }
}
