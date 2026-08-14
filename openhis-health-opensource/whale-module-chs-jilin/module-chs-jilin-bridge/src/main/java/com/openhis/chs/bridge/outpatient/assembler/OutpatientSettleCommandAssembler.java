/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;

/**
 * 门诊结算命令组装与校验（{@link OpPreSettleCommand} → {@link InvokeContext} / 契约配置）。
 *
 * <p>纯内存转换与本地配置读取，无 gateway 出站。</p>
 */
public final class OutpatientSettleCommandAssembler {

    private OutpatientSettleCommandAssembler() {
    }

    public static void validateRegistrationContext(OpPreSettleCommand context) {
        if (context == null || context.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(context.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo required, read card (1101) first");
        }
        if (isEmpty(context.getMdtrtCertType()) || isEmpty(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
    }

    public static void validatePreSettleContext(OpPreSettleCommand context) {
        if (context == null || context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(context.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo required, read card (1101) first");
        }
        if (isEmpty(context.getMdtrtCertType()) || isEmpty(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "收费明细不能为空");
        }
    }

    public static ContractConfig requireContractConfig(String contractBusNo) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "未找到契约配 " + contractBusNo);
        }
        return config;
    }

    public static InvokeContext buildInvokeContext(OpPreSettleCommand context) {
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

    public static OpPreSettleCommand rebuildContextFromPreSettle(YbClinicPreSettle pre) {
        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(pre.getContractId());
        ctx.setEncounterId(pre.getEncounterId());
        ctx.setPsnNo(pre.getPsnNo());
        ctx.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
        ctx.setMdtrtCertType(pre.getMdtrtCertType());
        ctx.setMdtrtCertNo(pre.getMdtrtCertNo());
        ctx.setInsutype(pre.getInsutype());
        ctx.setMedType(pre.getMedType());
        ctx.setAcctUsedFlag(pre.getAcctUsedFlag());
        ctx.setPsnSetlway(pre.getPsnSetlway());
        ctx.setAccountId(pre.getAccountId());
        ctx.setContractBusNo(pre.getContractBusNo());
        ctx.setMedfeeSumamt(pre.getMedfeeSumamt());
        ctx.setRetailMedfeeSumamt(pre.getRetailMedfeeSumamt());
        ctx.setUploadMedfeeSumamt(pre.getUploadMedfeeSumamt());
        ctx.setRetailUploadDiffAmt(pre.getRetailUploadDiffAmt());
        ctx.setTenantId(pre.getTenantId());
        return ctx;
    }

    public static OpPreSettleCommand buildMinimalContext(YbSettlementRecord record) {
        OpPreSettleCommand ctx = new OpPreSettleCommand();
        ctx.setContractId(record.getContractId());
        ctx.setEncounterId(record.getEncounterId());
        ctx.setPsnNo(record.getPsnNo());
        ctx.setTenantId(record.getTenantId());
        return ctx;
    }
}
