/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.mapper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageBaseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageIcuInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepageOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepagePayload;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
 * <p>读首页/子表经 {@link CaseRecordHomepageDomainService}（B.6.4）；费用等扩展字段取自
 * {@code extensionJson}；诊断类型码在本类内映射为 4401 字典值。</p>
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
    /** 国临码 → 医保 yb_no 解析（诊断）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 国临码 → 医保 yb_no 解析（手术）。 */
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;

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
        JSONObject ext = parseExtension(homepage.getExtensionJson());

        String fixmedinsCode = null;
        if (insCtx != null) {
            fixmedinsCode = trimToNull(insCtx.getFixmedinsCode());
        }
        String mdtrtSn = resolveMdtrtSn(insCtx, fixmedinsCode, homepage.getIptNo());
        String hmpgSn = trimToNull(homepage.getIptMedcasHmpgSn());

        MedcasHomepageBaseInfo base = new MedcasHomepageBaseInfo();
        base.setMdtrtSn(mdtrtSn);
        // 医保上下文存在时上送 mdtrt_id/psn_no/定点机构
        if (insCtx != null) {
            base.setMdtrtId(trimToNull(insCtx.getMdtrtId()));
            base.setPsnNo(trimToNull(insCtx.getPsnNo()));
            base.setFixmedinsCode(fixmedinsCode);
        }
        // 上下文无 psn_no 时从 extensionJson 回填
        if (trimToNull(base.getPsnNo()) == null) {
            base.setPsnNo(extGet(ext, "psn_no"));
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
        base.setAdmCaty(trimToNull(homepage.getAdmCatyCode()));
        base.setDscgCaty(trimToNull(homepage.getDscgCatyCode()));
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
        base.setMedfeeSumamt(extGet(ext, "medfee_sumamt"));
        base.setSelfpayAmt(extGet(ext, "selfpay_amt"));
        base.setMedfeePaymtdCode(extGet(ext, "medfee_paymtd_code"));
        // 费用分项：extension_json 同名键 → baseinfo
        base.setOrdnMedServfee(extGet(ext, "ordn_med_servfee"));
        base.setOrdnTrtOprtFee(extGet(ext, "ordn_trt_oprt_fee"));
        base.setNursFee(extGet(ext, "nurs_fee"));
        base.setComMedServOthFee(extGet(ext, "com_med_serv_oth_fee"));
        base.setPalgDiagFee(extGet(ext, "palg_diag_fee"));
        base.setLabDiagFee(extGet(ext, "lab_diag_fee"));
        base.setRdhyDiagFee(extGet(ext, "rdhy_diag_fee"));
        base.setClncDiseFee(extGet(ext, "clnc_dise_fee"));
        base.setNsrgtrtItemFee(extGet(ext, "nsrgtrt_item_fee"));
        base.setClncPhysTrtFee(extGet(ext, "clnc_phys_trt_fee"));
        base.setRgtrtTrtFee(extGet(ext, "rgtrt_trt_fee"));
        base.setAnstFee(extGet(ext, "anst_fee"));
        base.setRgtrtFee(extGet(ext, "rgtrt_fee"));
        base.setRhabFee(extGet(ext, "rhab_fee"));
        base.setTcmTrtFee(extGet(ext, "tcm_trt_fee"));
        base.setWmFee(extGet(ext, "wm_fee"));
        base.setAbtlMednFee(extGet(ext, "abtl_medn_fee"));
        base.setTcmpatFee(extGet(ext, "tcmpat_fee"));
        base.setTcmherbFee(extGet(ext, "tcmherb_fee"));
        base.setBloFee(extGet(ext, "blo_fee"));
        base.setAlbuFee(extGet(ext, "albu_fee"));
        base.setGlonFee(extGet(ext, "glon_fee"));
        base.setClotfacFee(extGet(ext, "clotfac_fee"));
        base.setCykiFee(extGet(ext, "cyki_fee"));
        base.setExamDspoMatlFee(extGet(ext, "exam_dspo_matl_fee"));
        base.setTrtDspoMatlFee(extGet(ext, "trt_dspo_matl_fee"));
        base.setOprnDspoMatlFee(extGet(ext, "oprn_dspo_matl_fee"));
        base.setOthFee(extGet(ext, "oth_fee"));
        base.setValiFlag("1");

        MedcasHomepagePayload payload = new MedcasHomepagePayload();
        payload.setBaseinfo(base);
        payload.setDiseinfo(buildDiseinfo(diagnoses, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setOprninfo(buildOprninfo(procedures, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setIcuinfo(Collections.<MedcasHomepageIcuInfo>emptyList());
        return payload;
    }

    /**
     * 首页诊断子表 → 4401 diseinfo 行（跳过无编码行，按类型+序号排序）。
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
        // Pre：首页 diag_code 存国临码，4401 须输出医保 yb_no
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : sorted) {
            String clinic = trimToNull(row.getDiagCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, String> ybByClinic = conditionDefinitionQueryService.mapYbNoByClinicCodes(clinicCodes);
        List<MedcasHomepageDiseInfo> result = new ArrayList<>();
        for (HomepageDiagnosis row : sorted) {
            // 无 ICD 编码行跳过，避免 4401 校验失败
            String clinicCode = trimToNull(row.getDiagCode());
            if (clinicCode == null) {
                continue;
            }
            String ybNo = trimToNull(ybByClinic.get(clinicCode));
            if (ybNo == null) {
                String hint = clinicCode;
                if (trimToNull(row.getDiagName()) != null) {
                    hint = clinicCode + " " + row.getDiagName().trim();
                }
                throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    "病案诊断未对医保码，请在诊断目录维护国临码(clinic_code)与医保编码(yb_no)对照：" + hint);
            }
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
     * 首页手术子表 → 4401 oprninfo 行（跳过无编码行，按序号排序）。
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
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        // Pre：首页 oprn_oprt_code 存国临码，4401 须输出医保 yb_no
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageProcedure row : sorted) {
            String clinic = trimToNull(row.getOprnOprtCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, String> ybByClinic = procedureDefinitionQueryService.mapYbNoByClinicCodes(clinicCodes);
        List<MedcasHomepageOprnInfo> result = new ArrayList<>();
        for (HomepageProcedure row : sorted) {
            // 无手术编码行跳过
            String clinicCode = trimToNull(row.getOprnOprtCode());
            if (clinicCode == null) {
                continue;
            }
            String ybNo = trimToNull(ybByClinic.get(clinicCode));
            if (ybNo == null) {
                String hint = clinicCode;
                if (trimToNull(row.getOprnOprtName()) != null) {
                    hint = clinicCode + " " + row.getOprnOprtName().trim();
                }
                throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    "病案手术未对医保码，请在手术目录维护国临码(clinic_code)与医保编码(yb_no)对照：" + hint);
            }
            MedcasHomepageOprnInfo item = new MedcasHomepageOprnInfo();
            item.setIptMedcasHmpgSn(hmpgSn);
            item.setMdtrtSn(mdtrtSn);
            item.setFixmedinsCode(fixmedinsCode);
            item.setOprnOprtDate(formatDate(row.getOprnDate()));
            item.setOprnOprtCode(ybNo);
            item.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            item.setMainOprnFlag(trimToNull(row.getMainOprnFlag()));
            // 实体已有愈合/麻醉/级别码则上送（规范有行时校这些码）
            item.setOprnLvCode(trimToNull(row.getOprnLevelCode()));
            item.setSincHealLvCode(trimToNull(row.getHealGradeCode()));
            item.setAnstMtdCode(trimToNull(row.getAnstWayCode()));
            item.setOperName(trimToNull(row.getOperDrName()));
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
     * 解析首页 extensionJson 为 JSONObject；失败时返回空对象。
     *
     * @param json extension JSON 字符串
     * @return 解析结果，永不为 null
     */
    private JSONObject parseExtension(String json) {
        if (trimToNull(json) == null) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    /**
     * 从 extension JSON 读取字符串字段。
     *
     * @param ext extension 对象
     * @param key 字段键
     * @return 字符串值，缺失时 null
     */
    private String extGet(JSONObject ext, String key) {
        if (ext == null || !ext.containsKey(key)) {
            return null;
        }
        Object val = ext.get(key);
        if (val == null) {
            return null;
        }
        return String.valueOf(val);
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
