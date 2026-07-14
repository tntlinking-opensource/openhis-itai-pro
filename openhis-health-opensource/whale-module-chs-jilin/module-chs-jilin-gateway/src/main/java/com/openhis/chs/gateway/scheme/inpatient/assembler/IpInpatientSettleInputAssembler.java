/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import com.openhis.chs.gateway.scheme.common.model.ChsFeeDetailLine;
import com.openhis.chs.gateway.scheme.common.model.ChsRevokeData;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.common.model.ChsSettleData;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2302FeeRevokeRow;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2302Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2304Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2305Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401MdtrtInfo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2402DscgInfo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2402Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2403Adminfo;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2403Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2404Input;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.domain.share.enums.AcctUsedFlag;
import com.openhis.chs.domain.share.enums.DscgTkdrugFlag;
import com.openhis.chs.domain.share.enums.DscgWay;
import com.openhis.chs.domain.share.enums.HospApprFlag;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.domain.share.enums.MidSetlFlag;
import com.openhis.chs.domain.share.enums.PsnSetlway;
import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 住院 2401/2301/2402/2303/2304/2305 2404/2405 平台报文组装 */
public final class IpInpatientSettleInputAssembler {

    private static final DateTimeFormatter FEE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DSCG_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private IpInpatientSettleInputAssembler() {
    }

    /** 2401：mdtrtinfo + diseinfo */
    public static Ip2401Input assemble2401(IpSettleContext ctx) {
        Ip2401MdtrtInfo mdtrtinfo = new Ip2401MdtrtInfo()
            .setPsnNo(ctx.getPsnNo())
            .setInsutype(ctx.getInsutype())
            .setBegntime(formatTime(ctx.getAdmissionTime()))
            .setMdtrtCertType(ctx.getMdtrtCertType())
            .setMdtrtCertNo(ctx.getMdtrtCertNo())
            .setMedType(resolveMedType(ctx))
            .setIptNo(ctx.getEncounterBusNo())
            .setAtddrNo(blankToDash(ctx.getAtddrNo()))
            .setChfpdrName(blankToDash(ctx.getChfpdrName()))
            .setAdmDiagDscr(blankToDash(ctx.getAdmDiagDscr()))
            .setAdmDeptCodg(blankToDash(ctx.getAdmDeptCodg()))
            .setAdmDeptName(blankToDash(ctx.getAdmDeptName()))
            .setAdmBed(blankToDash(ctx.getAdmBed()))
            .setDscgMaindiagCode(blankToDash(ctx.getMainDiagCode()))
            .setDscgMaindiagName(blankToDash(ctx.getMainDiagName()))
            .setInsuplcAdmdvs(ctx.getInsuplcAdmdvs())
            .setMdtrtareaAdmvs(ctx.getMdtrtareaAdmvs());
        return new Ip2401Input()
            .setMdtrtinfo(mdtrtinfo)
            .setDiseinfo(List.of(buildDiseRow(ctx, "1", "1")));
    }

    /** 2301：feedetail */
    public static Ip2301Input assemble2301(IpSettleContext ctx, String mdtrtId, List<IpSettleChargeLine> lines) {
        List<ChsFeeDetailLine> feedetail = new ArrayList<>(lines.size());
        for (IpSettleChargeLine line : lines) {
            ChsFeeDetailLine row = new ChsFeeDetailLine()
                .setFeedetlSn(line.getFeedetlSn())
                .setMdtrtId(mdtrtId)
                .setPsnNo(ctx.getPsnNo())
                .setMedType(resolveMedType(ctx))
                .setFeeOcurTime(line.getFeeOcurTime().format(FEE_TIME))
                .setMedListCodg(line.getMedListCodg())
                .setMedinsListCodg(line.getMedinsListCodg())
                .setDetItemFeeSumamt(line.getDetItemFeeSumamt())
                .setCnt(line.getCnt())
                .setPric(line.getPric())
                .setBilgDeptCodg(blankToDash(line.getBilgDeptCodg()))
                .setBilgDeptName(blankToDash(line.getBilgDeptName()))
                .setBilgDrCodg(blankToDash(line.getBilgDrCodg()))
                .setBilgDrName(blankToDash(line.getBilgDrName()))
                .setHospApprFlag(HospApprFlag.APPROVED.getCode())
                .setDscgTkdrugFlag(DscgTkdrugFlag.NO.getCode());
            if (isNotEmpty(line.getDrordNo())) {
                row.setDrordNo(line.getDrordNo());
            }
            feedetail.add(row);
        }
        return new Ip2301Input().setFeedetail(feedetail);
    }

    /** 2402：dscginfo + diseinfo */
    public static Ip2402Input assemble2402(IpSettleContext ctx, String mdtrtId) {
        Ip2402DscgInfo dscginfo = new Ip2402DscgInfo()
            .setMdtrtId(mdtrtId)
            .setPsnNo(ctx.getPsnNo())
            .setInsutype(ctx.getInsutype())
            .setEndtime(formatTime(resolveDischargeTime(ctx)))
            .setDscgDeptCodg(blankToDash(ctx.getDscgDeptCodg()))
            .setDscgDeptName(blankToDash(ctx.getDscgDeptName()))
            .setDscgBed(blankToDash(ctx.getDscgBed()))
            .setDscgWay(resolveDscgWay(ctx));
        return new Ip2402Input()
            .setDscginfo(dscginfo)
            .setDiseinfo(List.of(buildDiseRow(ctx, "2", "1")));
    }

