/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.snapshot;

import com.openhis.chs.domain.audit.command.AuditAnalyze3101Command;
import com.openhis.chs.domain.audit.command.AuditDiagnoseCommand;
import com.openhis.chs.domain.audit.command.AuditEncounterCommand;
import com.openhis.chs.domain.audit.command.AuditOrderCommand;
import com.openhis.chs.domain.audit.command.AuditPatientCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.outpatient.command.OpClinicDiagnosisLine;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.chs.domain.share.constants.ChsDetailAuditConstants;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot / 门诊预结算上下文 → 3101 Command 投影。
 *
 * <p>纯映射、无 I/O；机构类型等级与 mdtrtId 由调用方写入 Snapshot 后再投影。</p>
 */
public final class AuditFeeSnapshotProjector {

    private static final DateTimeFormatter DT =
        DateTimeFormatter.ofPattern(ChsDetailAuditConstants.DATE_TIME_PATTERN);

    private AuditFeeSnapshotProjector() {
    }

    /**
     * 从门诊预结算上下文投影 Snapshot（不含 medinsType/Lv/mdtrtId，由调用方补齐）。
     *
     * @param ctx 已 enrich 的门诊预结算命令
     * @return Snapshot
     */
    public static AuditFeeSnapshot fromOpPreSettle(OpPreSettleCommand ctx) {
        AuditFeeSnapshot snap = new AuditFeeSnapshot();
        if (ctx == null) {
            return snap;
        }
        snap.setEncounterId(ctx.getEncounterId());
        snap.setPsnNo(ctx.getPsnNo());
        snap.setPatnName(ctx.getPatientName());
        // Pre：HIS AdministrativeGender（MALE/FEMALE）→ 医保 GEND（1/2/0），长度须 1～3
        snap.setGend(mapHisGenderToPlatformGend(ctx.getPatientGender()));
        // Pre：3101 brdy 与 Encounter 患者出生日期对齐（§2.6）
        snap.setBrdy(ctx.getBrdy());
        snap.setPoolarea(ctx.getInsuplcAdmdvs());
        snap.setInsutype(ctx.getInsutype());
        snap.setMedType(ctx.getMedType());
        snap.setMedMdtrtType(ChsDetailAuditConstants.MED_MDTRT_TYPE_OUTPATIENT);
        snap.setMedfeeSumamt(ctx.getMedfeeSumamt());
        snap.setAdmDate(formatDt(ctx.getEncounterStartTime()));
        // Pre：门诊无出院时点时 dscg_date 与 adm_date 同值（表 116 必填）
        snap.setDscgDate(snap.getAdmDate());
        snap.setDscgMainDiseCodg(ctx.getMainDiagCode());
        snap.setDscgMainDiseName(ctx.getMainDiagName());
        snap.setDrCodg(ctx.getAtddrNo());
        snap.setDrName(ctx.getDrName());
        snap.setAdmDeptCodg(ctx.getDeptCode());
        snap.setAdmDeptName(ctx.getDeptName());
        snap.setDscgDeptCodg(ctx.getDeptCode());
        snap.setDscgDeptName(ctx.getDeptName());
        snap.setDiagnoses(mapDiagnoses(ctx));
        snap.setOrders(mapOrders(ctx));
        return snap;
    }

