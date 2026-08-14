/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.support;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.bridge.inpatient.invoker.Ip2301Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2303Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.chs.bridge.settlement.support.ChsFeeUploadNettingSupport;
import com.openhis.chs.domain.inpatient.command.IpPreSettleCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.loader.IpSettleContextLoader;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.openhis.chs.domain.medicalinfo.access.Med5201QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2404Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2303OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.parser.IpFeeUploadResultIndexer;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医保结算恢复：旧平台就诊无法 2405/2304 时，重新 2401 入院并用新 mdtrt_id 结算。
 *
 * <p>步骤：5201 清在院(2404) → 软删本地 map → 本地费用上传标 REVOKED → 2401 → 2301 → 2303 刷新预结算。</p>
 */
@Component
@RequiredArgsConstructor
public class IpPlatformReadmitForSettleSupport {

    private static final DateTimeFormatter PLATFORM_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INHOSP_STAS_IN_HOSPITAL = "1";

    private final InsuranceGatewayClient gatewayClient;
    private final Med5201QueryInvoker med5201QueryInvoker;
    private final IpSettleContextLoader contextLoader;
    private final Ip2401Invoker ip2401Invoker;
    private final Ip2301Invoker ip2301Invoker;
    private final Ip2303Invoker ip2303Invoker;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;
    private final YbInpatientPreSettleDomainService inpatientPreSettleDomainService;

    /**
     * 重新办理入院并刷新 ACTIVE 预结算快照到新 mdtrt_id。
     *
     * @param pre        ACTIVE 预结算快照
     * @param operatorId 操作人
     * @return 新平台就诊 ID
     */
    public String readmitAndRefreshPreSettle(YbInpatientPreSettle pre, Long operatorId) {
        if (pre == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照不存在，无法重新入院");
        }
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(pre.getTenantId());
        IpSettleContext fullCtx = this.buildFullContextFromPre(pre, operatorId);
        String newMdtrtId = this.readmitAndReupload(fullCtx, tenantId);

        BigDecimal medfeeSumamt = fullCtx.getMedfeeSumamt();
        if (medfeeSumamt == null) {
            medfeeSumamt = pre.getMedfeeSumamt();
        }
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(fullCtx);
        Ip2303Input input2303 = IpInpatientSettleInputAssembler.assemble2303(
            fullCtx, newMdtrtId, medfeeSumamt);
        ResultBody body2303 = ip2303Invoker.invoke(
            fullCtx.getContractBusNo(), input2303, invokeContext);
        ChsSetlinfoOutput setlinfo = Ip2303OutputParser.requireSetlinfo(body2303);
        PlatformSetlinfoFields platformSetlinfo = SetlinfoIntegrationMapper.toPlatformFields(setlinfo);
        inpatientPreSettleDomainService.refreshActiveFrom2303(pre, newMdtrtId, platformSetlinfo);
        return newMdtrtId;
    }

    /**
     * 预结算路径：清在院 → 换新 2401 → 重传 2301，返回新 mdtrt（由调用方继续 2303）。
     *
     * @param fullCtx  已 enrich 的完整上下文
     * @param tenantId 租户
     * @return 新平台就诊 ID
     */
    public String readmitAndReupload(IpSettleContext fullCtx, Long tenantId) {
        if (fullCtx == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算上下文为空，无法重新入院");
        }
        Long resolvedTenant = InpatientSettleCommandAssembler.resolveTenantId(
            tenantId != null ? tenantId : fullCtx.getTenantId());
        // Pre：清平台在院，避免 2401 撞「已有在院」
        this.revokeAllInHospitalAdmissions(fullCtx);
        List<Long> chargeItemIds = new ArrayList<>();
        if (fullCtx.getChargeLines() != null) {
            for (IpSettleChargeLine line : fullCtx.getChargeLines()) {
                if (line != null && line.getChargeItemId() != null) {
                    chargeItemIds.add(line.getChargeItemId());
                }
            }
        }
        inpatientFeeUploadDomainService.markRevokedByChargeItemIds(chargeItemIds, resolvedTenant);
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(fullCtx);
        String newMdtrtId = ip2401Invoker.registerFresh(
            fullCtx.getContractBusNo(), fullCtx, invokeContext, resolvedTenant);
        this.uploadAllChargeLines(fullCtx, newMdtrtId, invokeContext, resolvedTenant);
        return newMdtrtId;
    }

