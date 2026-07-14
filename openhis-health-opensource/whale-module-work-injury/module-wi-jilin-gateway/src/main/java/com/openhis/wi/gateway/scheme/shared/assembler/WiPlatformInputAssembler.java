/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.shared.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.defaultString;
import com.openhis.wi.gateway.scheme.recon.model.Wi1320Input;
import com.openhis.wi.gateway.scheme.recon.model.Wi1321Input;
import com.openhis.wi.gateway.scheme.recon.model.Wi1322Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2201Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2203Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2204FeeLine;
import com.openhis.wi.gateway.scheme.settle.model.Wi2204Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2205Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2206Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2207Input;
import com.openhis.wi.gateway.scheme.settle.model.Wi2208Input;
import com.openhis.wi.gateway.scheme.register.model.Wi2301Input;
import com.openhis.wi.gateway.scheme.register.model.Wi9102Input;
import com.openhis.wi.gateway.scheme.shared.model.WiInfnoConstants;
import com.openhis.wi.gateway.scheme.shared.model.WiJsonSupport;
import com.openhis.wi.gateway.scheme.shared.model.WiPlatformConstants;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.settle.command.WiSettleChargeLine;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.whale.core.application.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工伤 P0 infno 业务体组装（10-P0 / V1.3）。
 *
 * <p>Whale 只下发 {@code input} Map；fixmedins_code、operator、sign 等外壳由 OpenHIS.Win 注入。</p>
 * <p>私有 helper 默认值与字段映射真源：{@code whale-doc/whale-module-work-injury/10-P0-infno字段映射.md}。</p>
 * <p>调用方：{@link com.openhis.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator}、
 * {@link com.openhis.wi.bridge.register.invoker.Build2301PayloadInvokerImpl}。</p>
 */
public final class WiPlatformInputAssembler {

