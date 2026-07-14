/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.domain.inpatient.command.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.result.IpRevokeFeeDetailResult;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2302Input;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 【2302】撤销住院费用明细 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_inpatient_fee_upload。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2302Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbInpatientFeeUploadQueryService feeUploadQueryService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;

    /**
     * 撤销已上传住院费用明细并更新本地上传记录。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo 及可选 feedetlSn 列表
     * @return 撤销结果（撤销条数）
     */
    public IpRevokeFeeDetailResult invoke(IpRevokeFeeDetailCommand command) {
        InpatientSettleCommandAssembler.validateEncounterPlatformCommand(
            command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        List<YbInpatientFeeUpload> uploads = resolveUploadedFees(command, tenantId);
        if (uploads.isEmpty()) {
            return new IpRevokeFeeDetailResult().setRevokedCount(0);
        }
        List<String> feedetlSnList = new ArrayList<>();
        for (YbInpatientFeeUpload upload : uploads) {
            feedetlSnList.add(upload.getFeedetlSn());
        }
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());

        Ip2302Input input2302 = IpInpatientSettleInputAssembler.assemble2302(
            command.getMdtrtId(), command.getPsnNo(), feedetlSnList);
        ResultBody body2302 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_FEE_REVOKE, input2302, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2302, ChsInfNo.INPATIENT_FEE_REVOKE);

        inpatientFeeUploadDomainService.markRevokedByUploads(uploads);
        return new IpRevokeFeeDetailResult().setRevokedCount(uploads.size());
    }

    /**
     * 按收费项 ID 解析已上传且待撤销的住院费用明细。
     *
     * @param command 撤销命令（含 chargeItemIds）
     * @param tenantId 租户 ID
     * @return 已上传的费用上传记录列表
     */
    private List<YbInpatientFeeUpload> resolveUploadedFees(IpRevokeFeeDetailCommand command, Long tenantId) {
        List<YbInpatientFeeUpload> uploads = new ArrayList<>();
        if (command.getChargeItemIds() == null || command.getChargeItemIds().isEmpty()) {
            return uploads;
        }
        for (Long chargeItemId : command.getChargeItemIds()) {
            if (chargeItemId == null) {
                continue;
            }
            feeUploadQueryService.findOptionalByChargeItemId(chargeItemId, tenantId)
                .filter(row -> YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(uploads::add);
        }
        return uploads;
    }
}