    /**
     * 由预结算快照重建含收费明细/入院字段的完整上下文（供 2401/2301/2402）。
     *
     * @param pre        预结算快照
     * @param operatorId 操作人
     * @return 完整结算上下文
     */
    public IpSettleContext buildFullContextFromPre(YbInpatientPreSettle pre, Long operatorId) {
        IpPreSettleCommand command = new IpPreSettleCommand();
        command.setEncounterId(pre.getEncounterId());
        command.setContractId(pre.getContractId());
        command.setPaymentDraftId(pre.getPaymentDraftId());
        command.setSettleMode(pre.getSettleMode());
        command.setPsnNo(pre.getPsnNo());
        command.setMdtrtCertType(pre.getMdtrtCertType());
        command.setMdtrtCertNo(pre.getMdtrtCertNo());
        command.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
        command.setInsutype(pre.getInsutype());
        command.setAcctUsedFlag(pre.getAcctUsedFlag());
        command.setPsnSetlway(pre.getPsnSetlway());
        command.setMedType(pre.getMedType());
        command.setChargeItemIds(ChargeItemIdsSupport.parseIds(pre.getChargeItemIds()));
        IpSettleContext ctx = contextLoader.enrichPreSettle(command);
        ctx.setOperatorId(operatorId);
        return ctx;
    }

    /**
     * 将本批收费明细全部上传到新 mdtrt（本地已 REVOKED，不再跳过）。
     *
     * @param context       结算上下文
     * @param mdtrtId       新平台就诊
     * @param invokeContext 出站上下文
     * @param tenantId      租户
     */
    private void uploadAllChargeLines(
            IpSettleContext context,
            String mdtrtId,
            InvokeContext invokeContext,
            Long tenantId) {
        List<IpSettleChargeLine> lines = context.getChargeLines();
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "重新入院后无费用明细可上传，请重新预结算");
        }
        ChsFeeUploadNettingSupport.IpNetUploadPlan netPlan =
            ChsFeeUploadNettingSupport.planIpPendingUploadLines(lines);
        List<IpSettleChargeLine> uploadLines = netPlan.uploadLines();
        if (uploadLines.isEmpty()) {
            uploadLines = lines;
        }
        Ip2301Input input2301 = IpInpatientSettleInputAssembler.assemble2301(context, mdtrtId, uploadLines);
        ResultBody body2301 = ip2301Invoker.invoke(context.getContractBusNo(), input2301, invokeContext);
        inpatientFeeUploadDomainService.persistFrom2301(
            context.getEncounterId(),
            mdtrtId,
            uploadLines,
            IpFeeUploadResultIndexer.indexByFeedetlSn(body2301),
            body2301.getOutput(),
            tenantId);
        if (!netPlan.locallyClosedLines().isEmpty()) {
            inpatientFeeUploadDomainService.persistFrom2301(
                context.getEncounterId(),
                mdtrtId,
                netPlan.locallyClosedLines(),
                Map.of(),
                null,
                tenantId);
        }
    }

    /**
     * 5201 查出同人全部在院就诊并平台 2404（为重新 2401 腾位）。
     *
     * @param ctx 结算上下文
     */
    private void revokeAllInHospitalAdmissions(IpSettleContext ctx) {
        Set<String> inHospital = this.listAllInHospitalMdtrtIds(ctx);
        if (inHospital.isEmpty()) {
            return;
        }
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            ctx.getOperatorId(), ctx.getInsuplcAdmdvs());
        List<String> failures = new ArrayList<>();
        for (String mdtrtId : inHospital) {
            try {
                Ip2404Input input2404 = IpInpatientSettleInputAssembler.assemble2404Or2405(
                    mdtrtId, ctx.getPsnNo());
                ResultBody body2404 = gatewayClient.invoke(
                    ctx.getContractBusNo(),
                    ChsInfNo.INPATIENT_ADMISSION_REVOKE,
                    input2404,
                    invokeContext);
                ChsPlatformResponseSupport.requireSuccess(body2404, ChsInfNo.INPATIENT_ADMISSION_REVOKE);
            } catch (RuntimeException ex) {
                failures.add(mdtrtId + ":" + ex.getMessage());
            }
        }
        if (!failures.isEmpty() && failures.size() == inHospital.size()) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "重新入院前无法撤销平台在院就诊：" + String.join("；", failures));
        }
    }

    private Set<String> listAllInHospitalMdtrtIds(IpSettleContext ctx) {
        String medType = IpInpatientSettleInputAssembler.resolveMedTypeForContext(ctx);
        if (StringUtils.isEmpty(medType)) {
            medType = MedType.GENERAL_HOSPITALIZATION.getCode();
        }
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        // 长春 5201：查询跨度不能超过 92 天（取 91 天留余量）
        LocalDateTime begin = end.minusDays(91);
        QueryMdtrt5201Command query = new QueryMdtrt5201Command();
        query.setContractBusNo(ctx.getContractBusNo());
        query.setContractId(ctx.getContractId());
        query.setPsnNo(ctx.getPsnNo());
        query.setMedType(medType);
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
}
