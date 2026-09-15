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
import java.util.Collections;
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
     * 按 infno 组装 Win 待发送的业务 input Map（2204 默认第 0 批）。
     */
    public static Map<String, Object> assemble(
            String infno,
            WiSettleContext ctx,
            YbWiEncounterAnchor anchor,
            Map<String, Object> formContext,
            String mdtrtIdFor2207,
            YbSettlementRecord settlementRecord) {
        return assemble(infno, ctx, anchor, formContext, mdtrtIdFor2207, settlementRecord, 0);
    }

    /**
     * 按 infno 组装 Win 待发送的业务 input Map。
     *
     * @param infno              交易号
     * @param ctx                HIS 上下文（可为 null，9102 仅 anchor）
     * @param anchor             就诊锚点
     * @param formContext        前端表单（2301 等）
     * @param mdtrtIdFor2207     2207 用 2206 返回的单据号
     * @param settlementRecord   2208 用本地结算回执
     * @param feeUploadBatchIndex 2204 批次下标（0-based，每批最多 {@link WiPlatformConstants#FEE_UPLOAD_BATCH_SIZE}）
     * @return 平台 input 对象
     */
    public static Map<String, Object> assemble(
            String infno,
            WiSettleContext ctx,
            YbWiEncounterAnchor anchor,
            Map<String, Object> formContext,
            String mdtrtIdFor2207,
            YbSettlementRecord settlementRecord,
            int feeUploadBatchIndex) {
        if (WiInfnoConstants.PERSON_INFO.equals(infno)) {
            // NEUSI：input={}；读卡由 DLL / idfi_mode 完成
            return Collections.emptyMap();
        }
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
            return WiJsonSupport.toInputMap(assemble2204(ctx, anchor, feeUploadBatchIndex));
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
        input.setStartTime(today.minusYears(WiPlatformConstants.QUERY_START_YEARS_BEFORE).format(DAY));
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
        input.setMedType(resolveMedType(resolved, anchor));
        input.setAdmTime(formatDateTime(resolved.getAdmTime()));
        input.setAdmDiagDscr(requireDiagCode(resolved, anchor));
        input.setWardareaName(blankToDash(resolved.getDeptName()));
        input.setAdmDeptCodg(requirePlatformCode(resolved.getDeptCode(), "开单科室医保编码(adm_dept_codg)"));
        input.setAdmBed("-");
        input.setAtddrNo(requirePlatformCode(resolved.getAtddrNo(), "接诊医生医保编码(atddr_no)"));
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
        input.setMedType(resolveMedType(resolved, anchor));
        input.setAdmDiagDscr(requireDiagCode(resolved, anchor));
        input.setWardareaName(blankToDash(resolved.getDeptName()));
        input.setAdmDeptCodg(requirePlatformCode(resolved.getDeptCode(), "开单科室医保编码(adm_dept_codg)"));
        input.setAdmBed("-");
        input.setAtddrNo(requirePlatformCode(resolved.getAtddrNo(), "接诊医生医保编码(atddr_no)"));
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
        // 整单撤销：处方号、流水号显式空串（手册：仅 ipt_otp_no 则撤该流水下全部未结算处方）
        input.setRxno("");
        input.setFeedetlSn("");
        return input;
    }

    /**
     * 【2204】处方明细上报（单次最多 {@link WiPlatformConstants#FEE_UPLOAD_BATCH_SIZE} 条）。
     *
     * @param ctx                 HIS 上下文（chargeLines 必填）
     * @param anchor              就诊锚点
     * @param feeUploadBatchIndex 批次下标（0-based）
     * @return 2204 input DTO（顶层 inputs[]）
     */
    public static Wi2204Input assemble2204(
            WiSettleContext ctx, YbWiEncounterAnchor anchor, int feeUploadBatchIndex) {
        WiSettleContext resolved = requireSettleContext(ctx, anchor);
        if (resolved.getChargeLines() == null || resolved.getChargeLines().isEmpty()) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2204 无收费明细");
        }
        List<WiSettleChargeLine> all = resolved.getChargeLines();
        int batchSize = WiPlatformConstants.FEE_UPLOAD_BATCH_SIZE;
        int from = Math.max(0, feeUploadBatchIndex) * batchSize;
        if (from >= all.size()) {
            throw new BusinessException(
                    WiErrorCodes.INVALID_INVOKE_SESSION,
                    "2204 批次越界：batch=" + feeUploadBatchIndex + "，明细=" + all.size());
        }
        int to = Math.min(from + batchSize, all.size());
        Wi2204Input input = new Wi2204Input();
        List<Wi2204FeeLine> lines = new ArrayList<>();
        for (WiSettleChargeLine line : all.subList(from, to)) {
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
            row.setBilgDrCodg(requirePlatformCode(
                    firstNonBlank(line.getBilgDrCodg(), resolved.getAtddrNo()),
                    "开单医生医保编码(bilg_dr_codg)"));
            row.setBilgDeptCodg(requirePlatformCode(
                    firstNonBlank(line.getBilgDeptCodg(), resolved.getDeptCode()),
                    "开单科室医保编码(bilg_dept_codg)"));
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
     * 【2207】费用结算。手册：单据号不能为空，传入实际单据号（优先 2206 出参；院点空则用本地 chrg_bchno）。
     *
     * @param ctx     HIS 结算上下文
     * @param anchor  就诊锚点
     * @param mdtrtId 单据号（须非空）
     * @return 2207 input DTO
     */
    public static Wi2207Input assemble2207(WiSettleContext ctx, YbWiEncounterAnchor anchor, String mdtrtId) {
        if (isEmpty(mdtrtId)) {
            throw new BusinessException(
                    WiErrorCodes.INVALID_INVOKE_SESSION, "2207 缺少 mdtrt_id（须先完成 2206 并落单据号）");
        }
        Wi2207Input input = new Wi2207Input();
        Wi2206Input base = build2206Body(ctx, anchor, mdtrtId.trim());
        copy2206To2207(base, input);
        return input;
    }

    /**
     * 【2208】费用结算撤销（手册：ipt_otp_no / mdtrt_id / setl_time / keep_tp_type）。
     *
     * @param record 本地结算回执（mdtrt_id、setl_time 必填）
     * @param anchor 就诊锚点（ipt_otp_no 回退）
     * @return 2208 input DTO
     */
    public static Wi2208Input assemble2208(YbSettlementRecord record, YbWiEncounterAnchor anchor) {
        if (record == null || isEmpty(record.getMdtrtId())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2208 缺少 mdtrt_id");
        }
        String iptOtpNo = "";
        if (anchor != null && isNotEmpty(anchor.getIptOtpNo())) {
            iptOtpNo = anchor.getIptOtpNo().trim();
        }
        if (isEmpty(iptOtpNo)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2208 缺少 ipt_otp_no");
        }
        Wi2208Input input = new Wi2208Input();
        input.setIptOtpNo(iptOtpNo);
        input.setMdtrtId(record.getMdtrtId().trim());
        if (record.getSetlTime() != null) {
            input.setSetlTime(formatDateTime(record.getSetlTime()));
        } else {
            input.setSetlTime(formatDateTime(LocalDateTime.now()));
        }
        // 退费默认不保留处方，便于整单撤销后重新上传
        input.setKeepTpType(WiPlatformConstants.KEEP_TP_TYPE_NO);
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
        input.setMedType(resolveMedType(resolved, anchor));
        input.setSetlTime(formatDateTime(now));
        input.setDscgTime(formatDateTime(now));
        input.setDscgTrtRslt(WiPlatformConstants.DEFAULT_DSCG_TRT_RSLT);
        String diag = requireDiagCode(resolved, anchor);
        input.setDiagCode(diag);
        input.setPsnNo(resolved.getPsnNo());
        input.setAdmDeptCodg(requirePlatformCode(resolved.getDeptCode(), "开单科室医保编码(adm_dept_codg)"));
        input.setAtddrNo(requirePlatformCode(resolved.getAtddrNo(), "接诊医生医保编码(atddr_no)"));
        input.setDiagDscr(diag);
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

    /** 2201 OP_SETTLE 须已 bind qualification_id（9102），且就医类型为院点开通的 12。 */
    private static void requireQualification(YbWiEncounterAnchor anchor) {
        if (anchor == null || isEmpty(anchor.getQualificationId())) {
            throw new BusinessException(
                    WiErrorCodes.NO_QUALIFICATION,
                    "缺少有效门诊工伤审批（类型12）。若仅有审批类型10，请先「一键备案」申请类型12后再收费");
        }
        String medType = isEmpty(anchor.getMedType())
                ? WiPlatformConstants.DEFAULT_MED_TYPE
                : anchor.getMedType().trim();
        if (!WiPlatformConstants.DEFAULT_MED_TYPE.equals(medType)) {
            throw new BusinessException(
                    WiErrorCodes.NO_QUALIFICATION,
                    "当前审批/就医类型为 "
                            + medType
                            + "，与院点联网结算类型12不符。请重新办理门诊工伤备案（审批类型12）后再收费");
        }
    }

    /** ctx.medType → anchor.medType → 默认 12（须与所绑审批类型一致，勿强改）。 */
    private static String resolveMedType(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        if (ctx != null && isNotEmpty(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        if (anchor != null && isNotEmpty(anchor.getMedType())) {
            return anchor.getMedType().trim();
        }
        return WiPlatformConstants.DEFAULT_MED_TYPE;
    }

    /** 2201/2206 必填 ICD10；缺失时阻断，避免平台报含糊错误。 */
    private static String requireDiagCode(WiSettleContext ctx, YbWiEncounterAnchor anchor) {
        if (ctx != null && isNotEmpty(ctx.getAdmDiagCode()) && !"-".equals(ctx.getAdmDiagCode().trim())) {
            return ctx.getAdmDiagCode().trim();
        }
        if (anchor != null && isNotEmpty(anchor.getAdmDiagCode()) && !"-".equals(anchor.getAdmDiagCode().trim())) {
            return anchor.getAdmDiagCode().trim();
        }
        throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "缺少诊断医保编码(ICD10)，请先维护就诊诊断对照");
    }

    /** 必填平台编码（科室/医生 yb_no）；禁止用 "-" 糊弄。 */
    private static String requirePlatformCode(String value, String fieldLabel) {
        if (isEmpty(value) || "-".equals(value.trim())) {
            throw new BusinessException(
                    WiErrorCodes.INVALID_INVOKE_SESSION, fieldLabel + "不能为空，请在科室/人员档案维护医保编码");
        }
        return value.trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (isNotEmpty(primary) && !"-".equals(primary.trim())) {
            return primary.trim();
        }
        if (isNotEmpty(fallback) && !"-".equals(fallback.trim())) {
            return fallback.trim();
        }
        return primary;
    }

    private static String resolveTel(WiSettleContext ctx) {
        if (isNotEmpty(ctx.getPatientTel())) {
            return ctx.getPatientTel().trim();
        }
        return WiPlatformConstants.DEFAULT_TEL;
    }

    private static String resolveListType(WiSettleChargeLine line) {
        if (line != null && isNotEmpty(line.getListType())) {
            return normalizeListType(line.getListType().trim());
        }
        return WiPlatformConstants.DEFAULT_LIST_TYPE;
    }

    /**
     * 工伤手册三大目录：1药品/2诊疗/3材料。兼容误传入的省医保 101/103/201。
     *
     * @param raw HIS 或上下文中的 list_type
     * @return 平台合法值
     */
    private static String normalizeListType(String raw) {
        if ("1".equals(raw) || "2".equals(raw) || "3".equals(raw)) {
            return raw;
        }
        if ("101".equals(raw)) {
            return WiPlatformConstants.LIST_TYPE_DRUG;
        }
        if ("103".equals(raw)) {
            return WiPlatformConstants.LIST_TYPE_MATERIAL;
        }
        if ("201".equals(raw)) {
            return WiPlatformConstants.LIST_TYPE_SERVICE;
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
     * 【1320】总额对账（V2.5：本市 / 省内异地 / 全国异地三口径）。
     *
     * @param formContext stmtBegindate / stmtEnddate / socialPoolCode / reconScope / snydSign / qgydSign
     * @return 1320 input DTO
     */
    public static Wi1320Input assemble1320(Map<String, Object> formContext) {
        Wi1320Input input = new Wi1320Input();
        LocalDate today = LocalDate.now();
        input.setStmtBegindate(resolveString(formContext, "stmtBegindate", today.withDayOfMonth(1).format(DAY)));
        input.setStmtEnddate(resolveString(formContext, "stmtEnddate", today.format(DAY)));
        input.setRefdSetlFlag(resolve1320RefdSetlFlag(formContext));
        input.setOpter(trimToNull(resolveString(formContext, "opter", null)));

        String snydSign = resolve1320YdSign(formContext, "snydSign", "snyd_sign");
        String qgydSign = resolve1320YdSign(formContext, "qgydSign", "qgyd_sign");
        if (isEmpty(snydSign) && isEmpty(qgydSign)) {
            String[] scoped = resolve1320ScopeFlags(formContext);
            snydSign = scoped[0];
            qgydSign = scoped[1];
        }
        if (isEmpty(snydSign)) {
            snydSign = WiPlatformConstants.YD_SIGN_NO;
        }
        if (isEmpty(qgydSign)) {
            qgydSign = WiPlatformConstants.YD_SIGN_NO;
        }
        input.setSnydSign(snydSign);
        input.setQgydSign(qgydSign);
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }

    private static String resolve1320RefdSetlFlag(Map<String, Object> formContext) {
        String value = resolveString(formContext, "refdSetlFlag", null);
        if (isEmpty(value)) {
            value = resolveString(formContext, "refd_setl_flag", null);
        }
        return isEmpty(value) ? WiPlatformConstants.REFD_SETL_FLAG_YES : value.trim();
    }

    private static String resolve1320YdSign(Map<String, Object> formContext, String camelKey, String snakeKey) {
        String value = resolveString(formContext, camelKey, null);
        if (isEmpty(value)) {
            value = resolveString(formContext, snakeKey, null);
        }
        return isEmpty(value) ? null : value.trim();
    }

    /** reconScope：LOCAL=本市；SNYD=省内异地；QGYD=全国异地。 */
    private static String[] resolve1320ScopeFlags(Map<String, Object> formContext) {
        String scope = resolveString(formContext, "reconScope", "LOCAL");
        return switch (scope.trim().toUpperCase()) {
            case "SNYD", "PROVINCIAL" -> new String[] {
                    WiPlatformConstants.YD_SIGN_YES, WiPlatformConstants.YD_SIGN_NO
            };
            case "QGYD", "NATIONAL" -> new String[] {
                    WiPlatformConstants.YD_SIGN_NO, WiPlatformConstants.YD_SIGN_YES
            };
            default -> new String[] { WiPlatformConstants.YD_SIGN_NO, WiPlatformConstants.YD_SIGN_NO };
        };
    }

    /**
     * 【1321】明细对账（手册：ipt_otp_no + social_pool_code；院点月对账链默认不调用）。
     *
     * @param formContext iptOtpNo / socialPoolCode
     * @return 1321 input DTO
     */
    public static Wi1321Input assemble1321(Map<String, Object> formContext) {
        Wi1321Input input = new Wi1321Input();
        String iptOtpNo = resolveString(formContext, "iptOtpNo", null);
        if (isEmpty(iptOtpNo)) {
            iptOtpNo = resolveString(formContext, "ipt_otp_no", null);
        }
        if (isEmpty(iptOtpNo)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "1321 缺少 ipt_otp_no");
        }
        input.setIptOtpNo(iptOtpNo.trim());
        input.setMedrcdno(resolveString(formContext, "medrcdno", "0"));
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }

    /**
     * 【1322】对账确认。
     *
     * @param formContext month（必填 yyyyMM）/ socialPoolCode
     * @return 1322 input DTO
     */
    public static Wi1322Input assemble1322(Map<String, Object> formContext) {
        Wi1322Input input = new Wi1322Input();
        String month = resolveReconMonth(formContext);
        if (isEmpty(month)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "1322 须指定对账月份（yyyyMM，且须为已结束月份）");
        }
        input.setMonth(month);
        input.setSocialPoolCode(resolveString(formContext, "socialPoolCode", WiPlatformConstants.DEFAULT_SOCIAL_POOL_CODE));
        return input;
    }

    /** 对账月份统一为 yyyyMM；空则返回空串（不再默认当月，避免平台「月份还未结束」）。 */
    private static String resolveReconMonth(Map<String, Object> formContext) {
        String raw = resolveString(formContext, "month", "");
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replace("-", "").trim();
    }
}
