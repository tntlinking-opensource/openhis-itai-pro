/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import com.zxplat.plugin.chs.gateway.config.ContractConfigLoader;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;

/**
 * 住院结算命令组装与校验（{@link IpSettleContext} → {@link InvokeContext} / 契约配置）。
 *
 * <p>纯内存转换与本地配置读取，无 gateway 出站。</p>
 */
public final class InpatientSettleCommandAssembler {

    private InpatientSettleCommandAssembler() {
    }

    public static void validateAdmissionContext(IpSettleContext context) {
        if (context == null || context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (context.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (isEmpty(context.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo required, read card (1101) first");
        }
        if (isEmpty(context.getMdtrtCertType()) || isEmpty(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
    }

    /**
     * 校验住院预结算上下文。无卡试算不要求就诊凭证号（国家 2303 该字段非必填），仍要求人员编号与凭证类型。
     *
     * @param context 已装载的住院结算上下文
     */
    public static void validatePreSettleContext(IpSettleContext context) {
        if (context == null || context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(context.getPsnNo())) {
            // 无卡试算只能用入院已落下的人员编号，没有则无法问平台要报销
            if (Boolean.TRUE.equals(context.getCardlessTrial())) {
                throw new BusinessException(
                    ChsErrorCodes.CONTRACT_INVALID,
                    "未找到入院医保人员编号，无法无卡预结算");
            }
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo required, read card (1101) first");
        }
        if (isEmpty(context.getMdtrtCertType())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        // 收费处读卡路径仍要凭证号；医护站无卡试算按 2303 字段表允许不传
        if (!Boolean.TRUE.equals(context.getCardlessTrial()) && isEmpty(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        if (isEmpty(context.getSettleMode())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "settleMode 不能为空");
        }
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "收费明细不能为空");
        }
    }

    public static void validateEncounterPlatformCommand(
            Long encounterId,
            String contractBusNo,
            String mdtrtId,
            String psnNo) {
        if (encounterId == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (isEmpty(contractBusNo)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(mdtrtId)) {
            throw new BusinessException("INS-IP-009", "mdtrtId 不能为空");
        }
        if (isEmpty(psnNo)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空");
        }
    }

    public static ContractConfig requireContractConfig(String contractBusNo) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "未找到契约配 " + contractBusNo);
        }
        return config;
    }

    public static InvokeContext buildInvokeContext(IpSettleContext context) {
        InvokeContext.InvokeContextBuilder builder = InvokeContext.builder();
        if (context.getOperatorId() != null) {
            builder.operatorId(String.valueOf(context.getOperatorId()));
        }
        if (isNotEmpty(context.getInsuplcAdmdvs())) {
            builder.insuplcAdmdvs(context.getInsuplcAdmdvs());
        }
        return builder.build();
    }

    public static InvokeContext buildInvokeContext(Long operatorId, String insuplcAdmdvs) {
        InvokeContext.InvokeContextBuilder builder = InvokeContext.builder();
        if (operatorId != null) {
            builder.operatorId(String.valueOf(operatorId));
        }
        if (isNotEmpty(insuplcAdmdvs)) {
            builder.insuplcAdmdvs(insuplcAdmdvs);
        }
        return builder.build();
    }

    public static Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }

    public static IpSettleContext rebuildContextFromPreSettle(YbInpatientPreSettle pre) {
        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(pre.getContractId());
        ctx.setContractBusNo(pre.getContractBusNo());
        ctx.setEncounterId(pre.getEncounterId());
        ctx.setPsnNo(pre.getPsnNo());
        ctx.setMedType(pre.getMedType());
        ctx.setSettleMode(pre.getSettleMode());
        ctx.setMedfeeSumamt(pre.getMedfeeSumamt());
        ctx.setTenantId(pre.getTenantId());
        ctx.setMdtrtCertType(pre.getMdtrtCertType());
        ctx.setMdtrtCertNo(pre.getMdtrtCertNo());
        ctx.setInsutype(pre.getInsutype());
        ctx.setAcctUsedFlag(pre.getAcctUsedFlag());
        ctx.setPsnSetlway(pre.getPsnSetlway());
        ctx.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
        return ctx;
    }

    public static IpSettleContext buildMinimalContext(YbSettlementRecord record) {
        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(record.getContractId());
        ctx.setEncounterId(record.getEncounterId());
        ctx.setPsnNo(record.getPsnNo());
        ctx.setTenantId(record.getTenantId());
        return ctx;
    }
}
