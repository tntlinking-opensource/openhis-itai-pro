/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.support;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.bridge.inpatient.invoker.Ip2402Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2405Invoker;
import com.openhis.chs.domain.inpatient.command.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.medicalinfo.access.Med5201QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2402Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2404Input;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 住院正式结算前后的平台出院/出院撤销支撑（2402 / 2405）。
 *
 * <p>长春出院全结：2304 要求人员已离院，确认前应先 2402；同人多余在院时先 5201+2404 清理。
 * 2405 仅用于「误离院需回到在院」的恢复场景。</p>
 */
@Component
@RequiredArgsConstructor
public class IpPlatformDischargeRevokeSupport {

    private static final DateTimeFormatter PLATFORM_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 在院状态：1=在院（见前端 medical-info-query 字典） */
    private static final String INHOSP_STAS_IN_HOSPITAL = "1";

    private final Ip2402Invoker ip2402Invoker;
    private final Ip2405Invoker ip2405Invoker;
    private final InsuranceGatewayClient gatewayClient;
    private final Med5201QueryInvoker med5201QueryInvoker;

    /**
     * 出院全结确认前：清理同人其它在院后办理 2402，保证本就诊处于离院态供 2304。
     *
     * @param ctx         含出院科室/诊断等字段的完整上下文
     * @param keepMdtrtId 本单平台就诊 ID
     */
    public void ensureDischargedForSettle(IpSettleContext ctx, String keepMdtrtId) {
        if (ctx == null || StringUtils.isEmpty(keepMdtrtId)) {
            return;
        }
        if (!IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(ctx.getSettleMode())) {
            return;
        }
        String mdtrtId = keepMdtrtId.trim();
        // Pre：先清同人其它在院，避免 2402 被「其它在院」拦截
        this.revokeOtherInHospitalAdmissions(ctx, mdtrtId);
        try {
            this.invoke2402IgnoreIfAlreadyDischarged(ctx, mdtrtId);
        } catch (RuntimeException firstEx) {
            if (!isBlockedByOtherInHospital(firstEx)) {
                throw firstEx;
            }
            int cleaned = this.revokeOtherInHospitalAdmissions(ctx, mdtrtId);
            if (cleaned <= 0) {
                throw new BusinessException(
                    ChsErrorCodes.PLATFORM_FAILED,
                    "平台存在其他在院就诊，无法办理出院；请用医保信息查询(5201)核对并撤销多余入院登记后再结算。原因："
                        + firstEx.getMessage());
            }
            this.invoke2402IgnoreIfAlreadyDischarged(ctx, mdtrtId);
        }
    }

