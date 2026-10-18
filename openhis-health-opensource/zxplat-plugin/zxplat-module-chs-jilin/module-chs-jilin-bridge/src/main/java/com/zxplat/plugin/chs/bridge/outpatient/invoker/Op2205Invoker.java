/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.domain.outpatient.command.OpRevokeFeeDetailCommand;
import com.zxplat.plugin.chs.domain.outpatient.result.OpRevokeFeeDetailResult;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2205Input;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.Op2205InputAssembler;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
        // Gate：非 ACTIVE 快照默认不撤；但 chrg_bchno 碰撞时他患 SETTLED 会占号，
        // 若命令显式带本就诊 mdtrt/psn，仍须出站 2205 清悬挂明细（不可被他患 SETTLED 短路）。
        if (pre != null && !YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            boolean commandHasMdtrt =
                isNotEmpty(enriched.getMdtrtId()) && isNotEmpty(enriched.getPsnNo());
            boolean sameMdtrtAlreadySettled =
                YbClinicPreSettle.STATUS_SETTLED.equals(pre.getStatus())
                    && isNotEmpty(pre.getMdtrtId())
                    && isNotEmpty(enriched.getMdtrtId())
                    && pre.getMdtrtId().trim().equals(enriched.getMdtrtId().trim());
            if (sameMdtrtAlreadySettled) {
                // 本就诊该批已正式结算，2205 无效，须走 2208
                return new OpRevokeFeeDetailResult()
                    .setChrgBchno(pre.getChrgBchno())
                    .setRevoked(false);
            }
            if (!commandHasMdtrt) {
                return new OpRevokeFeeDetailResult()
                    .setChrgBchno(pre.getChrgBchno())
                    .setRevoked(false);
            }
            // 他患/旧状态占号：忽略本地非 ACTIVE 快照，按命令字段孤儿撤销
            pre = null;
        }
        String chrgBchno = enriched.getChrgBchno().trim();
        String mdtrtId;
        String psnNo;
        String contractBusNo;
        String insuplcAdmdvs;
        if (pre != null) {
            // Pre：ACTIVE 快照优先；命令可覆盖 contract / 参保地
            if (isNotEmpty(enriched.getMdtrtId())) {
                mdtrtId = enriched.getMdtrtId().trim();
            } else {
                mdtrtId = pre.getMdtrtId();
            }
            if (isNotEmpty(enriched.getPsnNo())) {
                psnNo = enriched.getPsnNo().trim();
            } else {
                psnNo = pre.getPsnNo();
            }
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
            // 2204 已成功、本地无 ACTIVE：用命令补齐字段直接 2205（含批次号碰撞孤儿）
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
        // Pre：平台已无明细视为幂等成功（上次 2205 成功后外层事务回滚导致本地仍 ACTIVE）
        if (!isPlatformSuccessOrAlreadyCleared(body2205)) {
            ChsPlatformResponseSupport.requireSuccess(body2205, ChsInfNo.OUTPATIENT_FEE_REVOKE);
        }

        // Pre：独立事务落库，避免后续 2204/2206 失败回滚把 CANCELLED 冲掉
        clinicFeeUploadDomainService.markRevokedByChrgBchnoInNewTx(chrgBchno, tenantId);
        if (pre != null) {
            clinicPreSettleDomainService.markCancelledByChrgBchnoInNewTx(chrgBchno, tenantId);
        }

        return new OpRevokeFeeDetailResult()
            .setChrgBchno(chrgBchno)
            .setRevoked(true);
    }

    /**
     * 2205 成功，或平台已无未结算明细（幂等）。
     *
     * @param body 平台响应
     * @return true 时可继续本地收口
     */
    private static boolean isPlatformSuccessOrAlreadyCleared(ResultBody body) {
        if (body == null) {
            return false;
        }
        if (ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            return true;
        }
        String err = body.getErrMsg();
        if (err == null || err.trim().isEmpty()) {
            return false;
        }
        return err.contains("未查询到任何有效相关的记录");
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