    /** 2303 input.data */
    public static Ip2303Input assemble2303(IpSettleContext ctx, String mdtrtId, BigDecimal medfeeSumamt) {
        ChsSettleData data = baseSettleData(ctx, mdtrtId, medfeeSumamt);
        data.setMidSetlFlag(resolveMidSetlFlag(ctx));
        if (IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(ctx.getSettleMode())) {
            data.setDscgTime(formatDscgDate(resolveDischargeTime(ctx)));
        } else {
            data.setDscgTime(formatDscgDate(resolveBatchEndDate(ctx)));
        }
        return new Ip2303Input().setData(data);
    }

    /** 2304 input.data）303 setlinfo 透传） */
    public static Ip2304Input assemble2304(
        IpSettleContext ctx,
        String mdtrtId,
        BigDecimal medfeeSumamt,
        ChsSetlinfoOutput setlinfo) {
        ChsSettleData data = baseSettleData(ctx, mdtrtId, medfeeSumamt);
        data.setMidSetlFlag(resolveMidSetlFlag(ctx));
        if (setlinfo != null) {
            data.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
            data.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
            data.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
            data.setInscpScpAmt(setlinfo.getInscpScpAmt());
        }
        if (isNotEmpty(ctx.getInvono())) {
            data.setInvono(ctx.getInvono().trim());
        }
        data.setMinpacuntDrugTracCnt(resolveTraceCount(ctx.getMinpacuntDrugTracCnt()));
        data.setMcsTracCnt(resolveTraceCount(ctx.getMcsTracCnt()));
        return new Ip2304Input().setData(data);
    }

    /** 2302：data 多行费用明细撤销 */
    public static Ip2302Input assemble2302(String mdtrtId, String psnNo, List<String> feedetlSnList) {
        List<Ip2302FeeRevokeRow> rows = new ArrayList<>(feedetlSnList.size());
        for (String feedetlSn : feedetlSnList) {
            rows.add(new Ip2302FeeRevokeRow()
                .setFeedetlSn(feedetlSn)
                .setMdtrtId(mdtrtId)
                .setPsnNo(psnNo));
        }
        Ip2302Input input = new Ip2302Input();
        input.setData(rows);
        return input;
    }

    /** 2305：data 单行结算撤销 */
    public static Ip2305Input assemble2305(String mdtrtId, String setlId, String psnNo) {
        ChsRevokeData data = new ChsRevokeData()
            .setMdtrtId(mdtrtId)
            .setSetlId(setlId)
            .setPsnNo(psnNo);
        return new Ip2305Input().setData(data);
    }

    /** 2403：adminfo + diseinfo（住院信息变更） */
    public static Ip2403Input assemble2403(IpSettleContext ctx, String mdtrtId) {
        Ip2403Adminfo adminfo = new Ip2403Adminfo()
            .setMdtrtId(mdtrtId)
            .setPsnNo(ctx.getPsnNo())
            .setBegntime(formatTime(ctx.getAdmissionTime()))
            .setMdtrtCertType(ctx.getMdtrtCertType())
            .setMedType(resolveMedType(ctx))
            .setIptOtpNo(ctx.getEncounterBusNo())
            .setAtddrNo(blankToDash(ctx.getAtddrNo()))
            .setChfpdrName(blankToDash(ctx.getChfpdrName()))
            .setAdmDiagDscr(blankToDash(ctx.getAdmDiagDscr()))
            .setAdmDeptCodg(blankToDash(ctx.getAdmDeptCodg()))
            .setAdmDeptName(blankToDash(ctx.getAdmDeptName()))
            .setAdmBed(blankToDash(ctx.getAdmBed()))
            .setDscgMaindiagCode(blankToDash(ctx.getMainDiagCode()))
            .setDscgMaindiagName(blankToDash(ctx.getMainDiagName()));
        ChsDiseInfoRow dise = buildDiseRow(ctx, "1", "1");
        dise.setMdtrtId(mdtrtId);
        return new Ip2403Input()
            .setAdminfo(adminfo)
            .setDiseinfo(List.of(dise));
    }

    /** 2404/2405：data 单行入出院撤销 */
    public static Ip2404Input assemble2404Or2405(String mdtrtId, String psnNo) {
        ChsRevokeData data = new ChsRevokeData()
            .setMdtrtId(mdtrtId)
            .setPsnNo(psnNo);
        return new Ip2404Input().setData(data);
    }