    /**
     * 从住院预结算上下文投影 Snapshot（不含 medinsType/Lv/mdtrtId，由调用方补齐）。
     *
     * @param ctx 已 enrich 的住院结算上下文
     * @return Snapshot
     */
    public static AuditFeeSnapshot fromIpSettle(IpSettleContext ctx) {
        AuditFeeSnapshot snap = new AuditFeeSnapshot();
        if (ctx == null) {
            return snap;
        }
        snap.setEncounterId(ctx.getEncounterId());
        snap.setPsnNo(ctx.getPsnNo());
        snap.setPoolarea(ctx.getInsuplcAdmdvs());
        snap.setInsutype(ctx.getInsutype());
        snap.setMedType(ctx.getMedType());
        snap.setMedMdtrtType(ChsDetailAuditConstants.MED_MDTRT_TYPE_INPATIENT);
        snap.setMedfeeSumamt(ctx.getMedfeeSumamt());
        snap.setAdmDate(formatDt(ctx.getAdmissionTime()));
        snap.setDscgDate(formatDt(ctx.getDischargeTime()));
        snap.setDscgMainDiseCodg(ctx.getMainDiagCode());
        snap.setDscgMainDiseName(ctx.getMainDiagName());
        snap.setDrCodg(ctx.getAtddrNo());
        snap.setDrName(ctx.getChfpdrName());
        snap.setAdmDeptCodg(ctx.getAdmDeptCodg());
        snap.setAdmDeptName(ctx.getAdmDeptName());
        snap.setDscgDeptCodg(ctx.getDscgDeptCodg());
        snap.setDscgDeptName(ctx.getDscgDeptName());
        if (StringUtils.isNotEmpty(ctx.getMainDiagCode())) {
            AuditFeeDiagnoseLine one = new AuditFeeDiagnoseLine();
            one.setDiseId(ctx.getMainDiagCode());
            one.setInoutDiseType("1");
            one.setMaindiseFlag("1");
            one.setDiasSrtNo("1");
            one.setDiseCodg(ctx.getMainDiagCode());
            one.setDiseName(ctx.getMainDiagName());
            one.setDiseDate(formatDt(ctx.getAdmissionTime()));
            snap.getDiagnoses().add(one);
        }
        snap.setOrders(mapIpOrders(ctx));
        return snap;
    }

    /**
     * Snapshot → 3101 Command（单患者单就诊）。
     *
     * @param snap     费用快照
     * @param trigScen 触发场景
     * @param syscode  系统代码
     * @return Command
     */
    public static AuditAnalyze3101Command toAnalyzeCommand(
            AuditFeeSnapshot snap,
            String trigScen,
            String syscode) {
        AuditAnalyze3101Command command = new AuditAnalyze3101Command();
        command.setSyscode(syscode);
        command.setTrigScen(trigScen);
        if (snap == null) {
            return command;
        }
        AuditPatientCommand patient = new AuditPatientCommand();
        patient.setPatnId(snap.getPsnNo());
        patient.setPatnName(snap.getPatnName());
        patient.setGend(snap.getGend());
        patient.setBrdy(snap.getBrdy());
        patient.setPoolarea(snap.getPoolarea());
        patient.setCurrMdtrtId(snap.getMdtrtId());

        AuditEncounterCommand encounter = new AuditEncounterCommand();
        encounter.setMdtrtId(snap.getMdtrtId());
        encounter.setMedinsId(snap.getMedinsId());
        encounter.setMedinsName(snap.getMedinsName());
        encounter.setMedinsAdmdvs(snap.getMedinsAdmdvs());
        encounter.setMedinsType(snap.getMedinsType());
        encounter.setMedinsLv(snap.getMedinsLv());
        encounter.setAdmDate(snap.getAdmDate());
        encounter.setDscgDate(snap.getDscgDate());
        encounter.setDscgMainDiseCodg(snap.getDscgMainDiseCodg());
        encounter.setDscgMainDiseName(snap.getDscgMainDiseName());
        encounter.setDrCodg(snap.getDrCodg());
        encounter.setAdmDeptCodg(snap.getAdmDeptCodg());
        encounter.setAdmDeptName(snap.getAdmDeptName());
        encounter.setDscgDeptCodg(snap.getDscgDeptCodg());
        encounter.setDscgDeptName(snap.getDscgDeptName());
        encounter.setMedMdtrtType(snap.getMedMdtrtType());
        encounter.setMedType(snap.getMedType());
        encounter.setMatnStas(ChsDetailAuditConstants.DEFAULT_MATN_STAS);
        encounter.setMedfeeSumamt(snap.getMedfeeSumamt());
        encounter.setInsutype(snap.getInsutype());
        encounter.setReimFlag(ChsDetailAuditConstants.DEFAULT_REIM_FLAG);
        // Pre：表 116 out_setl_flag 必填；本地就医默认否
        encounter.setOutSetlFlag(ChsDetailAuditConstants.DEFAULT_OUT_SETL_FLAG);
        encounter.setSetlTotlnum(1);
        encounter.setDiagnoses(mapDiagnoseCommands(snap.getDiagnoses()));
        encounter.setOrders(mapOrderCommands(snap.getOrders()));
        encounter.setOperations(new ArrayList<>());

        patient.getEncounters().add(encounter);
        command.getPatients().add(patient);
        return command;
    }

