/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.support;

import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.medicalinfo.access.Med5201QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientPlatformVisitAlignExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientPlatformVisitAlignExtensionOut;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院平台在院态诊断与轻量修复（本地 map ↔ 5201）。
 */
@Component
@RequiredArgsConstructor
public class IpPlatformVisitAlignSupport {

    private static final DateTimeFormatter PLATFORM_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INHOSP_STAS_IN_HOSPITAL = "1";

    public static final String CODE_OK = "OK";
    public static final String CODE_NO_LOCAL_MAP = "NO_LOCAL_MAP";
    public static final String CODE_LOCAL_DEAD = "LOCAL_DEAD";
    public static final String CODE_MULTI_INHOSP = "MULTI_INHOSP";
    public static final String CODE_PLATFORM_OUT_LOCAL_IN = "PLATFORM_OUT_LOCAL_IN";
    public static final String CODE_PLATFORM_ORPHAN_INHOSP = "PLATFORM_ORPHAN_INHOSP";

    private final Med5201QueryInvoker med5201QueryInvoker;
    private final YbEncounterPlatformMapQueryService platformMapQueryService;
    private final YbEncounterPlatformMapDomainService platformMapDomainService;
    private final YbInpatientFeeUploadRepository feeUploadRepository;
    private final YbInpatientFeeUploadDomainService feeUploadDomainService;
    private final IpPlatformDischargeRevokeSupport dischargeRevokeSupport;
    private final ChsInsuranceContractReader contractReader;

    public InpatientPlatformVisitAlignExtensionOut diagnose(InpatientPlatformVisitAlignExtensionIn command) {
        AlignContext ctx = this.requireAlignContext(command);
        return this.buildDiagnoseResult(ctx);
    }

    public InpatientPlatformVisitAlignExtensionOut repair(InpatientPlatformVisitAlignExtensionIn command) {
        AlignContext ctx = this.requireAlignContext(command);
        InpatientPlatformVisitAlignExtensionOut diagnosed = this.buildDiagnoseResult(ctx);
        String code = diagnosed.getMismatchCode();
        if (CODE_OK.equals(code) || CODE_NO_LOCAL_MAP.equals(code)) {
            diagnosed.setRepaired(false);
            diagnosed.setRepairSummary("无需修复");
            return diagnosed;
        }
        if (CODE_MULTI_INHOSP.equals(code)) {
            if (StringUtils.isEmpty(diagnosed.getLocalMdtrtId())) {
                diagnosed.setRepaired(false);
                diagnosed.setRepairSummary("无本单 mdtrt，未自动清全部在院；请用 5201 人工处理多余在院后重试");
                return diagnosed;
            }
            IpSettleContext settleCtx = this.buildSettleContext(ctx);
            int cleaned = dischargeRevokeSupport.revokeOtherInHospitalAdmissions(
                settleCtx, diagnosed.getLocalMdtrtId());
            diagnosed.setRepaired(cleaned > 0);
            diagnosed.setRepairSummary("已撤销同人其它在院 " + cleaned + " 条（保留本单 " + diagnosed.getLocalMdtrtId() + "）");
            return this.mergeAfterRepair(command, diagnosed);
        }
        if (CODE_LOCAL_DEAD.equals(code) || CODE_PLATFORM_OUT_LOCAL_IN.equals(code)) {
            this.softDeleteDeadLocalMap(ctx, diagnosed.getLocalMdtrtId());
            int revokedFees = this.markUploadedFeesRevoked(ctx.encounterId, ctx.tenantId);
            diagnosed.setRepaired(true);
            diagnosed.setRepairSummary(
                "已软删本地死映射 " + diagnosed.getLocalMdtrtId() + "，并标记费用上传撤销 " + revokedFees + " 行；下次预结算将重新 2401");
            return this.mergeAfterRepair(command, diagnosed);
        }
        if (CODE_PLATFORM_ORPHAN_INHOSP.equals(code)) {
            diagnosed.setRepaired(false);
            diagnosed.setRepairSummary("平台有在院但本地无 map：未自动清全部在院，请用 5201 核对后决定是否 2404");
            return diagnosed;
        }
        diagnosed.setRepaired(false);
        diagnosed.setRepairSummary("未知不一致码，未执行修复");
        return diagnosed;
    }

    private InpatientPlatformVisitAlignExtensionOut mergeAfterRepair(
            InpatientPlatformVisitAlignExtensionIn command,
            InpatientPlatformVisitAlignExtensionOut previous) {
        InpatientPlatformVisitAlignExtensionOut again = this.diagnose(command);
        again.setRepaired(previous.isRepaired());
        again.setRepairSummary(previous.getRepairSummary());
        return again;
    }

    private InpatientPlatformVisitAlignExtensionOut buildDiagnoseResult(AlignContext ctx) {
        String localMdtrt = ctx.localMdtrtId;
        List<String> platformIn = new ArrayList<>(this.listAllInHospitalMdtrtIds(ctx));
        InpatientPlatformVisitAlignExtensionOut out = new InpatientPlatformVisitAlignExtensionOut()
            .setLocalMdtrtId(localMdtrt)
            .setPlatformInHospitalMdtrtIds(platformIn);

        if (StringUtils.isEmpty(localMdtrt) && platformIn.isEmpty()) {
            return out.setMismatchCode(CODE_NO_LOCAL_MAP)
                .setAdvice("本地无平台就诊映射，平台亦无在院；下次医保预结算/登记将自动 2401");
        }
        if (StringUtils.isEmpty(localMdtrt) && !platformIn.isEmpty()) {
            return out.setMismatchCode(CODE_PLATFORM_ORPHAN_INHOSP)
                .setAdvice("平台存在在院就诊但本地无 map；请用 5201 核对后撤销多余入院，或完成登记写入 map");
        }
        boolean localInPlatform = platformIn.stream().anyMatch(id -> id.equals(localMdtrt));
        long otherCount = platformIn.stream().filter(id -> !id.equals(localMdtrt)).count();
        if (otherCount > 0) {
            return out.setMismatchCode(CODE_MULTI_INHOSP)
                .setAdvice("同人存在多余在院；可一键撤销其它在院（保留本单），或打开 5201 人工处理");
        }
        if (!localInPlatform) {
            // 本地有号但不在 5201 在院列表：可能已离院或就诊已废
            return out.setMismatchCode(CODE_LOCAL_DEAD)
                .setAdvice("本地 mdtrt 不在平台在院列表（可能已离院或死号）；可软删本地映射，下次预结算换新号");
        }
        return out.setMismatchCode(CODE_OK).setAdvice("本地 map 与平台在院一致");
    }

