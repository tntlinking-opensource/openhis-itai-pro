/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.domain.outpatient.command.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokeFeeDetailResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2205Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2205InputAssembler;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2205】撤销未结算费用明细 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_clinic_pre_settle，写 yb_clinic_fee_upload、yb_clinic_pre_settle。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2205Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;

    /**
     * 撤销未结算门诊费用明细并更新本地上传状态。
     *
     * @param command 含 chrgBchno、contractBusNo、mdtrtId、psnNo 及 operatorId
     * @return 撤销结果（chrgBchno、是否已撤销）
     */
    public OpRevokeFeeDetailResult invoke(OpRevokeFeeDetailCommand command) {
        OpRevokeFeeDetailCommand enriched = enrichCommand(command);
        if (isEmpty(enriched.getChrgBchno())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 chrg_bchno");
        }
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(enriched.getTenantId());
        YbClinicPreSettle pre = clinicPreSettleDomainService.findByChrgBchno(enriched.getChrgBchno(), tenantId);
        if (pre != null && !YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            return new OpRevokeFeeDetailResult()
                .setChrgBchno(pre.getChrgBchno())
                .setRevoked(false);
        }
        String chrgBchno = enriched.getChrgBchno().trim();
        String mdtrtId;
        String psnNo;
        String contractBusNo;
        String insuplcAdmdvs;
        if (pre != null) {
            mdtrtId = pre.getMdtrtId();
            psnNo = pre.getPsnNo();
            if (isNotEmpty(enriched.getContractBusNo())) {
                contractBusNo = enriched.getContractBusNo().trim();
            } else {
                contractBusNo = pre.getContractBusNo();
            }
            if (isNotEmpty(enriched.getInsuplcAdmdvs())) {
                insuplcAdmdvs = enriched.getInsuplcAdmdvs();
            } else {
                insuplcAdmdvs = pre.getInsuplcAdmdvs();
            }
        } else {
            // 2204 已成功、2206 未落库时：无预结算快照，用命令补齐字段直接 2205
            mdtrtId = enriched.getMdtrtId();
            psnNo = enriched.getPsnNo();
            contractBusNo = enriched.getContractBusNo();
            insuplcAdmdvs = enriched.getInsuplcAdmdvs();
            if (isEmpty(mdtrtId) || isEmpty(psnNo) || isEmpty(contractBusNo)) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照，且缺少 2205 撤销必要字段");
            }
        }
        OutpatientSettleCommandAssembler.requireContractConfig(contractBusNo);
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(
            enriched.getOperatorId(), insuplcAdmdvs);

        Op2205Input input2205 = Op2205InputAssembler.assemble(mdtrtId, chrgBchno, psnNo);
        ResultBody body2205 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_FEE_REVOKE, input2205, invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body2205, ChsInfNo.OUTPATIENT_FEE_REVOKE);

        clinicFeeUploadDomainService.markRevokedByChrgBchno(chrgBchno, tenantId);
        if (pre != null) {
            clinicPreSettleDomainService.markCancelledAndSave(pre);
        }

        return new OpRevokeFeeDetailResult()
            .setChrgBchno(chrgBchno)
            .setRevoked(true);
    }

    /**
     * 补全撤销命令的 operatorId、tenantId 及 chrgBchno 等默认值。
     *
     * @param command 原始撤销命令（可为 null）
     * @return 补全后的撤销命令
     */
    private OpRevokeFeeDetailCommand enrichCommand(OpRevokeFeeDetailCommand command) {
        OpRevokeFeeDetailCommand domain;
        if (command != null) {
            domain = command;
        } else {
            domain = new OpRevokeFeeDetailCommand();
        }
        if (domain.getOperatorId() == null) {
            domain.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        }
        if (domain.getTenantId() == null) {
            domain.setTenantId(TenantUtils.getCurrentTenantId());
        }
        if (isNotEmpty(domain.getChrgBchno())) {
            domain.setChrgBchno(domain.getChrgBchno().trim());
        }
        return domain;
    }
}