    public static String newSettlementRef(ContractConfig config, long seed) {
        String prefix = "IP";
        if (config != null && isNotEmpty(config.getFixmedinsCode())) {
            String code = config.getFixmedinsCode().trim();
            if (code.length() > 6) {
                prefix = code.substring(0, 6);
            } else {
                prefix = code;
            }
        }
        String suffix = String.valueOf(Math.abs(seed) % 1_000_000L);
        while (suffix.length() < 6) {
            suffix = "0" + suffix;
        }
        return prefix + suffix;
    }

    private static ChsSettleData baseSettleData(IpSettleContext ctx, String mdtrtId, BigDecimal medfeeSumamt) {
        return new ChsSettleData()
            .setPsnNo(ctx.getPsnNo())
            .setMdtrtCertType(ctx.getMdtrtCertType())
            .setMdtrtCertNo(ctx.getMdtrtCertNo())
            .setMedfeeSumamt(medfeeSumamt)
            .setPsnSetlway(resolvePsnSetlway(ctx))
            .setMdtrtId(mdtrtId)
            .setInsutype(ctx.getInsutype())
            .setAcctUsedFlag(resolveAcctUsedFlag(ctx))
            .setInsuplcAdmdvs(ctx.getInsuplcAdmdvs())
            .setMdtrtareaAdmvs(ctx.getMdtrtareaAdmvs());
    }

    private static ChsDiseInfoRow buildDiseRow(IpSettleContext ctx, String diagType, String mainFlag) {
        return new ChsDiseInfoRow()
            .setMdtrtId("")
            .setPsnNo(ctx.getPsnNo())
            .setDiagType(diagType)
            .setMaindiagFlag(mainFlag)
            .setDiagSrtNo("1")
            .setDiagCode(blankToDash(ctx.getMainDiagCode()))
            .setDiagName(blankToDash(ctx.getMainDiagName()))
            .setDiagDept(blankToDash(ctx.getAdmDeptName()))
            .setDiseDorNo(blankToDash(ctx.getAtddrNo()))
            .setDiseDorName(blankToDash(ctx.getChfpdrName()))
            .setDiagTime(formatTime(ctx.getAdmissionTime()));
    }

    private static String resolveMidSetlFlag(IpSettleContext ctx) {
        if (IpSettleContext.SETTLE_MODE_MID.equals(ctx.getSettleMode())) {
            return MidSetlFlag.YES.getCode();
        }
        return MidSetlFlag.NO.getCode();
    }

    private static String resolveMedType(IpSettleContext ctx) {
        if (isNotEmpty(ctx.getMedType())) {
            return ctx.getMedType().trim();
        }
        return MedType.GENERAL_HOSPITALIZATION.getCode();
    }

    /** 解析住院 med_type（默21 普通住院） */
    public static String resolveMedTypeForContext(IpSettleContext ctx) {
        return resolveMedType(ctx);
    }

    private static String resolvePsnSetlway(IpSettleContext ctx) {
        if (isNotEmpty(ctx.getPsnSetlway())) {
            return ctx.getPsnSetlway().trim();
        }
        return PsnSetlway.BY_ITEM.getCode();
    }

    private static String resolveAcctUsedFlag(IpSettleContext ctx) {
        if (isNotEmpty(ctx.getAcctUsedFlag())) {
            return ctx.getAcctUsedFlag().trim();
        }
        return AcctUsedFlag.YES.getCode();
    }

    private static String resolveDscgWay(IpSettleContext ctx) {
        if (isNotEmpty(ctx.getDscgWay())) {
            return ctx.getDscgWay().trim();
        }
        return DscgWay.ORDERED.getCode();
    }

    private static java.time.LocalDateTime resolveDischargeTime(IpSettleContext ctx) {
        if (ctx.getDischargeTime() != null) {
            return ctx.getDischargeTime();
        }
        return java.time.LocalDateTime.now();
    }

    private static java.time.LocalDateTime resolveBatchEndDate(IpSettleContext ctx) {
        java.time.LocalDateTime max = null;
        if (ctx.getChargeLines() != null) {
            for (IpSettleChargeLine line : ctx.getChargeLines()) {
                if (line.getFeeOcurTime() == null) {
                    continue;
                }
                if (max == null || line.getFeeOcurTime().isAfter(max)) {
                    max = line.getFeeOcurTime();
                }
            }
        }
        if (max != null) {
            return max;
        }
        return java.time.LocalDateTime.now();
    }

    private static String formatTime(java.time.LocalDateTime time) {
        if (time == null) {
            return java.time.LocalDateTime.now().format(FEE_TIME);
        }
        return time.format(FEE_TIME);
    }

    private static String formatDscgDate(java.time.LocalDateTime time) {
        LocalDate date;
        if (time == null) {
            date = LocalDate.now();
        } else {
            date = time.toLocalDate();
        }
        return date.format(DSCG_DATE);
    }

    /** 2304/2402 追溯码计数：平台要求非负整数，null/0 0 */
    private static int resolveTraceCount(Integer count) {
        if (count != null && count > 0) {
            return count;
        }
        return 0;
    }

    private static String blankToDash(String value) {
        if (isEmpty(value)) {
            return "-";
        }
        return value.trim();
    }
}
