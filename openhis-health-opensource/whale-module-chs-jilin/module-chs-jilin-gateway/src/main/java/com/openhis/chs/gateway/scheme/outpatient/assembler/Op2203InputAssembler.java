/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import com.openhis.chs.domain.outpatient.command.OpClinicDiagnosisLine;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.share.constants.ChsOutpatientRegisterFeeConstants;
import com.openhis.chs.domain.share.enums.DiagType;
import com.openhis.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203Input;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203MdtrtInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 【2203】门诊就诊信息上传报文组装。
 *
 * <p>Wave A：诊断禁 {@code "-"}；挂号费默认码。</p>
 * <p>Wave B：按 {@link OpPreSettleCommand#getClinicDiagnoses()} 多行上传 diseinfo（账单绑定全量）；
 * 缺列表时回退单行主诊（兼容）。首位仅 maindiag_flag，不截断上传。</p>
 */
public final class Op2203InputAssembler {

    private Op2203InputAssembler() {
    }

    /**
     * 组装 2203 入参（主诊进 mdtrtinfo；diseinfo 全量临床诊断行）。
     *
     * @param ctx     预结算上下文
     * @param mdtrtId 平台就诊号
     * @return 2203 报文根节点
     */
    public static Op2203Input assemble(OpPreSettleCommand ctx, String mdtrtId) {
        List<OpClinicDiagnosisLine> clinicDiagnoses = resolveClinicDiagnosesOrFallback(ctx);
        // Pre：main_cond_dscr 优先用上下文主诊名，缺省取 diseinfo 第 1 行
        String mainCondSource = ctx.getMainDiagName();
        if (mainCondSource == null || mainCondSource.isBlank()) {
            mainCondSource = clinicDiagnoses.get(0).getDiagName();
        }
        String mainCondDscr = OpOutpatientSettleInputAssembler.requireDiagText(
            mainCondSource, "main_cond_dscr");
        Op2203MdtrtInfo mdtrtinfo = new Op2203MdtrtInfo()
            .setMdtrtId(mdtrtId)
            .setPsnNo(ctx.getPsnNo())
            .setMedType(OpOutpatientSettleInputAssembler.resolveMedTypeForContext(ctx))
            .setBegntime(OpOutpatientSettleInputAssembler.formatTime(ctx.getEncounterStartTime()))
            .setMainCondDscr(mainCondDscr);
        List<ChsDiseInfoRow> diseinfo = new ArrayList<>(clinicDiagnoses.size());
        for (OpClinicDiagnosisLine line : clinicDiagnoses) {
            diseinfo.add(toDiseInfoRow(ctx, line));
        }
        return new Op2203Input()
            .setMdtrtinfo(mdtrtinfo)
            .setDiseinfo(diseinfo);
    }

    /**
     * 取全量诊断行；空则用 mainDiag 拼单行（Gateway 闸门仍禁 "-"）。
     *
     * @param ctx 预结算上下文
     * @return 至少一行的诊断列表
     */
    private static List<OpClinicDiagnosisLine> resolveClinicDiagnosesOrFallback(OpPreSettleCommand ctx) {
        List<OpClinicDiagnosisLine> clinicDiagnoses = ctx.getClinicDiagnoses();
        if (clinicDiagnoses != null && !clinicDiagnoses.isEmpty()) {
            return clinicDiagnoses;
        }
        // Pre：兼容未注入 clinicDiagnoses 的旧上下文
        String diagCode = OpOutpatientSettleInputAssembler.requireDiagText(
            ctx.getMainDiagCode(), "diag_code");
        String diagName = OpOutpatientSettleInputAssembler.requireDiagText(
            ctx.getMainDiagName(), "diag_name");
        OpClinicDiagnosisLine fallback = new OpClinicDiagnosisLine()
            .setDiagSrtNo(1)
            .setDiagCode(diagCode)
            .setDiagName(diagName)
            .setDiagType(DiagType.WESTERN.getCode())
            .setMaindiagFlag(ChsOutpatientRegisterFeeConstants.DIAG_MAINDIAG_FLAG_YES);
        return List.of(fallback);
    }

    /**
     * 单行 diseinfo 映射。
     *
     * @param ctx  预结算上下文（科室医师）
     * @param line 临床诊断行
     * @return 平台 diseinfo 行
     */
    private static ChsDiseInfoRow toDiseInfoRow(OpPreSettleCommand ctx, OpClinicDiagnosisLine line) {
        String diagCode = OpOutpatientSettleInputAssembler.requireDiagText(
            line.getDiagCode(), "diag_code");
        String diagName = OpOutpatientSettleInputAssembler.requireDiagText(
            line.getDiagName(), "diag_name");
        String diagType = DiagType.WESTERN.getCode();
        if (line.getDiagType() != null && !line.getDiagType().isBlank()) {
            diagType = line.getDiagType().trim();
        }
        String mainFlag = "0";
        if (ChsOutpatientRegisterFeeConstants.DIAG_MAINDIAG_FLAG_YES.equals(line.getMaindiagFlag())) {
            mainFlag = ChsOutpatientRegisterFeeConstants.DIAG_MAINDIAG_FLAG_YES;
        }
        String srtNo = "1";
        if (line.getDiagSrtNo() != null && line.getDiagSrtNo() > 0) {
            srtNo = String.valueOf(line.getDiagSrtNo());
        }
        return new ChsDiseInfoRow()
            .setDiagType(diagType)
            .setMaindiagFlag(mainFlag)
            .setDiagSrtNo(srtNo)
            .setDiagCode(diagCode)
            .setDiagName(diagName)
            .setDiagDept(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDeptCode()))
            .setDiseDorNo(OpOutpatientSettleInputAssembler.blankToDash(ctx.getAtddrNo()))
            .setDiseDorName(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDrName()))
            .setDiagTime(OpOutpatientSettleInputAssembler.formatTime(ctx.getEncounterStartTime()))
            .setValiFlag(ChsOutpatientRegisterFeeConstants.DIAG_VALI_FLAG_VALID);
    }
}
