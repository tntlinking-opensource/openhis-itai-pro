/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.hqms.support;

import com.zxplat.application.mrms.caserecord.homepage.dto.CaseRecordDetailDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.DiagnosisLineDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.HomepageDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.HomepagePageDto;
import com.zxplat.application.mrms.caserecord.homepage.dto.ProcedureLineDto;
import com.zxplat.application.mrms.upload.hqms.HqmsColumns;
import com.zxplat.application.mrms.upload.hqms.HqmsConstants;
import com.zxplat.application.mrms.upload.weistat.support.WeistatComaDurationSplitter;
import com.zxplat.application.mrms.upload.weistat.support.WeistatRegionNameResolver;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.MrmsConstants;
import com.zxplat.domain.share.enums.HomepageDiagType;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 病案首页聚合 → HQMS 一行（811 列 Map）。
 *
 * <p>与卫统列 mapper 独立；诊断出站西医国临/ICD；手术操作码出站首页
 * {@code oprnOprtCode}（国临）。本管道不出医保手术码；4401/4101A 才读同一条 PD 的 {@code yb_no}。
 * 禁止用诊疗目录费用 {@code yb_no}；
 * I助/II助取首页 {@code asit1Name}/{@code asit2Name}，空写 {@code -}。
 * 科别出站机构 {@code caty}；部分必填空位写 {@code -}（对齐国基 HQMS）。
 * 文档：12-HQMS导出-字段对照.md。</p>
 */
@Component
@RequiredArgsConstructor
public class HqmsRowAssembler {

    private final OrganizationQueryService organizationQueryService;

    private final WeistatRegionNameResolver weistatRegionNameResolver;

