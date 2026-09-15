/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.weistat.support;

import com.openhis.health.application.mrms.caserecord.homepage.dto.CaseRecordDetailDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepagePageDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import com.openhis.health.application.mrms.upload.weistat.WeistatN041Columns;
import com.openhis.health.application.mrms.upload.weistat.WeistatN041Constants;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 病案首页聚合 → 卫统 N041 一行（232 列 Map）。
 *
 * <p>诊断出站<strong>西医国临/ICD</strong>（中医目录码不进 ZYZD/JBDM）；费用取 {@code homepage.fees}；
 * 科别出站机构 {@code caty}（卫统科别，禁止医保 yb_no）。
 * 对照文档：{@code 11-卫统N041导出-字段对照.md}。</p>
 */
@Component
@RequiredArgsConstructor
public class WeistatN041RowAssembler {

    private final OrganizationQueryService organizationQueryService;

    private final WeistatRegionNameResolver weistatRegionNameResolver;

    /**
     * 组装一行 N041。
     *
     * @param page         loadPage 聚合（可缺 homepage，则多列空）
     * @param hospitalName 机构名称 → USERNAME
     * @return 列名→值；键集覆盖 {@link WeistatN041Columns#ORDERED}
     */
    public Map<String, String> assemble(HomepagePageDto page, String hospitalName) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String col : WeistatN041Columns.ORDERED) {
            row.put(col, "");
        }
        HomepageDto hp = null;
        CaseRecordDetailDto cr = null;
        if (page != null) {
            hp = page.getHomepage();
            cr = page.getCaseRecord();
        }
        row.put("USERNAME", StringUtils.nullToEmpty(hospitalName));
        if (hp == null) {
            return row;
        }
        // —— 身份与付费 ——
        row.put("YLFKFS", WeistatN041Constants.toN041PayWay(hp.getPayWayCode()));
        row.put("JKKH", text(hp.getHealthCardNo()));
        Integer iptCnt = null;
        String medcasno = null;
        if (cr != null) {
            iptCnt = cr.getPatnIptCnt();
            medcasno = cr.getMedcasno();
        }
        row.put("ZYCS", intOrDefault(iptCnt, "1"));
        row.put("BAH", text(medcasno));
        row.put("XM", text(hp.getPsnName()));
        row.put("XB", text(hp.getGendCode()));
        row.put("CSRQ", formatCompactDate(hp.getBrdy()));
        row.put("NL", intText(hp.getAgeYear()));
        row.put("GJ", text(hp.getNtlyCode()));
        row.put("BZYZSNL", intText(hp.getAgeMonth()));
        // XSECSTZ / XSERYTZ：缺口 G1，保持空
        // 出生地/籍贯：地名连续拼接（禁止逗号码串，避免 Excel 拆列成 220,000）
        row.put("CSD", preferPlaceName(hp.getBirplcName(), hp.getBirplcCodes()));
        row.put("GG", preferPlaceName(hp.getNaplName(), hp.getNaplCodes()));
        row.put("MZ", text(hp.getNatyCode()));
        row.put("SFZH", excelTextProtect(upperTailX(hp.getCertno())));
        row.put("ZY", text(hp.getPrfsCode()));
        row.put("HY", text(hp.getMrgStasCode()));
        row.put("XZZ", resolveCurrAddr(hp));
        row.put("DH", excelTextProtect(text(hp.getPsnTel())));
        row.put("YB1", excelTextProtect(text(hp.getCurrAddrPoscode())));
        row.put("HKDZ", resolveResdAddr(hp));
        row.put("YB2", excelTextProtect(text(hp.getResdAddrPoscode())));
        row.put("GZDWJDZ", text(hp.getEmprAddr()));
        row.put("DWDH", excelTextProtect(text(hp.getEmprTel())));
        row.put("YB3", excelTextProtect(text(hp.getEmprPoscode())));
        row.put("LXRXM", text(hp.getConerName()));
        row.put("GX", text(hp.getConerRltsCode()));
        row.put("DZ", text(hp.getConerAddr()));
        row.put("DH2", excelTextProtect(text(hp.getConerTel())));
        // —— 入出院 ——
        row.put("RYTJ", text(hp.getAdmWayCode()));
        row.put("RYSJ", formatCompactDateTime(hp.getAdmTime()));
        row.put("RYSJS", formatHour(hp.getAdmTime()));
        // 科别：卫统 caty（非 4401 医保码）
        row.put("RYKB", organizationQueryService.resolveWeistatCatyCode(hp.getAdmCatyCode()));
        row.put("RYBF", joinWardBed(hp.getAdmWard(), hp.getAdmBed()));
        row.put("ZKKB", text(hp.getTransferDeptSummary()));
        row.put("CYSJ", formatCompactDateTime(hp.getDscgTime()));
        row.put("CYSJS", formatHour(hp.getDscgTime()));
        row.put("CYKB", organizationQueryService.resolveWeistatCatyCode(hp.getDscgCatyCode()));
        row.put("CYBF", joinWardBed(hp.getDscgWard(), hp.getDscgBed()));
        row.put("SJZYTS", intOrDefault(hp.getIptDays(), "1"));
        row.put("MZZD", text(hp.getOtpWmDise()));
        row.put("JBBM", text(hp.getWmDiseCode()));
        // —— 诊断 ——
        fillDiagnoses(row, hp.getDiagnoses());
        row.put("BLH", text(hp.getPathologyNo()));
        row.put("YWGM", text(hp.getDrugAllergyCode()));
        row.put("GMYW", text(hp.getAllergyDrugName()));
        row.put("SWHZSJ", text(hp.getAutopsyCode()));
        row.put("XX", text(hp.getAboCode()));
        row.put("RH", text(hp.getRhCode()));
        // —— 医护 ——
        row.put("KZR", text(hp.getDeptDirectorName()));
        row.put("ZRYS", text(hp.getChfdrName()));
        row.put("ZZYS", text(hp.getAtddrName()));
        row.put("ZYYS", text(hp.getIptDrName()));
        row.put("ZRHS", text(hp.getRespNursName()));
        row.put("JXYS", text(hp.getTrainDrName()));
        row.put("SXYS", text(hp.getIntnDrName()));
        row.put("BMY", text(hp.getCodrName()));
        row.put("BAZL", text(hp.getMedcasQltCode()));
        row.put("ZKYS", text(hp.getQltctrlDrName()));
        row.put("ZKHS", text(hp.getQltctrlNursName()));
        row.put("ZKRQ", formatCompactDate(hp.getQltctrlDate()));
        // —— 手术 ——
        fillProcedures(row, hp.getProcedures());
        // —— 离院 / 再住院 / 昏迷 ——
        row.put("LYFS", text(hp.getDscgWayCode()));
        row.put("YZZY_YLJG", text(hp.getAcpMedinsName()));
        row.put("WSY_YLJG", text(hp.getAcpCommunityName()));
        row.put("SFZZYJH", text(hp.getRinp31Flag()));
        row.put("MD", text(hp.getRinp31Purpose()));
        WeistatComaDurationSplitter.Parts bf = WeistatComaDurationSplitter.split(hp.getBrnDamgBfadmComaDura());
        row.put("RYQ_T", bf.days());
        row.put("RYQ_XS", bf.hours());
        row.put("RYQ_F", bf.minutes());
        WeistatComaDurationSplitter.Parts af = WeistatComaDurationSplitter.split(hp.getBrnDamgAfadmComaDura());
        row.put("RYH_T", af.days());
        row.put("RYH_XS", af.hours());
        row.put("RYH_F", af.minutes());
        // —— 费用 ——
        fillFees(row, hp.getFees());
        return row;
    }

    /**
     * 填充主诊/其他诊/病理/损伤（N041 仅西医国临/ICD；中医目录码不占主诊）。
     *
     * @param row        行
     * @param diagnoses  诊断行
     */
    private void fillDiagnoses(Map<String, String> row, List<DiagnosisLineDto> diagnoses) {
        List<DiagnosisLineDto> list = List.of();
        if (diagnoses != null) {
            list = diagnoses;
        }
        DiagnosisLineDto typedMain = firstByType(list, HomepageDiagType.DISCHARGE_MAIN.getCode());
        List<DiagnosisLineDto> others = listByTypeSorted(list, HomepageDiagType.DISCHARGE_OTHER.getCode());
        // N041 主诊须西医疾病分类码：若 discharge_main 是中医目录（如 A04.01.01），从其他诊提升首条西医
        DiagnosisLineDto westernMain = resolveWesternMain(typedMain, others);
        if (westernMain != null) {
            row.put("ZYZD", text(westernMain.getDiagName()));
            row.put("JBDM", text(westernMain.getDiagCode()));
            String admCond = text(westernMain.getAdmCondCode());
            if (admCond.isEmpty()) {
                admCond = "1";
            }
            row.put("RYBQ", admCond);
            // 若主诊是从其他诊提升的：去掉重复西医行；原中医主诊改入其他诊，避免「缺东西」
            if (typedMain != null && westernMain != typedMain) {
                others = withoutSameDiag(others, westernMain);
                if (!isLikelyWesternIcd(typedMain.getDiagCode())) {
                    List<DiagnosisLineDto> withFormerMain = new ArrayList<>();
                    withFormerMain.add(typedMain);
                    withFormerMain.addAll(others);
                    others = withFormerMain;
                }
            } else if (typedMain == null && westernMain != null) {
                others = withoutSameDiag(others, westernMain);
            }
        }
        // 其他诊：仍按首页顺序写槽；中医行也会写出（有码有名不丢），但主诊位已保证西医
        for (int slot : WeistatN041Columns.OTHER_DIAG_SLOT_ORDER) {
            DiagnosisLineDto line = null;
            if (slot <= others.size()) {
                line = others.get(slot - 1);
            }
            if (line == null) {
                row.put("QTZD" + slot, "");
                row.put("JBDM" + slot, "");
                row.put("RYBQ" + slot, "");
            } else {
                row.put("QTZD" + slot, text(line.getDiagName()));
                row.put("JBDM" + slot, text(line.getDiagCode()));
                row.put("RYBQ" + slot, text(line.getAdmCondCode()));
            }
        }
        DiagnosisLineDto injury = firstByType(list, HomepageDiagType.INJURY_POISON.getCode());
        if (injury != null) {
            row.put("WBYY", text(injury.getDiagName()));
            row.put("H23", text(injury.getDiagCode()));
        }
        DiagnosisLineDto pathology = firstByType(list, HomepageDiagType.PATHOLOGY.getCode());
        if (pathology != null) {
            row.put("BLZD", text(pathology.getDiagName()));
            row.put("JBMM", text(pathology.getDiagCode()));
        }
    }

    /**
     * 选定 N041 西医主诊：优先已是西医的 discharge_main，否则取其他诊中首条西医。
     *
     * @param typedMain 首页出院主诊（可能为中医）
     * @param others    出院其他诊（已按 sortNo 排序）
     * @return 西医主诊行；皆无西医码则回落 typedMain（避免整列空）
     */
    private DiagnosisLineDto resolveWesternMain(DiagnosisLineDto typedMain, List<DiagnosisLineDto> others) {
        if (typedMain != null && isLikelyWesternIcd(typedMain.getDiagCode())) {
            return typedMain;
        }
        for (DiagnosisLineDto other : others) {
            if (other != null && isLikelyWesternIcd(other.getDiagCode())) {
                return other;
            }
        }
        // 无西医可提升时仍出首页主诊，避免「有诊断却整列空」被当成漏导
        return typedMain;
    }

    /**
     * 从其他诊列表去掉与主诊同一条（按码+名），防止主诊提升后重复占 QTZD。
     *
     * @param others 其他诊
     * @param main   已用作主诊的行
     * @return 新列表
     */
    private List<DiagnosisLineDto> withoutSameDiag(List<DiagnosisLineDto> others, DiagnosisLineDto main) {
        List<DiagnosisLineDto> filtered = new ArrayList<>();
        String mainCode = text(main.getDiagCode());
        String mainName = text(main.getDiagName());
        for (DiagnosisLineDto line : others) {
            if (line == null) {
                continue;
            }
            // 同一对象或同码同名视为重复
            if (line == main) {
                continue;
            }
            if (mainCode.equals(text(line.getDiagCode())) && mainName.equals(text(line.getDiagName()))) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    /**
     * 是否像西医 ICD/国临码（排除中医三节目录码 {@code A04.01.01}）。
     *
     * @param diagCode 诊断码
     * @return true 可作 N041 JBDM
     */
    private boolean isLikelyWesternIcd(String diagCode) {
        String code = StringUtils.trimToNull(diagCode);
        if (code == null) {
            return false;
        }
        // 中医病证分类常见：字母+2位+.+2位+.+2位
        if (code.matches("^[A-Za-z]\\d{2}\\.\\d{2}\\.\\d{2}$")) {
            return false;
        }
        // ICD-10 / 国临：字母+两位起，可带小数与 x 扩展（如 I25.103、I50.900x007）
        return code.matches("^[A-TV-Z][0-9][0-9A-Za-z](\\.[0-9A-Za-z]+)?([xX][0-9A-Za-z]+)?$");
    }

    /**
     * 填充手术 1～7 槽；I助/II助本期空（缺口 G2）。
     *
     * @param row         行
     * @param procedures  手术行
     */
    private void fillProcedures(Map<String, String> row, List<ProcedureLineDto> procedures) {
        List<ProcedureLineDto> sorted = new ArrayList<>();
        if (procedures != null) {
            sorted.addAll(procedures);
        }
        sorted.sort(Comparator.comparingInt(p -> {
            if (p.getSortNo() == null) {
                return Integer.MAX_VALUE;
            }
            return p.getSortNo();
        }));
        int limit = Math.min(WeistatN041Columns.MAX_PROCEDURE_SLOTS, sorted.size());
        for (int i = 0; i < limit; i++) {
            ProcedureLineDto p = sorted.get(i);
            int n = i + 1;
            row.put("SSJCZBM" + n, text(p.getOprnOprtCode()));
            row.put("SSJCZRQ" + n, formatCompactDate(p.getOprnDate()));
            row.put("SSJB" + n, text(p.getOprnLevelCode()));
            row.put("SSJCZMC" + n, text(p.getOprnOprtName()));
            row.put("SZ" + n, text(p.getOperDrName()));
            // YZ / EZ：无字段，保持空
            row.put("QKDJ" + n, text(p.getOprnIncisionCode()));
            row.put("QKYHLB" + n, text(p.getHealGradeCode()));
            row.put("MZFS" + n, text(p.getAnstWayCode()));
            row.put("MZYS" + n, text(p.getAnstDrName()));
        }
    }

    /**
     * 费用分项 → ZFY…QTF。
     *
     * @param row  行
     * @param fees 首页 fees Map
     */
    private void fillFees(Map<String, String> row, Map<String, BigDecimal> fees) {
        Map<String, BigDecimal> map = Map.of();
        if (fees != null) {
            map = fees;
        }
        row.put("ZFY", money(map.get(MrmsConstants.EXT_MEDFEE_SUMAMT)));
        row.put("ZFJE", money(map.get(MrmsConstants.EXT_SELFPAY_AMT)));
        row.put("YLFUF", money(map.get("ordn_med_servfee")));
        row.put("ZLCZF", money(map.get("ordn_trt_oprt_fee")));
        row.put("HLF", money(map.get("nurs_fee")));
        row.put("QTFY", money(map.get("com_med_serv_oth_fee")));
        row.put("BLZDF", money(map.get("palg_diag_fee")));
        row.put("SYSZDF", money(map.get("lab_diag_fee")));
        row.put("YXXZDF", money(map.get("rdhy_diag_fee")));
        row.put("LCZDXMF", money(map.get("clnc_dise_fee")));
        row.put("FSSZLXMF", money(map.get("nsrgtrt_item_fee")));
        row.put("WLZLF", money(map.get("clnc_phys_trt_fee")));
        row.put("SSZLF", money(map.get("rgtrt_trt_fee")));
        row.put("MAF", money(map.get("anst_fee")));
        row.put("SSF", money(map.get("rgtrt_fee")));
        row.put("KFF", money(map.get("rhab_fee")));
        row.put("ZYZLF", money(map.get("tcm_trt_fee")));
        row.put("XYF", money(map.get("wm_fee")));
        row.put("KJYWF", money(map.get("abtl_medn_fee")));
        row.put("ZCYF", money(map.get("tcmpat_fee")));
        row.put("ZCYF1", money(map.get("tcmherb_fee")));
        row.put("XF", money(map.get("blo_fee")));
        row.put("BDBLZPF", money(map.get("albu_fee")));
        row.put("QDBLZPF", money(map.get("glon_fee")));
        row.put("NXYZLZPF", money(map.get("clotfac_fee")));
        row.put("XBYZLZPF", money(map.get("cyki_fee")));
        row.put("HCYYCLF", money(map.get("exam_dspo_matl_fee")));
        row.put("YYCLF", money(map.get("trt_dspo_matl_fee")));
        row.put("YCXYYCLF", money(map.get("oprn_dspo_matl_fee")));
        row.put("QTF", money(map.get(MrmsConstants.FEE_KEY_OTH)));
    }

    /**
     * 取指定类型第一条诊断。
     *
     * @param list 诊断列表
     * @param type 类型码
     * @return 行或 null
     */
    private DiagnosisLineDto firstByType(List<DiagnosisLineDto> list, String type) {
        for (DiagnosisLineDto d : list) {
            if (d != null && type.equals(d.getDiagTypeCode())) {
                return d;
            }
        }
        return null;
    }

    /**
     * 按类型过滤并按 sortNo 升序。
     *
     * @param list 诊断列表
     * @param type 类型码
     * @return 排序后列表
     */
    private List<DiagnosisLineDto> listByTypeSorted(List<DiagnosisLineDto> list, String type) {
        List<DiagnosisLineDto> out = new ArrayList<>();
        for (DiagnosisLineDto d : list) {
            if (d != null && type.equals(d.getDiagTypeCode())) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt(d -> {
            if (d.getSortNo() == null) {
                return Integer.MAX_VALUE;
            }
            return d.getSortNo();
        }));
        return out;
    }

    /**
     * 现住址：优先全文；否则区划地名 + 详址（无 ASCII 逗号）。
     *
     * @param hp 首页
     * @return 地址文本
     */
    private String resolveCurrAddr(HomepageDto hp) {
        String full = StringUtils.trimToNull(hp.getCurrAddr());
        if (full != null) {
            return full;
        }
        return joinNonBlank(
                weistatRegionNameResolver.toPlaceText(hp.getCurrAddrCodes()),
                text(hp.getCurrAddrDetail()));
    }

    /**
     * 优先中文地名列；空则回落 codes 的 {@code /} 拼接（禁止逗号）。
     *
     * @param placeName 地名列
     * @param codes     区划码
     * @return 出站文本
     */
    private String preferPlaceName(String placeName, List<String> codes) {
        String name = text(placeName);
        // 有中文地名列则直接出站
        if (!name.isEmpty()) {
            return name;
        }
        // 名列空：回落 codes 斜杠拼接（非逗号）
        return weistatRegionNameResolver.toPlaceText(codes);
    }

    /**
     * 户口地址：区划地名 + 详址。
     *
     * @param hp 首页
     * @return 地址文本
     */
    private String resolveResdAddr(HomepageDto hp) {
        return joinNonBlank(
                weistatRegionNameResolver.toPlaceText(hp.getResdAddrCodes()),
                text(hp.getResdAddrDetail()));
    }

    /**
     * 病区+床位拼接。
     *
     * @param ward 病区
     * @param bed  床位
     * @return 拼接文本
     */
    private String joinWardBed(String ward, String bed) {
        return joinNonBlank(text(ward), text(bed));
    }

    /**
     * Excel 打开 CSV 时防止长数字变科学计数：前置 TAB（直报工具一般 trim 后仍可读）。
     *
     * <p>仅对「纯数字或尾号 X 的证件/电话/邮编」生效；短码不动。</p>
     *
     * @param raw 原值
     * @return 保护后的文本
     */
    private String excelTextProtect(String raw) {
        String t = text(raw);
        if (t.isEmpty()) {
            return t;
        }
        // 11 位及以上数字，或 15/18 位身份证（可含末位 X）
        boolean longDigits = t.matches("\\d{11,}");
        boolean idCard = t.matches("\\d{15}") || t.matches("\\d{17}[0-9Xx]");
        if (!longDigits && !idCard) {
            return t;
        }
        if (t.charAt(0) == '\t') {
            return t;
        }
        return "\t" + t;
    }

    /**
     * 非空片段空格拼接。
     *
     * @param parts 片段
     * @return 文本
     */
    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.trim());
        }
        return sb.toString();
    }

    /**
     * 身份证尾号 x 大写。
     *
     * @param certno 证件号
     * @return 文本
     */
    private String upperTailX(String certno) {
        String t = text(certno);
        if (t.isEmpty()) {
            return t;
        }
        if (t.endsWith("x")) {
            return t.substring(0, t.length() - 1) + "X";
        }
        return t;
    }

    /**
     * 金额结算刻度明文；空→空串（总费用调用方可再默认）。
     *
     * @param amount 金额
     * @return 文本
     */
    private String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return MonetaryAmountSupport.scaleSettle(amount).toPlainString();
    }

    /**
     * LocalDate → yyyyMMdd。
     *
     * @param date 日期
     * @return 文本
     */
    private String formatCompactDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_COMPACT_FORMATTER.format(date);
    }

    /**
     * LocalDateTime 日期部分 → yyyyMMdd。
     *
     * @param time 时间
     * @return 文本
     */
    private String formatCompactDateTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_COMPACT_FORMATTER.format(time.toLocalDate());
    }

    /**
     * 取小时（0–23）文本。
     *
     * @param time 时间
     * @return 文本
     */
    private String formatHour(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return String.valueOf(time.getHour());
    }

    /**
     * Integer 文本；空用默认。
     *
     * @param value        值
     * @param defaultValue 默认
     * @return 文本
     */
    private String intOrDefault(Integer value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    /**
     * Integer 文本；空→空串。
     *
     * @param value 值
     * @return 文本
     */
    private String intText(Integer value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    /**
     * trim 后空→空串。
     *
     * @param value 原值
     * @return 文本
     */
    private String text(String value) {
        String t = StringUtils.trimToNull(value);
        if (t == null) {
            return "";
        }
        return t;
    }
}
