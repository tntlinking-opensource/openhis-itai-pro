/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.mapper;

import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveResult;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveSupport;
import com.openhis.health.application.shared.mrms.MedcasOrgCatyOutboundSupport;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListSetlinfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 → 医保 4101A {@link MedcasSettleListPayload} 映射。
 *
 * <p>仅组装规范必填与条件必填字段；无 ICU/输血业务数据时输出空数组。
 * 护理四天 null→{@code "0"}；{@code bill_code} 无博思批次码时按发票号/流水/住院号/病案号兜底；
 * 诊断 {@code adm_cond_type} 空默认 {@code "3"}（情况不明）。
 * {@code diseinfo} <strong>仅出院诊断</strong>；{@code diag_type} 按中西医归类
 * （1 西医 / 2 中医主病 / 3 中医主证），对齐老结算清单，避免「西医/中医主诊断只能各一条」拒收。</p>
 */
@Component
@RequiredArgsConstructor
public class CaseRecord4101AMapper {

    private final CaseRecordHomepageDomainService homepageDomainService;
    /**
     * 国临/医保双码解析（西医上传必须 yb_no；中医不对码，出站用目录码）。
     */
    private final MedcasDualCodeResolveSupport medcasDualCodeResolveSupport;
    /** 入/出院科别出站：科室医保编码 yb_no（禁 bus_no）。 */
    private final MedcasOrgCatyOutboundSupport medcasOrgCatyOutboundSupport;
    /** 诊断目录读侧：中西医归类（上传跳过中医国临→医保门禁）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
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
        // 出院科别：科室医保编码（首页若误存 bus_no 则反查 yb_no）
        setl.setDscgCaty(medcasOrgCatyOutboundSupport.resolveOutboundCaty(
                homepage.getDscgCatyCode(), "dscg_caty"));
        setl.setOtpWmDise(trimToNull(homepage.getOtpWmDise()));
        setl.setWmDiseCode(trimToNull(homepage.getWmDiseCode()));
        // 护理四天：对齐老 SQL ifnull(...,0)，空不上送会卡 4101A Gate
        setl.setSpgaNurscareDays(intTextOrZero(homepage.getSpgaNurscareDays()));
        setl.setLv1NurscareDays(intTextOrZero(homepage.getLv1NurscareDays()));
        setl.setScdNurscareDays(intTextOrZero(homepage.getScdNurscareDays()));
        setl.setLv3NurscareDays(intTextOrZero(homepage.getLv3NurscareDays()));
        setl.setDscgWay(trimToNull(homepage.getDscgWayCode()));
        setl.setBillCode(trimToNull(insCtx.getBillCode()));
        setl.setBillNo(trimToNull(insCtx.getBillNo()));
        setl.setBizSn(trimToNull(insCtx.getBizSn()));
        // bill_code 仍空：对齐老 ifnull(..., ZY_NO) — 发票号 → 流水 → 住院号 → 病案号
        if (trimToNull(setl.getBillCode()) == null) {
            setl.setBillCode(firstNonBlank(
                    setl.getBillNo(), setl.getBizSn(), record.getIptNo(), record.getMedcasno()));
        }
        // 31 天再住院计划：正式列 rinp31_flag；空则默认「2=否」
        String rinp = trimToNull(homepage.getRinp31Flag());
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
     * <p>仅出院诊断（入院不上送）；跳过 {@code needYbUp=0}。
     * {@code diag_type}：西医恒 {@code 1}；中医主诊 {@code 2}（主病）、中医其他 {@code 3}（主证）。</p>
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
            if (!isDischargeDiagRow(row)) {
                continue;
            }
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
        // Pre：中西医归类；中医不对国临→医保；并决定 4101A diag_type 1/2/3
        Map<String, String> kindByCode =
                conditionDefinitionQueryService.mapDiagnosisKindByConditionCodes(clinicCodes);
        List<MedcasSettleListDiseInfo> result = new ArrayList<>();
        boolean westernMainUsed = false;
        boolean tcmMainUsed = false;
        for (HomepageDiagnosis row : sorted) {
            if (!isDischargeDiagRow(row)) {
                continue;
            }
            if (isNeedYbUpSkipped(row)) {
                continue;
            }
            String clinicCode = trimToNull(row.getDiagCode());
            if (clinicCode == null) {
                continue;
            }
            // 出站：西医必须医保码；中医用目录自身编码
            String hint = clinicCode;
            if (trimToNull(row.getDiagName()) != null) {
                hint = clinicCode + " " + row.getDiagName().trim();
            }
            String ybNo = medcasDualCodeResolveSupport.requireDiagnosisOutbound(
                    dualByClinic, kindByCode, clinicCode, hint);
            boolean isTcm = DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                    kindByCode.get(clinicCode));
            boolean wantMain = resolveDischargeMainFlag(row);
            // 平台：西医主诊、中医主病各至多一条；多余主标志降为 0
            boolean mainFlag = wantMain;
            if (mainFlag && isTcm) {
                if (tcmMainUsed) {
                    mainFlag = false;
                } else {
                    tcmMainUsed = true;
                }
            } else if (mainFlag) {
                if (westernMainUsed) {
                    mainFlag = false;
                } else {
                    westernMainUsed = true;
                }
            }
            MedcasSettleListDiseInfo item = new MedcasSettleListDiseInfo();
            item.setDiagType(resolve4101ADiagType(isTcm, mainFlag));
            item.setDiagCode(ybNo);
            // 上传名称优先目录医保名，否则国临诊断名
            MedcasDualCodeResolveResult dualHit = dualByClinic.get(clinicCode);
            String ybName = null;
            if (dualHit != null) {
                ybName = trimToNull(dualHit.getYbName());
            }
            if (ybName != null) {
                item.setDiagName(ybName);
            } else {
                item.setDiagName(trimToNull(row.getDiagName()));
            }
            // 入院病情：EMR/首页未填时按术语「3=情况不明」上送，避免 4101A 行校验整批失败
            String admCond = trimToNull(row.getAdmCondCode());
            if (admCond == null) {
                admCond = "3";
            }
            item.setAdmCondType(admCond);
            if (mainFlag) {
                item.setMaindiagFlag("1");
            } else {
                item.setMaindiagFlag("0");
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 是否为 4101A 应上送的出院诊断行。
     *
     * @param row 首页诊断行
     * @return true 出院主诊/其他
     */
    private boolean isDischargeDiagRow(HomepageDiagnosis row) {
        String type = trimToNull(row.getDiagTypeCode());
        if (HomepageDiagType.DISCHARGE_MAIN.getCode().equals(type)) {
            return true;
        }
        return HomepageDiagType.DISCHARGE_OTHER.getCode().equals(type);
    }

