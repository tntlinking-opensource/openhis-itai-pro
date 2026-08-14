/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.mapper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
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
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListSetlinfo;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 → 医保 4101A {@link MedcasSettleListPayload} 映射。
 *
 * <p>仅组装规范必填与条件必填字段；无 ICU/输血业务数据时输出空数组。</p>
 */
@Component
@RequiredArgsConstructor
public class CaseRecord4101AMapper {

    /** 首页诊断类型 → 4101A diag_type（1 西医主诊 / 2 西医其他）。 */
    private static final Map<String, String> DIAG_TYPE = new HashMap<>();

    static {
        DIAG_TYPE.put(HomepageDiagType.DISCHARGE_MAIN.getCode(), "1");
        DIAG_TYPE.put(HomepageDiagType.DISCHARGE_OTHER.getCode(), "2");
        DIAG_TYPE.put(HomepageDiagType.ADMISSION_MAIN.getCode(), "1");
        DIAG_TYPE.put(HomepageDiagType.ADMISSION_OTHER.getCode(), "2");
    }

    private final CaseRecordHomepageDomainService homepageDomainService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;

    /**
     * 组装 4101A 上传载荷。
     *
     * @param record 病案主档
     * @param insCtx 医保结算上下文（须含 psn_no/mdtrt_id/setl_id）
     * @param operatorUserId 当前登录用户主键（填报人）
     * @return 4101A 载荷
     */
    public MedcasSettleListPayload assemble(
            CaseRecord record, InsuranceEncounterContext insCtx, Long operatorUserId) {
        CaseRecordHomepage homepage = homepageDomainService.requireExistingByCaseRecordId(record.getId());
        List<HomepageDiagnosis> diagnoses = homepageDomainService.listDiagnoses(record.getId());
        List<HomepageProcedure> procedures = homepageDomainService.listProcedures(record.getId());
        JSONObject ext = parseExtension(homepage.getExtensionJson());

        MedcasSettleListSetlinfo setl = new MedcasSettleListSetlinfo();
        setl.setPsnNo(trimToNull(insCtx.getPsnNo()));
        setl.setMdtrtId(trimToNull(insCtx.getMdtrtId()));
        setl.setSetlId(trimToNull(insCtx.getSetlId()));
        setl.setMedcasno(trimToNull(record.getMedcasno()));
        setl.setNtly(trimToNull(homepage.getNtlyCode()));
        setl.setPrfs(trimToNull(homepage.getPrfsCode()));
        setl.setCurrAddr(trimToNull(homepage.getCurrAddr()));
        setl.setConerName(trimToNull(homepage.getConerName()));
        setl.setPatnRlts(trimToNull(homepage.getConerRltsCode()));
        setl.setConerAddr(trimToNull(homepage.getConerAddr()));
        setl.setConerTel(trimToNull(homepage.getConerTel()));
        setl.setAdmWay(trimToNull(homepage.getAdmWayCode()));
        setl.setTrtType(trimToNull(homepage.getTrtTypeCode()));
        setl.setAdmTime(formatDateTime(homepage.getAdmTime()));
        setl.setRefldeptDept(trimToNull(homepage.getTransferDeptSummary()));
        setl.setDscgTime(formatDateTime(homepage.getDscgTime()));
        setl.setDscgCaty(trimToNull(homepage.getDscgCatyCode()));
        setl.setOtpWmDise(trimToNull(homepage.getOtpWmDise()));
        setl.setWmDiseCode(trimToNull(homepage.getWmDiseCode()));
        setl.setSpgaNurscareDays(intText(homepage.getSpgaNurscareDays()));
        setl.setLv1NurscareDays(intText(homepage.getLv1NurscareDays()));
        setl.setScdNurscareDays(intText(homepage.getScdNurscareDays()));
        setl.setLv3NurscareDays(intText(homepage.getLv3NurscareDays()));
        setl.setDscgWay(trimToNull(homepage.getDscgWayCode()));
        setl.setBillCode(trimToNull(insCtx.getBillCode()));
        setl.setBillNo(trimToNull(insCtx.getBillNo()));
        setl.setBizSn(trimToNull(insCtx.getBizSn()));
        // 再入院标志：扩展优先，默认否
        String rinp = extGet(ext, "rinp31Flag");
        if (rinp == null) {
            rinp = extGet(ext, "days_rinp_flag_31");
        }
        if (rinp == null) {
            rinp = "2";
        }
        setl.setDaysRinpFlag31(rinp);
        setl.setSetlBegnDate(formatDate(insCtx.getSetlBegnDate()));
        setl.setSetlEndDate(formatDate(insCtx.getSetlEndDate()));
        // 结算日仍空时用入出院日兜底
        if (trimToNull(setl.getSetlBegnDate()) == null) {
            setl.setSetlBegnDate(formatDateTime(homepage.getAdmTime()));
        }
        if (trimToNull(setl.getSetlEndDate()) == null) {
            setl.setSetlEndDate(formatDateTime(homepage.getDscgTime()));
        }
        fillReporter(setl, operatorUserId, homepage);

        MedcasSettleListPayload payload = new MedcasSettleListPayload();
        payload.setSetlinfo(setl);
        payload.setDiseinfo(buildDiseinfo(diagnoses));
        payload.setOprninfo(buildOprninfo(procedures));
        return payload;
    }