    private WiPlatformInputAssembler() {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 按 infno 组装 Win 待发送的业务 input Map。
     *
     * @param infno         交易号
     * @param ctx           HIS 上下文（可为 null，9102 仅 anchor）
     * @param anchor        就诊锚点
     * @param formContext   前端表单（2301 等）
     * @param mdtrtIdFor2207 2207 用 2206 返回的单据号
     * @param settlementRecord 2208 用本地结算回执
     * @return 平台 input 对象
     */
    public static Map<String, Object> assemble(
            String infno,
            WiSettleContext ctx,
            YbWiEncounterAnchor anchor,
            Map<String, Object> formContext,
            String mdtrtIdFor2207,
            YbSettlementRecord settlementRecord) {
        if (WiInfnoConstants.QUALIFICATION_QUERY.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble9102(anchor));
        }
        if (WiInfnoConstants.APPROVAL_UPLOAD.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2301(ctx, anchor, formContext));
        }
        if (WiInfnoConstants.OUTPATIENT_REG.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2201(ctx, anchor));
        }
        if (WiInfnoConstants.OUTPATIENT_VISIT_UPLOAD.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2203(ctx, anchor));
        }
        if (WiInfnoConstants.OUTPATIENT_FEE_REVOKE.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2205(anchor));
        }
        if (WiInfnoConstants.OUTPATIENT_FEE_UPLOAD.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2204(ctx, anchor));
        }
        if (WiInfnoConstants.OUTPATIENT_PRE_SETTLE.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2206(ctx, anchor, null));
        }
        if (WiInfnoConstants.OUTPATIENT_SETTLE.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2207(ctx, anchor, mdtrtIdFor2207));
        }
        if (WiInfnoConstants.OUTPATIENT_SETTLE_REVOKE.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble2208(settlementRecord, anchor));
        }
        if (WiInfnoConstants.RECON_SUMMARY.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble1320(formContext));
        }
        if (WiInfnoConstants.RECON_DETAIL.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble1321(formContext));
        }
        if (WiInfnoConstants.RECON_CONFIRM.equals(infno)) {
            return WiJsonSupport.toInputMap(assemble1322(formContext));
        }
        throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "暂不支持的 infno: " + infno);
    }

    /**
     * 【9102】审批信息查询。
     *
     * @param anchor 就诊锚点（须含 psn_no）
     * @return 9102 input DTO
     */
    public static Wi9102Input assemble9102(YbWiEncounterAnchor anchor) {
        requireAnchorPsn(anchor);
        LocalDate today = LocalDate.now();
        Wi9102Input input = new Wi9102Input();
        input.setStartTime(today.minusDays(WiPlatformConstants.QUERY_START_DAYS_BEFORE).format(DAY));
        input.setEndTime(today.format(DAY));
        input.setPsnNo(anchor.getPsnNo());
        return input;
    }

    /**
     * 【2301】审批信息上传。
     *
     * @param ctx         HIS 上下文（injureAffirmId 回退来源）
     * @param anchor      就诊锚点
     * @param formContext 前端表单（approveType/startDate/…）
     * @return 2301 input DTO
     */
    public static Wi2301Input assemble2301(
            WiSettleContext ctx,
            YbWiEncounterAnchor anchor,
            Map<String, Object> formContext) {
        requireAnchorPsn(anchor);
        Wi2301Input input = new Wi2301Input();
        input.setPsnNo(anchor.getPsnNo());
        input.setApproveType(resolveString(formContext, "approveType", WiPlatformConstants.DEFAULT_APPROVE_TYPE));
        input.setOrgCode(trimToNull(stringValue(formContext, "orgCode")));
        input.setOrgName(trimToNull(stringValue(formContext, "orgName")));
        input.setStartDate(resolveStartDate(formContext));
        input.setEndDate(resolveEndDate(formContext, input.getStartDate()));
        input.setTerminReason(resolveString(formContext, "terminReason", "门诊工伤审批"));
        String injureAffirmId = resolveString(formContext, "injureAffirmId", null);
        if (isEmpty(injureAffirmId) && anchor != null) {
            injureAffirmId = anchor.getInjureAffirmId();
        }
        if (isEmpty(injureAffirmId) && ctx != null) {
            injureAffirmId = ctx.getInjureAffirmId();
        }
        if (isEmpty(injureAffirmId)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2301 缺少 injureAffirmID");
        }
        input.setInjureAffirmId(injureAffirmId);
        return input;
    }

    /**
     * 【2201】门诊登记。
     *
     * @param ctx    HIS 结算上下文
     * @param anchor 就诊锚点（须含 qualification_id）
     * @return 2201 input DTO
     */
    public static Wi2201Input assemble2201(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        WiSettleContext resolved = requireSettleContext(ctx, anchor);
        requireQualification(anchor);
        Wi2201Input input = new Wi2201Input();
        input.setIptOtpNo(resolved.getIptOtpNo());
        input.setMedType(resolveMedType(resolved));
        input.setAdmTime(formatDateTime(resolved.getAdmTime()));
        input.setAdmDiagDscr(resolveDiagCode(resolved, anchor));
        input.setWardareaName(blankToDash(resolved.getDeptName()));
        input.setAdmDeptCodg(blankToDash(resolved.getDeptCode()));
        input.setAdmBed("-");
        input.setAtddrNo(blankToDash(resolved.getAtddrNo()));
        input.setTel(resolveTel(resolved));
        input.setPsnNo(resolved.getPsnNo());
        input.setIptNo("");
        input.setQualificationId(anchor.getQualificationId());
        return input;
    }

    /**
     * 【2203】登记信息修改。
     *
     * @param ctx    HIS 结算上下文
     * @param anchor 就诊锚点
     * @return 2203 input DTO
     */
    public static Wi2203Input assemble2203(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        WiSettleContext resolved = requireSettleContext(ctx, anchor);
        Wi2203Input input = new Wi2203Input();
        input.setIptOtpNo(resolved.getIptOtpNo());
        input.setAdmTime(formatDateTime(resolved.getAdmTime()));
        input.setPsnNo(resolved.getPsnNo());
        input.setMedType(resolveMedType(resolved));
        input.setAdmDiagDscr(resolveDiagCode(resolved, anchor));
        input.setWardareaName(blankToDash(resolved.getDeptName()));
        input.setAdmDeptCodg(blankToDash(resolved.getDeptCode()));
        input.setAdmBed("-");
        input.setAtddrNo(blankToDash(resolved.getAtddrNo()));
        return input;
    }

    /**
     * 【2205】撤销未结算处方（整单：仅 ipt_otp_no）。
     *
     * @param anchor 就诊锚点（须含 ipt_otp_no）
     * @return 2205 input DTO
     */
    public static Wi2205Input assemble2205(YbWiEncounterAnchor anchor) {
        requireAnchorIpt(anchor);
        Wi2205Input input = new Wi2205Input();
        input.setIptOtpNo(anchor.getIptOtpNo());
        return input;
    }

    /**
     * 【2204】处方明细上报（最多 50 条/次，超出需分批——Wave1 整批上传）。
     *
     * @param ctx    HIS 上下文（chargeLines 必填）
     * @param anchor 就诊锚点
     * @return 2204 input DTO（inputs[]）
     */
    public static Wi2204Input assemble2204(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        WiSettleContext resolved = requireSettleContext(ctx, anchor);
        if (resolved.getChargeLines() == null || resolved.getChargeLines().isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2204 无收费明细");
        }
        Wi2204Input input = new Wi2204Input();
        List<Wi2204FeeLine> lines = new ArrayList<>();
        for (WiSettleChargeLine line : resolved.getChargeLines()) {
            Wi2204FeeLine row = new Wi2204FeeLine();
            row.setIptOtpNo(resolved.getIptOtpNo());
            row.setListType(resolveListType(line));
            row.setRxno(blankToDash(line.getRxno()));
            row.setFeedetlSn(line.getFeedetlSn());
            row.setFeeOcurTime(formatDateTime(line.getFeeOcurTime()));
            row.setMedListCodg(line.getMedListCodg());
            row.setPric(formatDecimal(line.getPric()));
            row.setCnt(formatDecimal(line.getCnt()));
            row.setUmamt(formatDecimal(line.getUmamt()));
            row.setBilgDrCodg(blankToDash(line.getBilgDrCodg()));
            row.setBilgDeptCodg(blankToDash(line.getBilgDeptCodg()));
            row.setMinUnit(WiPlatformConstants.MIN_UNIT_NO);
            lines.add(row);
        }
        input.setInputs(lines);
        return input;
    }

    /**
     * 【2206】费用预结算（mdtrt_id 传空）。
     *
     * @param ctx     HIS 结算上下文
     * @param anchor  就诊锚点
     * @param mdtrtId 平台单据号（2206 传 null 或空串）
     * @return 2206 input DTO
     */
    public static Wi2206Input assemble2206(WiSettleContext ctx, YbWiEncounterAnchor anchor, String mdtrtId) {
        return build2206Body(ctx, anchor, mdtrtId);
    }

    /**
     * 【2207】费用结算（mdtrt_id 必填）。
     *
     * @param ctx     HIS 结算上下文
     * @param anchor  就诊锚点
     * @param mdtrtId 2206 返回的 mdtrt_id
     * @return 2207 input DTO
     */
    public static Wi2207Input assemble2207(WiSettleContext ctx, YbWiEncounterAnchor anchor, String mdtrtId) {
        if (isEmpty(mdtrtId)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2207 缺少 mdtrt_id（须先完成 2206）");
        }
        Wi2207Input input = new Wi2207Input();
        Wi2206Input base = build2206Body(ctx, anchor, mdtrtId);
        copy2206To2207(base, input);
        return input;
    }

    /**
     * 【2208】费用结算撤销。
     *
     * @param record 本地结算回执（setl_id 必填）
     * @param anchor 就诊锚点（psn_no 回退）
     * @return 2208 input DTO
     */
    public static Wi2208Input assemble2208(YbSettlementRecord record, YbWiEncounterAnchor anchor) {
        if (record == null || isEmpty(record.getSetlId())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2208 缺少 setl_id");
        }
        Wi2208Input input = new Wi2208Input();
        input.setSetlId(record.getSetlId().trim());
        input.setMdtrtId(defaultString(record.getMdtrtId()).trim());
        String psnNo = "";
        if (isNotEmpty(record.getPsnNo())) {
            psnNo = record.getPsnNo().trim();
        } else if (anchor != null) {
            psnNo = defaultString(anchor.getPsnNo()).trim();
        }
        input.setPsnNo(psnNo);
        return input;
    }

    /** 2206/2207 共用报文体；2206 时 mdtrtId 传空串。 */
    private static Wi2206Input build2206Body(WiSettleContext ctx, YbWiEncounterAnchor anchor, String mdtrtId) {
        WiSettleContext resolved = requireSettleContext(ctx, anchor);
        LocalDateTime now = LocalDateTime.now();
        Wi2206Input input = new Wi2206Input();
        input.setIptOtpNo(resolved.getIptOtpNo());
        if (isNotEmpty(mdtrtId)) {
            input.setMdtrtId(mdtrtId);
        } else {
            input.setMdtrtId("");
        }
        input.setMedType(resolveMedType(resolved));
        input.setSetlTime(formatDateTime(now));
        input.setDscgTime(formatDateTime(now));
        input.setDscgTrtRslt(WiPlatformConstants.DEFAULT_DSCG_TRT_RSLT);
        input.setDiagCode(resolveDiagCode(resolved, anchor));
        input.setPsnNo(resolved.getPsnNo());
        input.setAdmDeptCodg(blankToDash(resolved.getDeptCode()));
        input.setAtddrNo(blankToDash(resolved.getAtddrNo()));
        input.setDiagDscr(resolveDiagCode(resolved, anchor));
        return input;
    }

    /** 2207 字段与 2206 一致，复制已有 body。 */
    private static void copy2206To2207(Wi2206Input source, Wi2207Input target) {
        target.setIptOtpNo(source.getIptOtpNo());
        target.setMdtrtId(source.getMdtrtId());
        target.setMedType(source.getMedType());
        target.setSetlTime(source.getSetlTime());
        target.setDscgTime(source.getDscgTime());
        target.setDscgTrtRslt(source.getDscgTrtRslt());
        target.setDiagCode(source.getDiagCode());
        target.setPsnNo(source.getPsnNo());
        target.setAdmDeptCodg(source.getAdmDeptCodg());
        target.setAtddrNo(source.getAtddrNo());
        target.setDiagDscr(source.getDiagDscr());
    }

    /**
     * 校验并补全结算上下文：ctx 必填，psn_no/ipt_otp_no 可回退 anchor。
     *
     * @throws BusinessException WI-006 缺 ctx / psn_no / ipt_otp_no
     */
    private static WiSettleContext requireSettleContext(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        if (ctx == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 HIS 结算上下文");
        }
        if (isEmpty(ctx.getPsnNo()) && anchor != null) {
            ctx.setPsnNo(anchor.getPsnNo());
        }
        if (isEmpty(ctx.getIptOtpNo()) && anchor != null) {
            ctx.setIptOtpNo(anchor.getIptOtpNo());
        }
        requireAnchorPsn(anchor);
        if (isEmpty(ctx.getPsnNo())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 psn_no");
        }
        if (isEmpty(ctx.getIptOtpNo())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 ipt_otp_no");
        }
        return ctx;
    }

    /** 9102/2201 等须已完成 1101 写入 psn_no。 */
    private static void requireAnchorPsn(YbWiEncounterAnchor anchor) {
        if (anchor == null || isEmpty(anchor.getPsnNo())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 psn_no，请先完成工伤 1101");
        }
    }

    /** 2205 撤销须已有 ipt_otp_no。 */
    private static void requireAnchorIpt(YbWiEncounterAnchor anchor) {
        if (anchor == null || isEmpty(anchor.getIptOtpNo())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少 ipt_otp_no");
        }
    }

    /** 2201 OP_SETTLE 须已 bind qualification_id（9102）。 */
    private static void requireQualification(YbWiEncounterAnchor anchor) {
        if (anchor == null || isEmpty(anchor.getQualificationId())) {
            throw new BusinessException(WiErrorCodes.NO_QUALIFICATION, "缺少 qualification_id，请先完成 9102");
        }
    }

    /** ctx.medType 优先，缺省 {@link WiPlatformConstants#DEFAULT_MED_TYPE}。 */
    private static String resolveMedType(WiSettleContext ctx) {
        if (ctx != null && isNotEmpty(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        return WiPlatformConstants.DEFAULT_MED_TYPE;
    }

    /** ctx.admDiagCode 优先，其次 anchor，缺省 "-"。 */
    private static String resolveDiagCode(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        if (isNotEmpty(ctx.getAdmDiagCode())) {
            return ctx.getAdmDiagCode().trim();
        }
        if (anchor != null && isNotEmpty(anchor.getAdmDiagCode())) {
            return anchor.getAdmDiagCode().trim();
        }
        return "-";
    }

    private static String resolveTel(WiSettleContext ctx) {
        if (isNotEmpty(ctx.getPatientTel())) {
            return ctx.getPatientTel().trim();
        }
        return WiPlatformConstants.DEFAULT_TEL;
    }

    private static String resolveListType(WiSettleChargeLine line) {
        if (line != null && isNotEmpty(line.getListType())) {
            return line.getListType().trim();
        }
        return WiPlatformConstants.DEFAULT_LIST_TYPE;
    }

    private static String resolveStartDate(Map<String, Object> formContext) {
        String fromForm = stringValue(formContext, "startDate");
        if (isNotEmpty(fromForm)) {
            return fromForm.trim();
        }
        return LocalDate.now().format(DAY);
    }

    private static String resolveEndDate(Map<String, Object> formContext, String startDate) {
        String fromForm = stringValue(formContext, "endDate");
        if (isNotEmpty(fromForm)) {
            return fromForm.trim();
        }
        LocalDate start = LocalDate.parse(startDate, DAY);
        return start.plusDays(WiPlatformConstants.APPROVAL_DEFAULT_DAYS).format(DAY);
    }

    private static String resolveString(Map<String, Object> formContext, String key, String defaultValue) {
        String value = stringValue(formContext, key);
        if (isNotEmpty(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    private static String stringValue(Map<String, Object> formContext, String key) {
        if (formContext == null || !formContext.containsKey(key)) {
            return null;
        }
        Object raw = formContext.get(key);
        if (raw == null) {
            return null;
        }
        return String.valueOf(raw);
    }

    private static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return LocalDateTime.now().format(DATE_TIME);
        }
        return time.format(DATE_TIME);
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String blankToDash(String value) {
        if (isEmpty(value)) {
            return "-";
        }
        return value.trim();
    }

    /**
     * 【1320】总额对账。
     *
     * @param formContext stmtBegindate / stmtEnddate / socialPoolCode
     * @return 1320 input DTO
     */
    public static Wi1320Input assemble1320(Map<String, Object> formContext) {
        Wi1320Input input = new Wi1320Input();
        LocalDate today = LocalDate.now();
        input.setStmtBegindate(resolveString(formContext, "stmtBegindate", today.withDayOfMonth(1).format(DAY)));
        input.setStmtEnddate(resolveString(formContext, "stmtEnddate", today.format(DAY)));
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }

    /**
     * 【1321】明细对账。
     *
     * @param formContext month / socialPoolCode
     * @return 1321 input DTO
     */
    public static Wi1321Input assemble1321(Map<String, Object> formContext) {
        Wi1321Input input = new Wi1321Input();
        input.setMonth(resolveString(formContext, "month", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))));
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }

    /**
     * 【1322】对账确认。
     *
     * @param formContext month / socialPoolCode
     * @return 1322 input DTO
     */
    public static Wi1322Input assemble1322(Map<String, Object> formContext) {
        Wi1322Input input = new Wi1322Input();
        input.setMonth(resolveString(formContext, "month", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))));
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }
}
