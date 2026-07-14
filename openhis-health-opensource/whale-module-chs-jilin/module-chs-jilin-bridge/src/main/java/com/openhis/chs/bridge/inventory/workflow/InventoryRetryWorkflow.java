/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.inventory.invoker.Inv35xxRetryInvoker;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.openhis.chs.domain.inventory.access.InventoryRetryAccess;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.RetryPendingInvUploadsCommand;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.result.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.service.YbInvUploadDomainService;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsGatewayOutputSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3501–3506】进销存失败重试 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>扫描可重试行 → Inv35xxRetry 重放 → 更新 yb_inv_upload 状态。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.inventory.access.InventoryRetryAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryRetryWorkflow implements InventoryRetryAccess {

    private final Inv35xxRetryInvoker inv35xxRetryInvoker;
    private final InvPlatformSupport invPlatformSupport;
    private final YbInvUploadDomainService uploadDomainService;
    private final ChsInventoryProperties inventoryProperties;

    public RetryPendingInvUploadsResult retry(RetryPendingInvUploadsCommand command) {
        RetryPendingInvUploadsResult result = new RetryPendingInvUploadsResult();
        if (!inventoryProperties.isUploadEnabled()) {
            return result;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        int batchSize = inventoryProperties.getRetryWorkerBatchSize();
        if (command != null && command.getBatchSize() != null && command.getBatchSize() > 0) {
            batchSize = command.getBatchSize();
        }
        for (YbInvUpload row : uploadDomainService.findRetryable(tenantId, batchSize)) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (isEmpty(row.getRequestJson())) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            result.setAttempted(result.getAttempted() + 1);
            uploadDomainService.recordRetryAttempt(row);
            if (retryStoredUpload(row)) {
                result.setSucceeded(result.getSucceeded() + 1);
            } else {
                result.setFailed(result.getFailed() + 1);
            }
        }
        return result;
    }

    private boolean retryStoredUpload(YbInvUpload row) {
        if (row == null || isEmpty(row.getInfno()) || isEmpty(row.getRequestJson())) {
            return false;
        }
        InvContractContext ctx = invPlatformSupport.resolveContract(row.getContractBusNo(), row.getContractId());
        JSONObject input = JSON.parseObject(row.getRequestJson());
        try {
            ResultBody body = inv35xxRetryInvoker.invoke(
                ctx.busNo(), row.getInfno(), input, invPlatformSupport.buildInvokeContext());
            JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
            JSONObject platformResult = output.getJSONObject("result");
            if (platformResult == null) {
                platformResult = output;
            }
            String retRslt = InvPlatformOutputParser.readSaleUploadField(platformResult, "retRslt", "ret_rslt");
            String msgRslt = InvPlatformOutputParser.readSaleUploadField(platformResult, "msgRslt", "msg_rslt");
            String uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_SUCCESS;
            if (requiresPlatformRetRslt(row.getInfno())) {
                if (!ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS.equals(retRslt)) {
                    uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_FAILED;
                }
            } else {
                retRslt = ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS;
            }
            uploadDomainService.applyRetrySuccess(row, uploadStatus, retRslt, msgRslt, JSON.toJSONString(body));
            return ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus);
        } catch (RuntimeException ex) {
            uploadDomainService.applyRetryFailure(row, ex.getMessage());
            return false;
        }
    }

    private static boolean requiresPlatformRetRslt(String infno) {
        return ChsInfNo.INVENTORY_SALE.equals(infno) || ChsInfNo.INVENTORY_SALE_RETURN.equals(infno);
    }
}
