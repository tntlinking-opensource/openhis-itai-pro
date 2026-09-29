/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.mapper;

import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveResult;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveSupport;
import com.openhis.health.application.shared.mrms.MedcasOrgCatyOutboundSupport;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.caserecord.vo.FeeSummarySnapshot;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageBaseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageIcuInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 → 医保 4401 {@link MedcasHomepagePayload} 映射。
 *
 * <p>读首页/子表经 {@link CaseRecordHomepageDomainService}（B.6.4）；费用读首页费用快照列
 * （无则补种一次）；诊断类型码在本类内映射为 4401 字典值。</p>
 */
@Component
@RequiredArgsConstructor
public class CaseRecord4401Mapper {

    /** 首页诊断类型 → 4401 ipt_patn_disediag_type_code。 */
    private static final Map<String, String> DIAG_TYPE_CODE = new HashMap<>();

    /** 首页诊断类型 → 4401 maindiag_flag（行上无显式标志时）。 */
    private static final Map<String, String> DIAG_MAIN_FLAG = new HashMap<>();

    static {
        DIAG_TYPE_CODE.put(HomepageDiagType.DISCHARGE_MAIN.getCode(), "1");
        DIAG_TYPE_CODE.put(HomepageDiagType.DISCHARGE_OTHER.getCode(), "1");
        DIAG_TYPE_CODE.put(HomepageDiagType.ADMISSION_MAIN.getCode(), "2");
        DIAG_TYPE_CODE.put(HomepageDiagType.ADMISSION_OTHER.getCode(), "2");
        DIAG_TYPE_CODE.put(HomepageDiagType.PATHOLOGY.getCode(), "3");
        DIAG_TYPE_CODE.put(HomepageDiagType.INJURY_POISON.getCode(), "4");

        DIAG_MAIN_FLAG.put(HomepageDiagType.DISCHARGE_MAIN.getCode(), "1");
        DIAG_MAIN_FLAG.put(HomepageDiagType.ADMISSION_MAIN.getCode(), "1");
        DIAG_MAIN_FLAG.put(HomepageDiagType.DISCHARGE_OTHER.getCode(), "0");
        DIAG_MAIN_FLAG.put(HomepageDiagType.ADMISSION_OTHER.getCode(), "0");
        DIAG_MAIN_FLAG.put(HomepageDiagType.PATHOLOGY.getCode(), "0");
        DIAG_MAIN_FLAG.put(HomepageDiagType.INJURY_POISON.getCode(), "0");
    }