    /**
     * 门诊诊断行 → Snapshot 诊断列表；无 clinicDiagnoses 时用主诊兜底一行。
     *
     * @param ctx 门诊预结算上下文
     * @return 诊断行（可空列表）
     */
    private static List<AuditFeeDiagnoseLine> mapDiagnoses(OpPreSettleCommand ctx) {
        List<AuditFeeDiagnoseLine> list = new ArrayList<>();
        if (ctx.getClinicDiagnoses() == null || ctx.getClinicDiagnoses().isEmpty()) {
            // 无多诊列表：仅主诊码时仍发一行，避免 3101 诊断树全空
            if (StringUtils.isNotEmpty(ctx.getMainDiagCode())) {
                AuditFeeDiagnoseLine one = new AuditFeeDiagnoseLine();
                one.setDiseId(ctx.getMainDiagCode());
                one.setInoutDiseType("1");
                one.setMaindiseFlag("1");
                one.setDiasSrtNo("1");
                one.setDiseCodg(ctx.getMainDiagCode());
                one.setDiseName(ctx.getMainDiagName());
                one.setDiseDate(formatDt(ctx.getEncounterStartTime()));
                list.add(one);
            }
            return list;
        }
        for (OpClinicDiagnosisLine line : ctx.getClinicDiagnoses()) {
            if (line == null) {
                continue;
            }
            AuditFeeDiagnoseLine d = new AuditFeeDiagnoseLine();
            d.setDiseId(line.getDiagCode());
            d.setInoutDiseType(line.getDiagType());
            d.setMaindiseFlag(line.getMaindiagFlag());
            if (line.getDiagSrtNo() != null) {
                d.setDiasSrtNo(String.valueOf(line.getDiagSrtNo()));
            }
            d.setDiseCodg(line.getDiagCode());
            d.setDiseName(line.getDiagName());
            d.setDiseDate(formatDt(ctx.getEncounterStartTime()));
            list.add(d);
        }
        return list;
    }

    /**
     * 门诊费用行 → Snapshot 医嘱/处方行（rxId 优先 feedetlSn）。
     *
     * @param ctx 门诊预结算上下文
     * @return 医嘱行
     */
    private static List<AuditFeeOrderLine> mapOrders(OpPreSettleCommand ctx) {
        List<AuditFeeOrderLine> list = new ArrayList<>();
        if (ctx.getChargeLines() == null) {
            return list;
        }
        for (OpSettleChargeLine line : ctx.getChargeLines()) {
            if (line == null) {
                continue;
            }
            AuditFeeOrderLine o = new AuditFeeOrderLine();
            if (line.getFeedetlSn() != null) {
                o.setRxId(line.getFeedetlSn());
            } else if (line.getChargeItemId() != null) {
                o.setRxId(String.valueOf(line.getChargeItemId()));
            }
            o.setRxno(line.getRxno());
            o.setHilistCode(line.getMedListCodg());
            o.setHosplistCode(line.getMedinsListCodg());
            o.setHilistName(line.getItemName());
            o.setHilistType(line.getHilistType());
            o.setChrgType(line.getChrgType());
            o.setHilistLv(line.getHilistLv());
            o.setSpec(line.getSpec());
            o.setSpecUnt(line.getSpecUnt());
            o.setCnt(line.getCnt());
            o.setPric(line.getPric());
            o.setSumamt(line.getDetItemFeeSumamt());
            o.setDrordDeptCodg(line.getBilgDeptCodg());
            o.setDrordDeptName(line.getBilgDeptName());
            o.setDrordDrCodg(line.getBilgDrCodg());
            o.setDrordDrName(line.getBilgDrName());
            o.setDrordBegnDate(formatDt(line.getFeeOcurTime()));
            list.add(o);
        }
        return list;
    }