    /**
     * 调用 2402；平台已出院时幂等忽略。
     *
     * @param ctx     结算上下文
     * @param mdtrtId 平台就诊 ID
     */
    public void invoke2402IgnoreIfAlreadyDischarged(IpSettleContext ctx, String mdtrtId) {
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);
        Ip2402Input input2402 = IpInpatientSettleInputAssembler.assemble2402(ctx, mdtrtId);
        try {
            ip2402Invoker.invoke(ctx.getContractBusNo(), input2402, invokeContext);
        } catch (RuntimeException ex) {
            if (isAlreadyDischargedOnPlatform(ex)) {
                return;
            }
            throw ex;
        }
    }

    /**
     * 2402 是否因平台已出院被拒（可幂等忽略）。
     *
     * @param ex 平台异常
     * @return 命中已出院文案时为 true
     */
    public static boolean isAlreadyDischargedOnPlatform(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("已经是出院状态")) {
            return true;
        }
        if (msg.contains("IptPsrnSettlMgtBO_972")) {
            return true;
        }
        return msg.contains("iptPsnSetlMgtBO_972");
    }

    /**
     * 出院全结路径：幂等撤销平台出院；遇「同人另有在院」时清理多余在院后再撤。
     *
     * @param ctx            结算上下文
     * @param keepMdtrtId    本单保留的平台就诊 ID（费用所在就诊，不可 2404）
     */
    public void revokeDischargeForSettle(IpSettleContext ctx, String keepMdtrtId) {
        if (ctx == null || StringUtils.isEmpty(keepMdtrtId)) {
            return;
        }
        if (!IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(ctx.getSettleMode())) {
            return;
        }
        this.doRevokeDischargeForSettle(ctx, keepMdtrtId);
    }

    /**
     * 不校验 settleMode 的出院撤销（2304 已报离院时的重试路径）。
     *
     * @param ctx         结算上下文
     * @param keepMdtrtId 本单保留的平台就诊 ID
     */
    public void forceRevokeDischargeForSettle(IpSettleContext ctx, String keepMdtrtId) {
        if (ctx == null || StringUtils.isEmpty(keepMdtrtId)) {
            return;
        }
        this.doRevokeDischargeForSettle(ctx, keepMdtrtId);
    }

    /**
     * 执行 2405；遇同人多余在院则 5201+2404 清理后重试。
     *
     * @param ctx         结算上下文
     * @param keepMdtrtId 本单保留就诊
     */
    private void doRevokeDischargeForSettle(IpSettleContext ctx, String keepMdtrtId) {
        try {
            this.invoke2405IgnoreIfNotDischarged(ctx, keepMdtrtId);
        } catch (RuntimeException firstEx) {
            if (!isBlockedByOtherInHospital(firstEx)) {
                throw firstEx;
            }
            int cleaned = this.revokeOtherInHospitalAdmissions(ctx, keepMdtrtId.trim());
            if (cleaned <= 0) {
                throw new BusinessException(
                    ChsErrorCodes.PLATFORM_FAILED,
                    "平台存在其他在院就诊，无法撤销本就诊出院；请用医保信息查询(5201)核对并撤销多余入院登记后再结算。原因："
                        + firstEx.getMessage());
            }
            this.invoke2405IgnoreIfNotDischarged(ctx, keepMdtrtId);
        }
    }

    /**
     * 调用 2405；仅当平台本就未出院时忽略失败。
     *
     * @param ctx     结算上下文
     * @param mdtrtId 平台就诊 ID
     */
    public void invoke2405IgnoreIfNotDischarged(IpSettleContext ctx, String mdtrtId) {
        IpEncounterPlatformCommand command = new IpEncounterPlatformCommand();
        command.setEncounterId(ctx.getEncounterId());
        command.setContractBusNo(ctx.getContractBusNo());
        command.setMdtrtId(mdtrtId);
        command.setPsnNo(ctx.getPsnNo());
        command.setInsuplcAdmdvs(ctx.getInsuplcAdmdvs());
        command.setOperatorId(ctx.getOperatorId());
        try {
            ip2405Invoker.invoke(command);
        } catch (RuntimeException ex) {
            if (isNotDischargedOnPlatform(ex)) {
                return;
            }
            throw ex;
        }
    }

    /**
     * 5201 查找并平台侧 2404 撤销「非本单」的在院就诊（不改本地 map，避免误删本单映射）。
     *
     * @param ctx         结算上下文
     * @param keepMdtrtId 本单 mdtrt_id
     * @return 成功撤销的条数
     */
    private int revokeOtherInHospitalAdmissions(IpSettleContext ctx, String keepMdtrtId) {
        Set<String> otherInHospital = this.listOtherInHospitalMdtrtIds(ctx, keepMdtrtId);
        if (otherInHospital.isEmpty()) {
            return 0;
        }
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(
            ctx.getOperatorId(), ctx.getInsuplcAdmdvs());
        int success = 0;
        List<String> failures = new ArrayList<>();
        for (String otherMdtrtId : otherInHospital) {
            try {
                Ip2404Input input2404 = IpInpatientSettleInputAssembler.assemble2404Or2405(
                    otherMdtrtId, ctx.getPsnNo());
                ResultBody body2404 = gatewayClient.invoke(
                    ctx.getContractBusNo(),
                    ChsInfNo.INPATIENT_ADMISSION_REVOKE,
                    input2404,
                    invokeContext);
                ChsPlatformResponseSupport.requireSuccess(body2404, ChsInfNo.INPATIENT_ADMISSION_REVOKE);
                success++;
            } catch (RuntimeException ex) {
                failures.add(otherMdtrtId + ":" + ex.getMessage());
            }
        }
        if (success <= 0 && !failures.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "自动撤销多余在院就诊失败，请人工处理。详情：" + String.join("；", failures));
        }
        return success;
    }

    /**
     * 5201 列出同人其它在院住院就诊 ID。
     *
     * @param ctx         结算上下文
     * @param keepMdtrtId 本单保留就诊
     * @return 其它在院 mdtrt_id 集合
     */
    private Set<String> listOtherInHospitalMdtrtIds(IpSettleContext ctx, String keepMdtrtId) {
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
        Set<String> others = new LinkedHashSet<>();
        if (result == null || result.getRows() == null) {
            return others;
        }
        for (Map<String, Object> row : result.getRows()) {
            if (row == null) {
                continue;
            }
            String mdtrtId = firstText(row, "mdtrt_id", "mdtrtId");
            if (StringUtils.isEmpty(mdtrtId) || keepMdtrtId.equals(mdtrtId.trim())) {
                continue;
            }
            String inhospStas = firstText(row, "inhosp_stas", "inhospStas");
            if (!INHOSP_STAS_IN_HOSPITAL.equals(inhospStas)) {
                continue;
            }
            others.add(mdtrtId.trim());
        }
        return others;
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

    /**
     * 2405 是否因「同人另有在院」被拒。
     *
     * @param ex 平台异常
     * @return 命中时为 true
     */
    public static boolean isBlockedByOtherInHospital(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("不能办理出院撤销")) {
            return true;
        }
        if (msg.contains("其他为在院状态")) {
            return true;
        }
        return msg.contains("iptPsnSetlMgtBO_1587");
    }

    /**
     * 2405 失败是否表示平台本就未出院。
     *
     * @param ex 平台异常
     * @return 命中未出院文案时为 true
     */
    public static boolean isNotDischargedOnPlatform(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("不是出院")) {
            return true;
        }
        if (msg.contains("非出院")) {
            return true;
        }
        if (msg.contains("未办理出院")) {
            return true;
        }
        return msg.contains("入院状态");
    }
}