    private final CaseRecordHomepageDomainService homepageDomainService;
    /**
     * 国临/医保双码解析（西医上传必须 yb_no；中医不对码，出站用目录码）。
     */
    private final MedcasDualCodeResolveSupport medcasDualCodeResolveSupport;
    /** 入/出院科别出站：科室医保编码 yb_no（禁 bus_no）。 */
    private final MedcasOrgCatyOutboundSupport medcasOrgCatyOutboundSupport;
    /** 诊断目录读侧：中西医归类（上传跳过中医国临→医保门禁）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 组装 4401 上传载荷。
     *
     * @param record 病案主档
     * @param insCtx 医保就诊上下文，可为 null
     * @return 4401 载荷
     */
    public MedcasHomepagePayload assemble(CaseRecord record, InsuranceEncounterContext insCtx) {
        CaseRecordHomepage homepage = homepageDomainService.requireExistingByCaseRecordId(record.getId());
        List<HomepageDiagnosis> diagnoses = homepageDomainService.listDiagnoses(record.getId());
        List<HomepageProcedure> procedures = homepageDomainService.listProcedures(record.getId());

        String fixmedinsCode = null;
        if (insCtx != null) {
            fixmedinsCode = trimToNull(insCtx.getFixmedinsCode());
        }
        String mdtrtSn = resolveMdtrtSn(insCtx, fixmedinsCode, homepage.getIptNo());
        String hmpgSn = trimToNull(homepage.getIptMedcasHmpgSn());

        MedcasHomepageBaseInfo base = new MedcasHomepageBaseInfo();
        base.setMdtrtSn(mdtrtSn);
        // 医保上下文存在时上送 mdtrt_id/psn_no/定点机构；psn_no 仅来自 InsuranceEncounterContext
        if (insCtx != null) {
            base.setMdtrtId(trimToNull(insCtx.getMdtrtId()));
            base.setPsnNo(trimToNull(insCtx.getPsnNo()));
            base.setFixmedinsCode(fixmedinsCode);
        }
        if (record.getPatnIptCnt() != null) {
            base.setPatnIptCnt(String.valueOf(record.getPatnIptCnt()));
        }
        base.setIptNo(trimToNull(homepage.getIptNo()));
        base.setMedcasno(trimToNull(record.getMedcasno()));
        base.setPsnName(trimToNull(homepage.getPsnName()));
        base.setGend(trimToNull(homepage.getGendCode()));
        base.setBrdy(formatDate(homepage.getBrdy()));
        base.setNtly(trimToNull(homepage.getNtlyCode()));
        base.setNaty(trimToNull(homepage.getNatyCode()));
        base.setCertno(trimToNull(homepage.getCertno()));
        base.setPrfs(trimToNull(homepage.getPrfsCode()));
        base.setMrgStas(trimToNull(homepage.getMrgStasCode()));
        base.setCurrAddr(trimToNull(homepage.getCurrAddr()));
        base.setPsnTel(trimToNull(homepage.getPsnTel()));
        base.setConerName(trimToNull(homepage.getConerName()));
        base.setConerTel(trimToNull(homepage.getConerTel()));
        base.setAdmWayCode(trimToNull(homepage.getAdmWayCode()));
        // 入/出院科别：科室医保编码（首页若误存 bus_no 则反查 yb_no）
        base.setAdmCaty(medcasOrgCatyOutboundSupport.resolveOutboundCaty(
                homepage.getAdmCatyCode(), "adm_caty"));
        base.setDscgCaty(medcasOrgCatyOutboundSupport.resolveOutboundCaty(
                homepage.getDscgCatyCode(), "dscg_caty"));
        base.setAdmDate(formatDateTime(homepage.getAdmTime()));
        base.setDscgDate(formatDateTime(homepage.getDscgTime()));
        if (homepage.getIptDays() != null) {
            base.setIptDays(String.valueOf(homepage.getIptDays()));
        }
        base.setDscgWay(trimToNull(homepage.getDscgWayCode()));
        base.setDeptdrtName(trimToNull(homepage.getDeptDirectorName()));
        base.setChfdrName(trimToNull(homepage.getChfdrName()));
        base.setAtddrName(trimToNull(homepage.getAtddrName()));
        base.setIptDrName(trimToNull(homepage.getIptDrName()));
        base.setRespNursName(trimToNull(homepage.getRespNursName()));
        // 付费方式：仅正式列 pay_way_code
        base.setMedfeePaymtdCode(trimToNull(homepage.getPayWayCode()));
        // 费用：读首页快照；无快照则补种（与打开页同口径，上报不另走现查）
        CaseRecordHomepage feeHomepage =
                homepageDomainService.ensureFeeSnapshotSeeded(record, homepage, MrmsConstants.SYSTEM_OPERATOR_ID);
        FeeSummarySnapshot fee = homepageDomainService.readFeeSnapshot(feeHomepage);
        applyFeeSnapshotToBase(base, fee);
        base.setValiFlag("1");

        MedcasHomepagePayload payload = new MedcasHomepagePayload();
        payload.setBaseinfo(base);
        payload.setDiseinfo(buildDiseinfo(diagnoses, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setOprninfo(buildOprninfo(procedures, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setIcuinfo(Collections.<MedcasHomepageIcuInfo>emptyList());
        return payload;
    }

    /**
     * 将费用快照写入 4401 baseinfo（总额/自付/分项）。
     *
     * @param base 4401 基本信息
     * @param fee  首页费用快照；可空
     */
    private void applyFeeSnapshotToBase(MedcasHomepageBaseInfo base, FeeSummarySnapshot fee) {
        // 无汇总时费用字段保持 null，由平台侧校验
        if (fee == null) {
            return;
        }
        base.setMedfeeSumamt(feeAmtToString(fee.getMedfeeSumamt()));
        base.setSelfpayAmt(feeAmtToString(fee.getSelfpayAmt()));
        Map<String, BigDecimal> items = fee.getFeeItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        base.setOrdnMedServfee(feeAmtToString(items.get("ordn_med_servfee")));
        base.setOrdnTrtOprtFee(feeAmtToString(items.get("ordn_trt_oprt_fee")));
        base.setNursFee(feeAmtToString(items.get("nurs_fee")));
        base.setComMedServOthFee(feeAmtToString(items.get("com_med_serv_oth_fee")));
        base.setPalgDiagFee(feeAmtToString(items.get("palg_diag_fee")));
        base.setLabDiagFee(feeAmtToString(items.get("lab_diag_fee")));
        base.setRdhyDiagFee(feeAmtToString(items.get("rdhy_diag_fee")));
        base.setClncDiseFee(feeAmtToString(items.get("clnc_dise_fee")));
        base.setNsrgtrtItemFee(feeAmtToString(items.get("nsrgtrt_item_fee")));
        base.setClncPhysTrtFee(feeAmtToString(items.get("clnc_phys_trt_fee")));
        base.setRgtrtTrtFee(feeAmtToString(items.get("rgtrt_trt_fee")));
        base.setAnstFee(feeAmtToString(items.get("anst_fee")));
        base.setRgtrtFee(feeAmtToString(items.get("rgtrt_fee")));
        base.setRhabFee(feeAmtToString(items.get("rhab_fee")));
        base.setTcmTrtFee(feeAmtToString(items.get("tcm_trt_fee")));
        base.setWmFee(feeAmtToString(items.get("wm_fee")));
        base.setAbtlMednFee(feeAmtToString(items.get("abtl_medn_fee")));
        base.setTcmpatFee(feeAmtToString(items.get("tcmpat_fee")));
        base.setTcmherbFee(feeAmtToString(items.get("tcmherb_fee")));
        base.setBloFee(feeAmtToString(items.get("blo_fee")));
        base.setAlbuFee(feeAmtToString(items.get("albu_fee")));
        base.setGlonFee(feeAmtToString(items.get("glon_fee")));
        base.setClotfacFee(feeAmtToString(items.get("clotfac_fee")));
        base.setCykiFee(feeAmtToString(items.get("cyki_fee")));
        base.setExamDspoMatlFee(feeAmtToString(items.get("exam_dspo_matl_fee")));
        base.setTrtDspoMatlFee(feeAmtToString(items.get("trt_dspo_matl_fee")));
        base.setOprnDspoMatlFee(feeAmtToString(items.get("oprn_dspo_matl_fee")));
        base.setOthFee(feeAmtToString(items.get(MrmsConstants.FEE_KEY_OTH)));
    }

    /**
     * BigDecimal 金额转 4401 字符串；null 保持 null。
     *
     * @param amt 金额
     * @return 非科学计数法字符串
     */
    private String feeAmtToString(BigDecimal amt) {
        if (amt == null) {
            return null;
        }
        return amt.toPlainString();
    }

    /**
     * 首页诊断子表 → 4401 diseinfo 行。
     *
     * <p>跳过无编码行；{@code needYbUp=0} 不上送（与结算清单 4101A 同口径）。
     * 西医出站须医保码；中医不对国临→医保。</p>
     *
     * @param rows 诊断实体
     * @param hmpgSn 4401 首页流水号
     * @param mdtrtSn 就诊流水号
     * @param fixmedinsCode 定点机构编码
     * @return 4401 诊断行列表
     */
    private List<MedcasHomepageDiseInfo> buildDiseinfo(
            List<HomepageDiagnosis> rows,
            String hmpgSn,
            String mdtrtSn,
            String fixmedinsCode) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<HomepageDiagnosis> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageDiagnosis::getDiagTypeCode)
                .thenComparing(HomepageDiagnosis::getSortNo));
        // Pre：首页 diag_code 存国临/中医目录码；西医出站必须医保码；中医不对码
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : sorted) {
            // needYbUp=0：不上送，不参与双码预解析
            if (isNeedYbUpSkipped(row)) {
                continue;
            }
            String clinic = trimToNull(row.getDiagCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, MedcasDualCodeResolveResult> dualByClinic =
                medcasDualCodeResolveSupport.resolveDiagnosisBatch(clinicCodes);
        Map<String, String> kindByCode =
                conditionDefinitionQueryService.mapDiagnosisKindByConditionCodes(clinicCodes);
        List<MedcasHomepageDiseInfo> result = new ArrayList<>();
        for (HomepageDiagnosis row : sorted) {
            // 首页「上传=否」：4401 diseinfo 跳过（与 4101A 同口径）
            if (isNeedYbUpSkipped(row)) {
                continue;
            }
            // 无编码行跳过，避免 4401 校验失败
            String clinicCode = trimToNull(row.getDiagCode());
            if (clinicCode == null) {
                continue;
            }
            String hint = clinicCode;
            if (trimToNull(row.getDiagName()) != null) {
                hint = clinicCode + " " + row.getDiagName().trim();
            }
            String ybNo = medcasDualCodeResolveSupport.requireDiagnosisOutbound(
                    dualByClinic, kindByCode, clinicCode, hint);
            MedcasHomepageDiseInfo item = new MedcasHomepageDiseInfo();
            item.setIptMedcasHmpgSn(hmpgSn);
            item.setMdtrtSn(mdtrtSn);
            item.setFixmedinsCode(fixmedinsCode);
            item.setIptPatnDisediagTypeCode(mapDiagTypeCode(row.getDiagTypeCode()));
            item.setMaindiagFlag(mapDiagMainFlag(row.getDiagTypeCode(), row.getMaindiagFlag()));
            item.setDiagCode(ybNo);
            item.setDiagName(trimToNull(row.getDiagName()));
            item.setAdmCondCode(trimToNull(row.getAdmCondCode()));
            item.setValiFlag("1");
            result.add(item);
        }
        return result;
    }

    /**
     * 老系统 {@code needYbUp=0} 不上送病案首页诊断。
     *
     * @param row 诊断行
     * @return true 表示跳过
     */
    private boolean isNeedYbUpSkipped(HomepageDiagnosis row) {
        return "0".equals(trimToNull(row.getNeedYbUp()));
    }

    /**
     * 首页手术子表 → 4401 oprninfo 行（按序号排序；空列表→{@code []}）。
     *
     * <p>每行国临码须非空且能解析 {@code yb_no}，否则整单失败（EX-MRMS-UP-OP-201/202）；
     * 禁止静默跳过无码行。</p>
     *
     * @param rows 手术实体
     * @param hmpgSn 4401 首页流水号
     * @param mdtrtSn 就诊流水号
     * @param fixmedinsCode 定点机构编码
     * @return 4401 手术行列表
     */
    private List<MedcasHomepageOprnInfo> buildOprninfo(
            List<HomepageProcedure> rows,
            String hmpgSn,
            String mdtrtSn,
            String fixmedinsCode) {
        // 无手术行：上送空数组（非 null）
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        // Pre：首页 oprn_oprt_code 存国临；出站必须医保码，无对照报错
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageProcedure row : sorted) {
            String clinic = trimToNull(row.getOprnOprtCode());
            // 仅收集非空国临码做批量对码；空码行在下方 Gate 抛 EX-202
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, MedcasDualCodeResolveResult> dualByClinic =
                medcasDualCodeResolveSupport.resolveProcedureBatch(clinicCodes);
        List<MedcasHomepageOprnInfo> result = new ArrayList<>();
        for (HomepageProcedure row : sorted) {
            String clinicCode = trimToNull(row.getOprnOprtCode());
            // 业务分支：组装报错 hint（码+名；皆空则序号）
            String hint = clinicCode;
            if (hint == null) {
                hint = trimToNull(row.getOprnOprtName());
            } else if (trimToNull(row.getOprnOprtName()) != null) {
                hint = clinicCode + " " + row.getOprnOprtName().trim();
            }
            if (hint == null && row.getSortNo() != null) {
                hint = "序号" + row.getSortNo();
            }
            // Gate：空码→EX-202；无 yb_no→EX-201
            String ybNo = medcasDualCodeResolveSupport.requireProcedureYbOutbound(
                    dualByClinic, clinicCode, hint);
            MedcasHomepageOprnInfo item = new MedcasHomepageOprnInfo();
            item.setIptMedcasHmpgSn(hmpgSn);
            item.setMdtrtSn(mdtrtSn);
            item.setFixmedinsCode(fixmedinsCode);
            item.setOprnOprtDate(formatDate(row.getOprnDate()));
            item.setOprnOprtCode(ybNo);
            item.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            item.setMainOprnFlag(trimToNull(row.getMainOprnFlag()));
            // Asm：愈合/麻醉/级别/助手术者透传可空（AS BR-MRMS-UP-OP-004）
            item.setOprnLvCode(trimToNull(row.getOprnLevelCode()));
            item.setSincHealLvCode(trimToNull(row.getHealGradeCode()));
            item.setAnstMtdCode(trimToNull(row.getAnstWayCode()));
            item.setOperName(trimToNull(row.getOperDrName()));
            item.setAsit1Name(trimToNull(row.getAsit1Name()));
            item.setAsitName2(trimToNull(row.getAsit2Name()));
            item.setAnstDrName(trimToNull(row.getAnstDrName()));
            item.setValiFlag("1");
            result.add(item);
        }
        return result;
    }

    /**
     * 解析 4401 mdtrt_sn：优先医保上下文，否则 fixmedinsCode+iptNo 拼接。
     *
     * @param insCtx 医保就诊上下文，可为 null
     * @param fixmedinsCode 定点机构编码
     * @param iptNo 住院号
     * @return 就诊流水号，可能为 null
     */
    private String resolveMdtrtSn(InsuranceEncounterContext insCtx, String fixmedinsCode, String iptNo) {
        if (insCtx != null && trimToNull(insCtx.getMdtrtSn()) != null) {
            return insCtx.getMdtrtSn();
        }
        if (trimToNull(fixmedinsCode) != null && trimToNull(iptNo) != null) {
            return fixmedinsCode + iptNo;
        }
        return trimToNull(fixmedinsCode);
    }

    /**
     * 首页诊断类型 → 4401 {@code ipt_patn_disediag_type_code}；未知类型默认 {@code "1"}。
     *
     * @param diagTypeCode {@link HomepageDiagType} code
     * @return 4401 类型码
     */
    private static String mapDiagTypeCode(String diagTypeCode) {
        return DIAG_TYPE_CODE.getOrDefault(diagTypeCode, "1");
    }

    /**
     * 首页诊断主诊断标志 → 4401 {@code maindiag_flag}；行上已有值时优先行值。
     *
     * @param diagTypeCode 诊断类型 code
     * @param rowMainFlag 行上主诊断标志，可为 null
     * @return 4401 主诊断标志
     */
    private static String mapDiagMainFlag(String diagTypeCode, String rowMainFlag) {
        // 行上已显式标记主诊断时优先行值
        if (trimToNull(rowMainFlag) != null) {
            return rowMainFlag;
        }
        return DIAG_MAIN_FLAG.getOrDefault(diagTypeCode, "0");
    }

    /**
     * LocalDate 格式化为 4401 展示日期（yyyy-MM-dd）。
     *
     * @param date 日期
     * @return 格式化字符串，null 时 null
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }

    /**
     * LocalDateTime 格式化为 4401 展示日期（yyyy-MM-dd）。
     *
     * @param dateTime 日期时间
     * @return 格式化字符串，null 时 null
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }
}