    private void softDeleteDeadLocalMap(AlignContext ctx, String mdtrtId) {
        platformMapDomainService.softDeleteEncounterMap(
            ctx.encounterId,
            ctx.contractId,
            mdtrtId,
            MedType.GENERAL_HOSPITALIZATION.getCode(),
            ctx.operatorId,
            ctx.tenantId);
    }

    private int markUploadedFeesRevoked(Long encounterId, Long tenantId) {
        List<YbInpatientFeeUpload> uploaded = feeUploadRepository
            .findAllByEncounterIdAndUploadStatusAndTenantIdAndIsDeletedFalse(
                encounterId, YbFeeUploadStatus.UPLOADED, tenantId);
        if (uploaded == null || uploaded.isEmpty()) {
            return 0;
        }
        List<Long> chargeItemIds = new ArrayList<>();
        for (YbInpatientFeeUpload row : uploaded) {
            if (row != null && row.getChargeItemId() != null) {
                chargeItemIds.add(row.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return 0;
        }
        feeUploadDomainService.markRevokedByChargeItemIds(chargeItemIds, tenantId);
        return chargeItemIds.size();
    }

    private AlignContext requireAlignContext(InpatientPlatformVisitAlignExtensionIn command) {
        if (command == null || command.getEncounterId() == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊与合同不能为空");
        }
        if (StringUtils.isEmpty(command.getInsuredPersonNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空，请先读卡");
        }
        if (!contractReader.isInsuranceContract(command.getContractId())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "非医保合同，无需平台在院对齐");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        String localMdtrt = platformMapQueryService
            .findOptionalByEncounterContractMedType(
                command.getEncounterId(),
                command.getContractId(),
                MedType.GENERAL_HOSPITALIZATION.getCode(),
                tenantId)
            .map(YbEncounterPlatformMap::getMdtrtId)
            .orElse(null);
        AlignContext ctx = new AlignContext();
        ctx.encounterId = command.getEncounterId();
        ctx.contractId = command.getContractId();
        ctx.contractBusNo = contractBusNo;
        ctx.psnNo = command.getInsuredPersonNo().trim();
        ctx.insuplcAdmdvs = command.getInsuplcAdmdvs();
        ctx.operatorId = command.getOperatorId();
        ctx.tenantId = tenantId;
        ctx.localMdtrtId = localMdtrt;
        return ctx;
    }

    private IpSettleContext buildSettleContext(AlignContext ctx) {
        IpSettleContext settleCtx = new IpSettleContext();
        settleCtx.setEncounterId(ctx.encounterId);
        settleCtx.setContractId(ctx.contractId);
        settleCtx.setContractBusNo(ctx.contractBusNo);
        settleCtx.setPsnNo(ctx.psnNo);
        settleCtx.setInsuplcAdmdvs(ctx.insuplcAdmdvs);
        settleCtx.setOperatorId(ctx.operatorId);
        settleCtx.setTenantId(ctx.tenantId);
        settleCtx.setSettleMode(IpSettleContext.SETTLE_MODE_DISCHARGE_FULL);
        settleCtx.setMedType(MedType.GENERAL_HOSPITALIZATION.getCode());
        return settleCtx;
    }

    private Set<String> listAllInHospitalMdtrtIds(AlignContext ctx) {
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        LocalDateTime begin = end.minusDays(91);
        QueryMdtrt5201Command query = new QueryMdtrt5201Command();
        query.setContractBusNo(ctx.contractBusNo);
        query.setContractId(ctx.contractId);
        query.setPsnNo(ctx.psnNo);
        query.setMedType(MedType.GENERAL_HOSPITALIZATION.getCode());
        query.setBegntime(PLATFORM_DT.format(begin));
        query.setEndtime(PLATFORM_DT.format(end));
        MedicalInfoQueryResult result = med5201QueryInvoker.invoke(query);
        Set<String> ids = new LinkedHashSet<>();
        if (result == null || result.getRows() == null) {
            return ids;
        }
        for (Map<String, Object> row : result.getRows()) {
            if (row == null) {
                continue;
            }
            String mdtrtId = firstText(row, "mdtrt_id", "mdtrtId");
            if (StringUtils.isEmpty(mdtrtId)) {
                continue;
            }
            String inhospStas = firstText(row, "inhosp_stas", "inhospStas");
            if (!INHOSP_STAS_IN_HOSPITAL.equals(inhospStas)) {
                continue;
            }
            ids.add(mdtrtId.trim());
        }
        return ids;
    }

    private static String firstText(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private static final class AlignContext {
        private Long encounterId;
        private Long contractId;
        private String contractBusNo;
        private String psnNo;
        private String insuplcAdmdvs;
        private Long operatorId;
        private Long tenantId;
        private String localMdtrtId;
    }
}