    /**
     * 组装一行 HQMS。
     *
     * @param page  loadPage 聚合
     * @param org   机构上下文（A01/A02）
     * @return 列名→值；键集覆盖 {@link HqmsColumns#ORDERED}
     */
    public Map<String, String> assemble(HomepagePageDto page, HqmsOrgContext org) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String col : HqmsColumns.ORDERED) {
            row.put(col, "");
        }
        HomepageDto hp = null;
        CaseRecordDetailDto cr = null;
        if (page != null) {
            hp = page.getHomepage();
            cr = page.getCaseRecord();
        }
        String orgCode = HqmsConstants.DASH;
        String orgName = "";
        if (org != null) {
            if (org.orgCode() != null && !org.orgCode().isBlank()) {
                orgCode = org.orgCode();
            }
            if (org.orgName() != null) {
                orgName = org.orgName();
            }
        }
        row.put("A01", orgCode);
        row.put("A02", orgName);
        if (hp == null) {
            return row;
        }
        // —— 身份与就诊 ——
        String medcasno = null;
        Integer iptCnt = null;
        if (cr != null) {
            medcasno = cr.getMedcasno();
            iptCnt = cr.getPatnIptCnt();
        }
        row.put("A48", text(medcasno));
        row.put("A49", intOrDefault(iptCnt, "1"));
        row.put("B12", formatDateTime(hp.getAdmTime()));
        row.put("B15", formatDateTime(hp.getDscgTime()));
        row.put("A47", dashIfBlank(hp.getHealthCardNo()));
        row.put("A46C", HqmsConstants.toHqmsPayWay(hp.getPayWayCode()));
        row.put("A11", text(hp.getPsnName()));
        row.put("A12C", text(hp.getGendCode()));
        row.put("A13", formatDisplayDate(hp.getBrdy()));
        row.put("A14", intText(hp.getAgeYear()));
        row.put("A15C", text(hp.getNtlyCode()));
        row.put("A21C", text(hp.getMrgStasCode()));
        row.put("A38C", dashIfBlank(hp.getPrfsCode()));
        row.put("A19C", text(hp.getNatyCode()));
        row.put("A20N", "1");
        row.put("A20", excelTextProtect(upperTailX(hp.getCertno())));
        row.put("A22", preferPlaceName(hp.getBirplcName(), hp.getBirplcCodes()));
        // A23C：接口 RC036 省码（非中文地名）
        row.put("A23C", resolveNativeProvinceRc036(hp.getNaplCodes()));
        row.put("A24", resolveResdAddr(hp));
        row.put("A25C", excelTextProtect(dashIfBlank(hp.getResdAddrPoscode())));
        row.put("A26", resolveCurrAddr(hp));
        row.put("A27", excelTextProtect(text(hp.getPsnTel())));
        row.put("A28C", excelTextProtect(dashIfBlank(hp.getCurrAddrPoscode())));
        row.put("A29", dashIfBlank(hp.getEmprAddr()));
        row.put("A30", excelTextProtect(dashIfBlank(hp.getEmprTel())));
        row.put("A31C", excelTextProtect(dashIfBlank(hp.getEmprPoscode())));
        row.put("A32", text(hp.getConerName()));
        // 联系人关系：有码用真值，空则 9=其他（对齐国基默认，避免必填空）
        String rlts = text(hp.getConerRltsCode());
        if (rlts.isEmpty()) {
            rlts = "9";
        }
        row.put("A33C", rlts);
        row.put("A34", dashIfBlank(hp.getConerAddr()));
        row.put("A35", excelTextProtect(dashIfBlank(hp.getConerTel())));
        row.put("B38", "0");
        row.put("B11C", text(hp.getAdmWayCode()));
        row.put("B13C", organizationQueryService.resolveWeistatCatyCode(hp.getAdmCatyCode()));
        row.put("B14", joinWardBed(hp.getAdmWard(), hp.getAdmBed()));
        row.put("B21C", text(hp.getTransferDeptSummary()));
        row.put("B16C", organizationQueryService.resolveWeistatCatyCode(hp.getDscgCatyCode()));
        row.put("B17", joinWardBed(hp.getDscgWard(), hp.getDscgBed()));
        row.put("B20", intOrDefault(hp.getIptDays(), "1"));
        row.put("C01C", text(hp.getWmDiseCode()));
        row.put("C02N", text(hp.getOtpWmDise()));
        // —— 诊断 / 过敏 / 血型 / 医护 ——
        fillDiagnoses(row, hp.getDiagnoses());
        row.put("C11", text(hp.getPathologyNo()));
        row.put("C24C", mapAllergyFlag(hp.getDrugAllergyCode()));
        row.put("C25", text(hp.getAllergyDrugName()));
        row.put("C34C", text(hp.getAutopsyCode()));
        row.put("C26C", text(hp.getAboCode()));
        row.put("C27C", text(hp.getRhCode()));
        row.put("B22", text(hp.getDeptDirectorName()));
        row.put("B23", text(hp.getChfdrName()));
        row.put("B24", text(hp.getAtddrName()));
        row.put("B25", text(hp.getIptDrName()));
        row.put("B26", text(hp.getRespNursName()));
        row.put("B27", text(hp.getTrainDrName()));
        row.put("B28", text(hp.getIntnDrName()));
        row.put("B29", text(hp.getCodrName()));
        row.put("B30C", text(hp.getMedcasQltCode()));
        row.put("B31", text(hp.getQltctrlDrName()));
        row.put("B32", text(hp.getQltctrlNursName()));
        row.put("B33", formatDisplayDate(hp.getQltctrlDate()));
        // —— 手术 ——
        fillProcedures(row, hp.getProcedures());
        // —— 离院 / 再住院 / 昏迷（列名对齐国基 header：B36C/B37/B34C/B35） ——
        row.put("B34C", text(hp.getDscgWayCode()));
        String transferName = text(hp.getAcpMedinsName());
        if (transferName.isEmpty()) {
            transferName = text(hp.getAcpCommunityName());
        }
        row.put("B35", transferName);
        row.put("B36C", text(hp.getRinp31Flag()));
        row.put("B37", text(hp.getRinp31Purpose()));
        WeistatComaDurationSplitter.Parts bf = WeistatComaDurationSplitter.split(hp.getBrnDamgBfadmComaDura());
        row.put("C28", blankToZero(bf.days()));
        row.put("C29", blankToZero(bf.hours()));
        row.put("C30", blankToZero(bf.minutes()));
        WeistatComaDurationSplitter.Parts af = WeistatComaDurationSplitter.split(hp.getBrnDamgAfadmComaDura());
        row.put("C31", blankToZero(af.days()));
        row.put("C32", blankToZero(af.hours()));
        row.put("C33", blankToZero(af.minutes()));
        // —— 费用 ——
        fillFees(row, hp.getFees());
        return row;
    }

    /**
     * 填充入院/出院主诊与其他诊（其他诊最多 40 槽，序号 01～40）。
     *
     * @param row        行
     * @param diagnoses  诊断行
     */
    private void fillDiagnoses(Map<String, String> row, List<DiagnosisLineDto> diagnoses) {
        List<DiagnosisLineDto> list = List.of();
        if (diagnoses != null) {
            list = diagnoses;
        }
        DiagnosisLineDto admMain = firstByType(list, HomepageDiagType.ADMISSION_MAIN.getCode());
        if (admMain != null) {
            row.put("F02C", text(admMain.getDiagCode()));
            row.put("F03N", text(admMain.getDiagName()));
        }
        DiagnosisLineDto typedMain = firstByType(list, HomepageDiagType.DISCHARGE_MAIN.getCode());
        List<DiagnosisLineDto> others = listByTypeSorted(list, HomepageDiagType.DISCHARGE_OTHER.getCode());
        DiagnosisLineDto westernMain = resolveWesternMain(typedMain, others);
        if (westernMain != null) {
            row.put("C03C", text(westernMain.getDiagCode()));
            row.put("C04N", text(westernMain.getDiagName()));
            String admCond = text(westernMain.getAdmCondCode());
            if (admCond.isEmpty()) {
                admCond = "1";
            }
            row.put("C05C", admCond);
            row.put("F05", text(westernMain.getOutIllStatusCode()));
            if (typedMain != null && westernMain != typedMain) {
                others = withoutSameDiag(others, westernMain);
                if (!isLikelyWesternIcd(typedMain.getDiagCode())) {
                    List<DiagnosisLineDto> withFormer = new ArrayList<>();
                    withFormer.add(typedMain);
                    withFormer.addAll(others);
                    others = withFormer;
                }
            } else if (typedMain == null) {
                others = withoutSameDiag(others, westernMain);
            }
        }
        int limit = Math.min(HqmsColumns.MAX_OTHER_DIAG_SLOTS, others.size());
        for (int i = 0; i < limit; i++) {
            DiagnosisLineDto line = others.get(i);
            String nn = slot2(i + 1);
            row.put("C06x" + nn + "C", text(line.getDiagCode()));
            row.put("C07x" + nn + "N", text(line.getDiagName()));
            row.put("C08x" + nn + "C", text(line.getAdmCondCode()));
            row.put("F06x" + nn, text(line.getOutIllStatusCode()));
        }
        // 病理：C09C 编码 / C10N 名称；病理号走首页 pathologyNo→C11（见 assemble）
        DiagnosisLineDto pathology = firstByType(list, HomepageDiagType.PATHOLOGY.getCode());
        if (pathology != null) {
            row.put("C09C", text(pathology.getDiagCode()));
            row.put("C10N", text(pathology.getDiagName()));
        }
        // 损伤中毒外因：C12C / C13N
        DiagnosisLineDto injury = firstByType(list, HomepageDiagType.INJURY_POISON.getCode());
        if (injury != null) {
            row.put("C12C", text(injury.getDiagCode()));
            row.put("C13N", text(injury.getDiagName()));
        }
    }

    /**
     * 填充主要手术（C14x01…）与其他手术（C35x01…，最多 40）。
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
        // 无手术时主手术三列写 -（国基必填习惯）
        if (sorted.isEmpty()) {
            row.put("C14x01C", HqmsConstants.DASH);
            row.put("C15x01N", HqmsConstants.DASH);
            row.put("C16x01", HqmsConstants.DASH);
            return;
        }
        ProcedureLineDto main = null;
        for (ProcedureLineDto p : sorted) {
            // 业务分支：main_oprn_flag=1 覆盖为首台主手术
            if (p != null && "1".equals(text(p.getMainOprnFlag()))) {
                main = p;
                break;
            }
        }
        // 未标主手术时取排序首行（与国基「第 1 台」习惯一致）
        if (main == null) {
            for (ProcedureLineDto p : sorted) {
                if (p != null) {
                    main = p;
                    break;
                }
            }
        }
        // 列表皆空壳时主手术槽写 -，避免 NPE
        if (main == null) {
            row.put("C14x01C", HqmsConstants.DASH);
            row.put("C15x01N", HqmsConstants.DASH);
            row.put("C16x01", HqmsConstants.DASH);
            return;
        }
        putMainProcedure(row, main);
        List<ProcedureLineDto> others = new ArrayList<>();
        for (ProcedureLineDto p : sorted) {
            if (p == null || p == main) {
                continue;
            }
            others.add(p);
        }
        int limit = Math.min(HqmsColumns.MAX_OTHER_PROCEDURE_SLOTS, others.size());
        for (int i = 0; i < limit; i++) {
            putOtherProcedure(row, others.get(i), i + 1);
        }
    }

    /**
     * 写入主要手术槽 C14x01…C23x01。
     *
     * @param row  行
     * @param main 主手术
     */
    private void putMainProcedure(Map<String, String> row, ProcedureLineDto main) {
        // 手术码直出首页国临（与卫统同口径；4401 读同一条 PD 的 yb_no）
        row.put("C14x01C", dashIfBlank(main.getOprnOprtCode()));
        row.put("C15x01N", dashIfBlank(main.getOprnOprtName()));
        String dt = formatProcedureDateTime(main);
        if (dt.isEmpty()) {
            dt = HqmsConstants.DASH;
        }
        row.put("C16x01", dt);
        row.put("C17x01", text(main.getOprnLevelCode()));
        row.put("C18x01", text(main.getOperDrName()));
        // I助/II助：手术执行已带入首页；空写 -（HQMS 条件必填习惯）
        row.put("C19x01", dashIfBlank(main.getAsit1Name()));
        row.put("C20x01", dashIfBlank(main.getAsit2Name()));
        row.put("C21x01C", joinIncisionHeal(main.getOprnIncisionCode(), main.getHealGradeCode()));
        row.put("C22x01C", text(main.getAnstWayCode()));
        row.put("C23x01", text(main.getAnstDrName()));
    }

    /**
     * 写入其他手术槽 C35xNN…C44xNN。
     *
     * @param row   行
     * @param p     手术
     * @param slot  1-based 槽号
     */
    private void putOtherProcedure(Map<String, String> row, ProcedureLineDto p, int slot) {
        // 调用方已过滤 null；此处再挡一层避免槽位半写
        if (p == null) {
            return;
        }
        String nn = slot2(slot);
        // 其他手术码同样直出国临（与主手术 C14、卫统 SSJCZBM 同口径）
        row.put("C35x" + nn + "C", text(p.getOprnOprtCode()));
        row.put("C36x" + nn + "N", text(p.getOprnOprtName()));
        row.put("C37x" + nn, formatProcedureDateTime(p));
        row.put("C38x" + nn, text(p.getOprnLevelCode()));
        row.put("C39x" + nn, text(p.getOperDrName()));
        // 其他手术 I助/II助：对齐国基 asistantI/II；空写 -（HQMS 习惯，异于卫统留空）
        row.put("C40x" + nn, dashIfBlank(p.getAsit1Name()));
        row.put("C41x" + nn, dashIfBlank(p.getAsit2Name()));
        row.put("C42x" + nn + "C", joinIncisionHeal(p.getOprnIncisionCode(), p.getHealGradeCode()));
        row.put("C43x" + nn + "C", text(p.getAnstWayCode()));
        row.put("C44x" + nn, text(p.getAnstDrName()));
    }

    /**
     * 费用分项 → D01…D34。
     *
     * @param row  行
     * @param fees 首页 fees
     */
    private void fillFees(Map<String, String> row, Map<String, BigDecimal> fees) {
        Map<String, BigDecimal> map = Map.of();
        if (fees != null) {
            map = fees;
        }
        row.put("D01", moneyOrZero(map.get(MrmsConstants.EXT_MEDFEE_SUMAMT)));
        row.put("D09", moneyOrZero(map.get(MrmsConstants.EXT_SELFPAY_AMT)));
        row.put("D11", moneyOrZero(map.get("ordn_med_servfee")));
        row.put("D12", moneyOrZero(map.get("ordn_trt_oprt_fee")));
        row.put("D13", moneyOrZero(map.get("nurs_fee")));
        row.put("D14", moneyOrZero(map.get("com_med_serv_oth_fee")));
        row.put("D15", moneyOrZero(map.get("palg_diag_fee")));
        row.put("D16", moneyOrZero(map.get("lab_diag_fee")));
        row.put("D17", moneyOrZero(map.get("rdhy_diag_fee")));
        row.put("D18", moneyOrZero(map.get("clnc_dise_fee")));
        row.put("D19", moneyOrZero(map.get("nsrgtrt_item_fee")));
        row.put("D19x01", moneyOrZero(map.get("clnc_phys_trt_fee")));
        row.put("D20", moneyOrZero(map.get("rgtrt_trt_fee")));
        row.put("D20x01", moneyOrZero(map.get("anst_fee")));
        row.put("D20x02", moneyOrZero(map.get("rgtrt_fee")));
        row.put("D21", moneyOrZero(map.get("rhab_fee")));
        row.put("D22", moneyOrZero(map.get("tcm_trt_fee")));
        row.put("D23", moneyOrZero(map.get("wm_fee")));
        row.put("D23x01", moneyOrZero(map.get("abtl_medn_fee")));
        row.put("D24", moneyOrZero(map.get("tcmpat_fee")));
        row.put("D25", moneyOrZero(map.get("tcmherb_fee")));
        row.put("D26", moneyOrZero(map.get("blo_fee")));
        row.put("D27", moneyOrZero(map.get("albu_fee")));
        row.put("D28", moneyOrZero(map.get("glon_fee")));
        row.put("D29", moneyOrZero(map.get("clotfac_fee")));
        row.put("D30", moneyOrZero(map.get("cyki_fee")));
        row.put("D31", moneyOrZero(map.get("exam_dspo_matl_fee")));
        row.put("D32", moneyOrZero(map.get("trt_dspo_matl_fee")));
        row.put("D33", moneyOrZero(map.get("oprn_dspo_matl_fee")));
        row.put("D34", moneyOrZero(map.get(MrmsConstants.FEE_KEY_OTH)));
    }

    /**
     * 选定西医主诊：优先已是西医的 discharge_main，否则取其他诊中首条西医。
     *
     * @param typedMain 首页出院主诊
     * @param others    其他诊
     * @return 主诊行；皆无西医则回落 typedMain
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
        return typedMain;
    }

    /**
     * 从其他诊去掉与主诊同码同名行。
     *
     * @param others 其他诊
     * @param main   主诊
     * @return 新列表
     */
    private List<DiagnosisLineDto> withoutSameDiag(List<DiagnosisLineDto> others, DiagnosisLineDto main) {
        List<DiagnosisLineDto> filtered = new ArrayList<>();
        String mainCode = text(main.getDiagCode());
        String mainName = text(main.getDiagName());
        for (DiagnosisLineDto line : others) {
            if (line == null || line == main) {
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
     * 是否像西医 ICD/国临（排除中医三节目录码）。
     *
     * @param diagCode 诊断码
     * @return true 可作 HQMS 出院主诊编码
     */
    private boolean isLikelyWesternIcd(String diagCode) {
        String code = StringUtils.trimToNull(diagCode);
        if (code == null) {
            return false;
        }
        if (code.matches("^[A-Za-z]\\d{2}\\.\\d{2}\\.\\d{2}$")) {
            return false;
        }
        return code.matches("^[A-TV-Z][0-9][0-9A-Za-z](\\.[0-9A-Za-z]+)?([xX][0-9A-Za-z]+)?$");
    }

    /**
     * 药物过敏：院内码 → HQMS 1无/2有；空默认 1。
     *
     * @param drugAllergyCode 首页过敏码
     * @return 1 或 2
     */
    private String mapAllergyFlag(String drugAllergyCode) {
        String c = text(drugAllergyCode);
        if (c.isEmpty() || "1".equals(c) || "无".equals(c)) {
            return "1";
        }
        return "2";
    }

    /**
     * 籍贯省出站：{@code napl_codes} → HQMS RC036（A23C）。
     *
     * <p>取级联第一级区划码（或仅市级码时仍按前两位定省）；不写中文地名。</p>
     *
     * @param naplCodes 籍贯省市区 codes
     * @return RC036 值；无码或未识别→空串
     */
    private String resolveNativeProvinceRc036(List<String> naplCodes) {
        // 未选籍贯区划则无法定省
        if (naplCodes == null || naplCodes.isEmpty()) {
            return "";
        }
        String first = text(naplCodes.get(0));
        if (first.isEmpty()) {
            return "";
        }
        return HqmsConstants.toRc036FromAdminDivisionCode(first);
    }

    /**
     * 优先中文地名列；空则回落 codes 的 {@code /} 拼接。
     *
     * @param placeName 地名列
     * @param codes     区划码
     * @return 出站文本
     */
    private String preferPlaceName(String placeName, List<String> codes) {
        String name = text(placeName);
        // 有中文地名列则直接出站（A22 出生地址）
        if (!name.isEmpty()) {
            return name;
        }
        // 名列空：回落 codes 斜杠拼接
        return weistatRegionNameResolver.toPlaceText(codes);
    }

    /**
     * 现住址：优先 currAddr，否则区划码拼接+detail。
     *
     * @param hp 首页
     * @return 地址文本
     */
    private String resolveCurrAddr(HomepageDto hp) {
        String addr = text(hp.getCurrAddr());
        if (!addr.isEmpty()) {
            return addr;
        }
        return joinNonBlank(
                weistatRegionNameResolver.toPlaceText(hp.getCurrAddrCodes()),
                text(hp.getCurrAddrDetail()));
    }

    /**
     * 户口地址：区划码拼接 + 详址。
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
     * 病房+床位空格拼接。
     *
     * @param ward 病房
     * @param bed  床位
     * @return 文本
     */
    private String joinWardBed(String ward, String bed) {
        return joinNonBlank(text(ward), text(bed));
    }

    /**
     * 切口与愈合码拼接（HQMS 切口愈合常合并一列）。
     *
     * @param incision 切口
     * @param heal     愈合
     * @return 拼接或单段
     */
    private String joinIncisionHeal(String incision, String heal) {
        String a = text(incision);
        String b = text(heal);
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + b;
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
     * 取指定类型第一条诊断。
     *
     * @param list 列表
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
     * @param list 列表
     * @param type 类型码
     * @return 排序列表
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
     * 槽号两位：1→01。
     *
     * @param slot 1-based
     * @return 两位文本
     */
    private String slot2(int slot) {
        if (slot < 10) {
            return "0" + slot;
        }
        return String.valueOf(slot);
    }

    /**
     * 昏迷等必填数字空→0（对齐国基写 0）。
     *
     * @param raw 原文
     * @return 数字文本
     */
    private String blankToZero(String raw) {
        String t = text(raw);
        if (t.isEmpty()) {
            return "0";
        }
        return t;
    }

    /**
     * 金额结算刻度；空→0。
     *
     * @param amount 金额
     * @return 明文
     */
    private String moneyOrZero(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return MonetaryAmountSupport.scaleSettle(amount).toPlainString();
    }

    /**
     * LocalDateTime → yyyy-MM-dd HH:mm:ss。
     *
     * @param time 时间
     * @return 文本
     */
    private String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_TIME_FORMATTER.format(time);
    }

    /**
     * LocalDate → yyyy-MM-dd。
     *
     * @param date 日期
     * @return 文本
     */
    private String formatDisplayDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_DISPLAY_FORMATTER.format(date);
    }

    /**
     * 手术日期时间：优先首页 {@code oprnTime}（执行登记的手术时刻）；仅有日期时回落国基口径
     * {@code yyyy-MM-dd 00:00:01}。
     *
     * @param line 手术行
     * @return 文本；无时刻无日期则空串
     */
    private String formatProcedureDateTime(ProcedureLineDto line) {
        if (line == null) {
            return "";
        }
        LocalDateTime oprnTime = line.getOprnTime();
        if (oprnTime != null) {
            // 有执行手术时刻则出完整时分秒，避免只报当天 00:00:01
            return GeneralSharedConstants.DATE_TIME_FORMATTER.format(oprnTime);
        }
        LocalDate date = line.getOprnDate();
        if (date == null) {
            // 无日期则由调用方对主手术槽改写为 -
            return "";
        }
        return GeneralSharedConstants.DATE_DISPLAY_FORMATTER.format(date) + " 00:00:01";
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
     * 空则 {@code -}，否则原文。
     *
     * @param raw 原文
     * @return 文本
     */
    private String dashIfBlank(String raw) {
        String t = text(raw);
        if (t.isEmpty()) {
            return HqmsConstants.DASH;
        }
        return t;
    }

    /**
     * 长数字 Excel 文本保护（前置 TAB）。
     *
     * @param raw 原文
     * @return 保护后文本；空仍空（不写 TAB）
     */
    private String excelTextProtect(String raw) {
        String t = text(raw);
        if (t.isEmpty() || HqmsConstants.DASH.equals(t)) {
            return t;
        }
        if (t.charAt(0) == '\t') {
            return t;
        }
        return "\t" + t;
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
     * trim 后空→空串。
     *
     * @param raw 原文
     * @return 文本
     */
    private String text(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }
}