    /**
     * 住院费用行 → Snapshot 医嘱行。
     *
     * @param ctx 住院结算上下文
     * @return 医嘱行
     */
    private static List<AuditFeeOrderLine> mapIpOrders(IpSettleContext ctx) {
        List<AuditFeeOrderLine> list = new ArrayList<>();
        if (ctx.getChargeLines() == null) {
            return list;
        }
        for (IpSettleChargeLine line : ctx.getChargeLines()) {
            if (line == null) {
                continue;
            }
            AuditFeeOrderLine o = new AuditFeeOrderLine();
            if (line.getFeedetlSn() != null) {
                o.setRxId(line.getFeedetlSn());
            } else if (line.getChargeItemId() != null) {
                o.setRxId(String.valueOf(line.getChargeItemId()));
            }
            o.setRxno(line.getDrordNo());
            o.setHilistCode(line.getMedListCodg());
            o.setHosplistCode(line.getMedinsListCodg());
            o.setCnt(line.getCnt());
            o.setPric(line.getPric());
            o.setSumamt(line.getDetItemFeeSumamt());
            o.setDrordDeptCodg(line.getBilgDeptCodg());
            o.setDrordDeptName(line.getBilgDeptName());
            o.setDrordDrCodg(line.getBilgDrCodg());
            o.setDrordDrName(line.getBilgDrName());
            o.setDrordBegnDate(formatDt(line.getFeeOcurTime()));
            list.add(o);
        }
        return list;
    }

    /**
     * Snapshot 诊断行 → 3101 Command 诊断节点。
     *
     * @param lines Snapshot 诊断
     * @return Command 诊断
     */
    private static List<AuditDiagnoseCommand> mapDiagnoseCommands(List<AuditFeeDiagnoseLine> lines) {
        List<AuditDiagnoseCommand> list = new ArrayList<>();
        if (lines == null) {
            return list;
        }
        for (AuditFeeDiagnoseLine line : lines) {
            if (line == null) {
                continue;
            }
            AuditDiagnoseCommand d = new AuditDiagnoseCommand();
            d.setDiseId(line.getDiseId());
            d.setInoutDiseType(line.getInoutDiseType());
            d.setMaindiseFlag(line.getMaindiseFlag());
            d.setDiasSrtNo(line.getDiasSrtNo());
            d.setDiseCodg(line.getDiseCodg());
            d.setDiseName(line.getDiseName());
            d.setDiseDate(line.getDiseDate());
            list.add(d);
        }
        return list;
    }

