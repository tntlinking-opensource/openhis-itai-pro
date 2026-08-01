/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.inventory.invoker.Inv35xxRetryInvoker;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.openhis.chs.domain.inventory.access.InventoryRetryAccess;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.RetryInvUploadWithRequestCommand;
import com.openhis.chs.domain.inventory.command.RetryPendingInvUploadsCommand;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.result.RetryInvUploadWithRequestResult;
import com.openhis.chs.domain.inventory.result.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.service.YbInvUploadDomainService;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inventory.parser.InvPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsGatewayOutputSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【3501–3506】进销存失败重试 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>扫描可重试行 → Inv35xxRetry 重放 → 更新 yb_inv_upload 状态；
 * 单条/批量补传 SUCCESS 后按配对规则跟传伴生（后置 3502 或前置 3502 成功后的 3505）。</p>
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
    private final InventoryRetryCompanionFollowUp companionFollowUp;

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
                // Cmd：批量重试成功后同样跟传配对
                companionFollowUp.followUpAfterRetrySuccess(row);
            } else {
                result.setFailed(result.getFailed() + 1);
            }
        }
        return result;
    }

    /**
     * 单条补传：可选覆盖 request_json 后重放持久化报文；SUCCESS 后触发配对跟传。
     *
     * @param command 含 uploadId 与可选 requestJson
     * @return 补传结果
     */
    @Override
    public RetryInvUploadWithRequestResult retryOne(RetryInvUploadWithRequestCommand command) {
        RetryInvUploadWithRequestResult result = new RetryInvUploadWithRequestResult();
        if (!inventoryProperties.isUploadEnabled()) {
            result.setSucceeded(false);
            result.setPlatformMsg("进销存上报开关已关闭");
            return result;
        }
        if (command == null || command.getUploadId() == null) {
            result.setSucceeded(false);
            result.setPlatformMsg("uploadId 不能为空");
            return result;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInvUpload> loaded = uploadDomainService.findByIdForTenant(command.getUploadId(), tenantId);
        if (loaded.isEmpty()) {
            result.setUploadId(command.getUploadId());
            result.setSucceeded(false);
            result.setPlatformMsg("上报流水不存在");
            return result;
        }
        YbInvUpload row = loaded.get();
        // Gate：仅 FAILED/PENDING 可补传，避免 SUCCESS 双报平台
        if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
            result.setUploadId(row.getId());
            result.setSucceeded(false);
            result.setUploadStatus(row.getUploadStatus());
            result.setPlatformMsg("已成功上报，禁止重复补传");
            result.setResponseJson(row.getResponseJson());
            return result;
        }
        if (ChsInventoryConstants.UPLOAD_STATUS_DELETED.equals(row.getUploadStatus())) {
            result.setUploadId(row.getId());
            result.setSucceeded(false);
            result.setUploadStatus(row.getUploadStatus());
            result.setPlatformMsg("流水已标记删除，不可补传");
            result.setResponseJson(row.getResponseJson());
            return result;
        }
        if (isNotEmpty(command.getRequestJson())) {
            row = uploadDomainService.updateRequestJson(row, command.getRequestJson());
        }
        if (isEmpty(row.getRequestJson())) {
            result.setUploadId(row.getId());
            result.setSucceeded(false);
            result.setUploadStatus(row.getUploadStatus());
            result.setPlatformMsg("request_json 为空，无法补传（出站前校验失败需手工填写报文）");
            return result;
        }
        result.setUploadId(row.getId());
        uploadDomainService.recordRetryAttempt(row);
        boolean succeeded = retryStoredUpload(row);
        result.setSucceeded(succeeded);
        result.setUploadStatus(row.getUploadStatus());
        result.setPlatformMsg(row.getPlatformMsg());
        result.setResponseJson(row.getResponseJson());
        // Cmd：主交易补传 SUCCESS 后跟传配对（伴生 3502 或前置 3502 后的 3505）
        if (succeeded) {
            companionFollowUp.followUpAfterRetrySuccess(row);
        }
        return result;
    }

    /**
     * 重放已持久化的 request_json 出站并更新流水状态。
     *
     * @param row 待重试的上报流水
     * @return 重放是否成功
     */
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
            String uploadStatus = ChsInventoryConstants.UPLOAD_STATUS_SUCCESS;
            String retRslt = ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS;
            String msgRslt = null;
            // 3505/3506：含省本级空 output 无 ret_rslt 的成功回填
            if (requiresPlatformRetRslt(row.getInfno())) {
                InvPlatformOutputParser.SaleUploadBizResult biz =
                    InvPlatformOutputParser.resolveSaleUploadBizResult(output);
                uploadStatus = biz.uploadStatus();
                retRslt = biz.retRslt();
                msgRslt = biz.msgRslt();
            }
            uploadDomainService.applyRetrySuccess(row, uploadStatus, retRslt, msgRslt, JSON.toJSONString(body));
            return ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus);
        } catch (RuntimeException ex) {
            uploadDomainService.applyRetryFailure(row, ex.getMessage());
            return false;
        }
    }

    /**
     * 3505/3506 重放后须按平台业务码判定 SUCCESS/FAILED（含省本级空 output）。
     *
     * @param infno 接口编号
     * @return 是否需要解析 ret_rslt
     */
    private static boolean requiresPlatformRetRslt(String infno) {
        return ChsInfNo.INVENTORY_SALE.equals(infno) || ChsInfNo.INVENTORY_SALE_RETURN.equals(infno);
    }
}