    /**
     * 老系统 {@code needYbUp=0} 不上送结算清单诊断。
     *
     * @param row 诊断行
     * @return true 表示跳过
     */
    private boolean isNeedYbUpSkipped(HomepageDiagnosis row) {
        return "0".equals(trimToNull(row.getNeedYbUp()));
    }

    /**
     * 出院行是否按主诊断上送（行标志优先，否则看出院主诊类型）。
     *
     * @param row 出院诊断行
     * @return true 主诊
     */
    private boolean resolveDischargeMainFlag(HomepageDiagnosis row) {
        String mainFlag = trimToNull(row.getMaindiagFlag());
        if (mainFlag != null) {
            return "1".equals(mainFlag);
        }
        return HomepageDiagType.DISCHARGE_MAIN.getCode().equals(trimToNull(row.getDiagTypeCode()));
    }

    /**
     * 4101A {@code diag_type}：对齐老结算清单（1 西医 / 2 中医主病 / 3 中医主证）。
     *
     * @param isTcm    是否中医目录
     * @param mainFlag 是否主诊
     * @return 平台 diag_type
     */
    private String resolve4101ADiagType(boolean isTcm, boolean mainFlag) {
        if (!isTcm) {
            return "1";
        }
        if (mainFlag) {
            return "2";
        }
        return "3";
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
        Map<String, MedcasDualCodeResolveResult> dualByClinic =
                medcasDualCodeResolveSupport.resolveProcedureBatch(clinicCodes);
        List<MedcasSettleListOprnInfo> result = new ArrayList<>();
        for (HomepageProcedure row : sorted) {
            String clinicCode = trimToNull(row.getOprnOprtCode());
            if (clinicCode == null) {
                continue;
            }
            // 出站必须医保码；无对照抛错
            String hint = clinicCode;
            if (trimToNull(row.getOprnOprtName()) != null) {
                hint = clinicCode + " " + row.getOprnOprtName().trim();
            }
            String ybNo = medcasDualCodeResolveSupport.requireYbOutbound(dualByClinic, clinicCode, hint);
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
     * Integer → 上报用数字文本；null 按老系统 {@code ifnull(...,0)} 输出 {@code "0"}。
     *
     * @param value 护理天数；null 表示首页未填
     * @return 非空数字文本
     */
    private String intTextOrZero(Integer value) {
        if (value == null) {
            return "0";
        }
        return String.valueOf(value);
    }

    /**
     * 取首个非空白文本（4101A bill_code 末档兜底链）。
     *
     * @param candidates 候选值，按优先级从左到右
     * @return 首个非空；皆空则 null
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String trimmed = trimToNull(candidate);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
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