    /**
     * Snapshot 医嘱行 → 3101 Command 医嘱节点（补默认长嘱/行为/当前医嘱标志）。
     *
     * @param lines Snapshot 医嘱
     * @return Command 医嘱
     */
    private static List<AuditOrderCommand> mapOrderCommands(List<AuditFeeOrderLine> lines) {
        List<AuditOrderCommand> list = new ArrayList<>();
        if (lines == null) {
            return list;
        }
        for (AuditFeeOrderLine line : lines) {
            if (line == null) {
                continue;
            }
            AuditOrderCommand o = new AuditOrderCommand();
            o.setRxId(line.getRxId());
            // Pre：表 118 rxno 必填；无电子处方时用 rx_id 回填，禁止空串
            if (StringUtils.isNotEmpty(line.getRxno())) {
                o.setRxno(line.getRxno());
            } else {
                o.setRxno(line.getRxId());
            }
            o.setLongDrordFlag(ChsDetailAuditConstants.DEFAULT_LONG_DRORD_FLAG);
            o.setHilistType(firstNonEmpty(line.getHilistType(), ChsDetailAuditConstants.HILIST_TYPE_WESTERN_DRUG));
            o.setChrgType(firstNonEmpty(line.getChrgType(), ChsDetailAuditConstants.DEFAULT_CHRG_TYPE));
            o.setDrordBhvr(ChsDetailAuditConstants.DEFAULT_DRORD_BHVR);
            o.setHilistCode(line.getHilistCode());
            o.setHilistName(firstNonEmpty(line.getHilistName(), ChsDetailAuditConstants.DEFAULT_SPEC));
            o.setHilistLv(firstNonEmpty(line.getHilistLv(), ChsDetailAuditConstants.DEFAULT_HILIST_LV));
            // Pre：目录价缺省用行单价，禁止空
            if (line.getPric() != null) {
                o.setHilistPric(line.getPric());
            } else {
                o.setHilistPric(java.math.BigDecimal.ZERO);
            }
            o.setHosplistCode(firstNonEmpty(line.getHosplistCode(), line.getHilistCode()));
            o.setHosplistName(firstNonEmpty(line.getHilistName(), ChsDetailAuditConstants.DEFAULT_SPEC));
            o.setCnt(line.getCnt());
            o.setPric(line.getPric());
            o.setSumamt(line.getSumamt());
            o.setSpec(firstNonEmpty(line.getSpec(), ChsDetailAuditConstants.DEFAULT_SPEC));
            o.setSpecUnt(firstNonEmpty(line.getSpecUnt(), ChsDetailAuditConstants.DEFAULT_SPEC_UNT));
            o.setDrordBegnDate(line.getDrordBegnDate());
            o.setDrordDeptCodg(line.getDrordDeptCodg());
            o.setDrordDeptName(line.getDrordDeptName());
            o.setDrordDrCodg(line.getDrordDrCodg());
            o.setDrordDrName(line.getDrordDrName());
            o.setDrordDrProfttl(ChsDetailAuditConstants.DEFAULT_DRORD_DR_PROFTTL);
            o.setCurrDrordFlag(ChsDetailAuditConstants.DEFAULT_CURR_DRORD_FLAG);
            list.add(o);
        }
        return list;
    }

    /**
     * 本地时间 → 平台日期时间串（{@link ChsDetailAuditConstants#DATE_TIME_PATTERN}）；空则 null。
     *
     * @param time 本地时间
     * @return 格式化串或 null
     */
    private static String formatDt(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return DT.format(time);
    }

    /**
     * HIS 行政性别码 → 医保字典 {@code GEND}（长度 1～3）。
     *
     * <p>HIS 存 {@code MALE}/{@code FEMALE}/{@code UNKNOWN}；平台样例与国标为 {@code 1}/{@code 2}/{@code 0}。
     * 若入参已是短数字码（≤3）则原样返回。</p>
     *
     * @param hisGender Encounter/患者 {@code genderEnum}
     * @return 平台 gend；无法识别则 null
     */
    private static String mapHisGenderToPlatformGend(String hisGender) {
        if (StringUtils.isEmpty(hisGender)) {
            return null;
        }
        String trimmed = hisGender.trim();
        // 已是平台短码（如读卡回写的 1/2）
        if (trimmed.length() <= 3 && Character.isDigit(trimmed.charAt(0))) {
            return trimmed;
        }
        if (ChsDetailAuditConstants.HIS_GENDER_MALE.equalsIgnoreCase(trimmed)) {
            return ChsDetailAuditConstants.GEND_MALE;
        }
        if (ChsDetailAuditConstants.HIS_GENDER_FEMALE.equalsIgnoreCase(trimmed)) {
            return ChsDetailAuditConstants.GEND_FEMALE;
        }
        if (ChsDetailAuditConstants.HIS_GENDER_UNKNOWN.equalsIgnoreCase(trimmed)) {
            return ChsDetailAuditConstants.GEND_UNKNOWN;
        }
        return null;
    }

    /**
     * 非空优先，否则回落。
     *
     * @param primary  优先值
     * @param fallback 回落值
     * @return 非空串
     */
    private static String firstNonEmpty(String primary, String fallback) {
        if (StringUtils.isNotEmpty(primary)) {
            return primary.trim();
        }
        return fallback;
    }
}