    /**
     * 填报部门/填报人：操作员科室名 + 姓名；回退首页主诊医师名。
     *
     * @param setl setlinfo
     * @param operatorUserId 登录用户
     * @param homepage 首页
     */
    private void fillReporter(MedcasSettleListSetlinfo setl, Long operatorUserId, CaseRecordHomepage homepage) {
        String fillPsn = null;
        String fillDept = null;
        if (operatorUserId != null) {
            Practitioner practitioner = practitionerQueryService.findByUserId(operatorUserId);
            if (practitioner != null) {
                fillPsn = trimToNull(practitioner.getName());
                if (practitioner.getOrgId() != null) {
                    Organization org = organizationQueryService.loadOrganizationByIdForDisplay(practitioner.getOrgId());
                    if (org != null) {
                        fillDept = trimToNull(org.getName());
                    }
                }
            }
        }
        if (fillPsn == null) {
            fillPsn = trimToNull(homepage.getChfdrName());
        }
        if (fillPsn == null) {
            fillPsn = trimToNull(homepage.getAtddrName());
        }
        if (fillDept == null) {
            fillDept = trimToNull(homepage.getDscgCatyName());
        }
        if (fillDept == null) {
            fillDept = trimToNull(homepage.getAdmCatyName());
        }
        setl.setMedinsFillPsn(fillPsn);
        setl.setMedinsFillDept(fillDept);
    }

    /**
     * 诊断子表 → 4101A diseinfo。
     *
     * @param rows 诊断行
     * @return diseinfo
     */
    private List<MedcasSettleListDiseInfo> buildDiseinfo(List<HomepageDiagnosis> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageDiagnosis> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageDiagnosis::getDiagTypeCode)
                .thenComparing(HomepageDiagnosis::getSortNo));
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : sorted) {
            String clinic = trimToNull(row.getDiagCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, String> ybByClinic = conditionDefinitionQueryService.mapYbNoByClinicCodes(clinicCodes);
        List<MedcasSettleListDiseInfo> result = new ArrayList<>();
        for (HomepageDiagnosis row : sorted) {
            String clinicCode = trimToNull(row.getDiagCode());
            if (clinicCode == null) {
                continue;
            }
            String ybNo = trimToNull(ybByClinic.get(clinicCode));
            if (ybNo == null) {
                throw new BusinessException(
                        MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                        "结算清单诊断未对医保码：" + clinicCode);
            }
            MedcasSettleListDiseInfo item = new MedcasSettleListDiseInfo();
            item.setDiagType(DIAG_TYPE.getOrDefault(row.getDiagTypeCode(), "1"));
            item.setDiagCode(ybNo);
            item.setDiagName(trimToNull(row.getDiagName()));
            item.setAdmCondType(trimToNull(row.getAdmCondCode()));
            String mainFlag = trimToNull(row.getMaindiagFlag());
            if (mainFlag == null) {
                if (HomepageDiagType.DISCHARGE_MAIN.getCode().equals(row.getDiagTypeCode())
                        || HomepageDiagType.ADMISSION_MAIN.getCode().equals(row.getDiagTypeCode())) {
                    mainFlag = "1";
                } else {
                    mainFlag = "0";
                }
            }
            item.setMaindiagFlag(mainFlag);
            result.add(item);
        }
        return result;
    }

    /**
     * 手术子表 → 4101A oprninfo。
     *
     * @param rows 手术行
     * @return oprninfo
     */
    private List<MedcasSettleListOprnInfo> buildOprninfo(List<HomepageProcedure> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageProcedure row : sorted) {
            String clinic = trimToNull(row.getOprnOprtCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, String> ybByClinic = procedureDefinitionQueryService.mapYbNoByClinicCodes(clinicCodes);
        List<MedcasSettleListOprnInfo> result = new ArrayList<>();
        for (HomepageProcedure row : sorted) {
            String clinicCode = trimToNull(row.getOprnOprtCode());
            if (clinicCode == null) {
                continue;
            }
            String ybNo = trimToNull(ybByClinic.get(clinicCode));
            if (ybNo == null) {
                throw new BusinessException(
                        MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                        "结算清单手术未对医保码：" + clinicCode);
            }
            MedcasSettleListOprnInfo item = new MedcasSettleListOprnInfo();
            // 1 主要手术 / 2 其他手术
            if ("1".equals(trimToNull(row.getMainOprnFlag()))) {
                item.setOprnOprtType("1");
            } else {
                item.setOprnOprtType("2");
            }
            item.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            item.setOprnOprtCode(ybNo);
            item.setAnstWay(trimToNull(row.getAnstWayCode()));
            item.setOperDrCode(trimToNull(row.getOperDrCode()));
            item.setAnstDrCode(trimToNull(row.getAnstDrCode()));
            item.setOprnOprtBegntime(formatDate(row.getOprnDate()));
            result.add(item);
        }
        return result;
    }

    /**
     * 解析 extension JSON。
     *
     * @param json extension 字符串
     * @return JSONObject，永不 null
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
     * 读取 extension 字符串字段。
     *
     * @param ext extension
     * @param key 键
     * @return 值或 null
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
     * Integer → 上报用数字文本。
     *
     * @param value 天数
     * @return 文本或 null
     */
    private String intText(Integer value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * LocalDate 格式化为 yyyy-MM-dd。
     *
     * @param date 日期
     * @return 字符串
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }

    /**
     * LocalDateTime 格式化为 yyyy-MM-dd HH:mm:ss（入出院时间）。
     *
     * @param dateTime 日期时间
     * @return 字符串
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }
}
