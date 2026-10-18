/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.bridge.inventory.support.InvPlatformSupport;
import com.zxplat.plugin.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.command.InvDelete3507Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.result.InvDelete3507Result;
import com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inventory.model.Inv3507Input;
import com.zxplat.plugin.chs.gateway.scheme.inventory.assembler.InvPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【3507】进销存数据删除 gateway 出站；成功时 upsert 删除流水并 markDeletedByBatchNo。 */
@Component
@RequiredArgsConstructor
public class Inv3507Invoker implements com.zxplat.plugin.chs.domain.inventory.access.Inv3507Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final InvPlatformSupport invPlatformSupport;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbInvUploadDomainService uploadDomainService;

    public InvDelete3507Result invoke(InvDelete3507Command command) {
        assertDeleteCommand(command);
        InvContractContext ctx = invPlatformSupport.resolveContract(command.getContractBusNo(), command.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再操作进销存");

        InvDelete3507Result result = new InvDelete3507Result();
        result.setFixmedinsBchno(command.getFixmedinsBchno().trim());
        result.setInvDataType(command.getInvDataType().trim());

        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setFixmedinsBchno(command.getFixmedinsBchno().trim());
        line.setSourceLineId(0L);

        Inv3507Input input = InvPlatformInputAssembler.assemble3507(
            command.getFixmedinsBchno().trim(), command.getInvDataType().trim());
        String requestJson = JSON.toJSONString(input);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(), ChsInfNo.INVENTORY_DELETE, input, invPlatformSupport.buildInvokeContext());
            ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INVENTORY_DELETE);
            YbInvUpload saved = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_DELETE, line,
                ChsInventoryConstants.SOURCE_TYPE_MANUAL, command.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS,
                ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS, null, requestJson, JSON.toJSONString(body),
                operatorId, tenantId, false);
            uploadDomainService.markDeletedByBatchNo(
                tenantId, command.getFixmedinsBchno().trim(), command.getInvDataType().trim());
            return result.setUploadId(saved.getId()).setUploadStatus(saved.getUploadStatus());
        } catch (RuntimeException ex) {
            YbInvUpload failed = uploadDomainService.upsertUploadRecord(
                null, ctx.contract().id(), ctx.busNo(), ChsInfNo.INVENTORY_DELETE, line,
                ChsInventoryConstants.SOURCE_TYPE_MANUAL, command.getSourceId(),
                ChsInventoryConstants.UPLOAD_STATUS_FAILED,
                ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED, ex.getMessage(), requestJson, null,
                operatorId, tenantId, true);
            return result.setUploadId(failed.getId()).setUploadStatus(failed.getUploadStatus())
                .setPlatformMsg(ex.getMessage());
        }
    }

    private static void assertDeleteCommand(InvDelete3507Command command) {
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "删除参数不能为空");
        }
        if (isEmpty(command.getFixmedinsBchno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "fixmedins_bchno 不能为空");
        }
        if (isEmpty(command.getInvDataType())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "inv_data_type 不能为空");
        }
    }
}
