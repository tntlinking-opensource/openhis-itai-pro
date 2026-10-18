/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.mapper;

import com.zxplat.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.zxplat.application.shared.mrms.MedcasDualCodeResolveResult;
import com.zxplat.application.shared.mrms.MedcasDualCodeResolveSupport;
import com.zxplat.application.shared.mrms.MedcasOrgCatyOutboundSupport;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.zxplat.domain.mrms.caserecord.entity.HomepageBld;
import com.zxplat.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.zxplat.domain.mrms.caserecord.entity.HomepageIcu;
import com.zxplat.domain.mrms.caserecord.entity.HomepageProcedure;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.HomepageDiagType;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListBldInfo;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListDiseInfo;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListIcuInfo;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListOprnInfo;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import com.zxplat.domain.infrastructure.integration.insurance.medcas.dto.MedcasSettleListSetlinfo;
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

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 → 医保 4101A {@link MedcasSettleListPayload} 映射。
 *
 * <p>组装规范必填与条件必填字段；ICU/输血有行则上送，无则空数组（对齐老 IN_HOS_*）。
 * 护理四天 null→{@code "0"}；{@code bill_code} 无博思批次码时按发票号/流水/住院号/病案号兜底；
 * {@code ntly}/{@code prfs} 空默认 {@code 01}/{@code 90}；{@code days_rinp_flag_31} 空默认 {@code 1}
 * （对齐老 {@code HAS_PLAN31} null→1）；{@code hi_paymtd} 由 {@code psn_setlway} 映射（本院固定 01→1）；
 * {@code chfpdr_code}←主治 {@code Practitioner.ybNo}；手术 {@code anst_way} 空默认 {@code 9}。
 * 诊断 {@code adm_cond_type} 空默认 {@code "2"}（对齐老 {@code ifnull(IN_ILL_STATUS,'2')}）。
 * {@code diseinfo} <strong>仅出院诊断</strong>（等价老 {@code SERVICE_TYPE=2} 上传源）；
 * {@code diag_type} 1 西医 / 2 中医主病 / 3 中医主证；主诊按西医/中医各组首条重排
 * （对齐老 {@code sendDrgDiagnose}）；{@code diag_code} 优先医保码，无对照回落落库码
 * （对齐老 {@code ifnull(DRG_CODE,YB_CODE,icd10)}，本院库 DRG 恒空）。</p>
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
        List<HomepageIcu> icus = homepageDomainService.listIcus(record.getId());
        List<HomepageBld> blds = homepageDomainService.listBlds(record.getId());

        MedcasSettleListSetlinfo setl = new MedcasSettleListSetlinfo();
        setl.setPsnNo(trimToNull(insCtx.getPsnNo()));
        setl.setMdtrtId(trimToNull(insCtx.getMdtrtId()));
        setl.setSetlId(trimToNull(insCtx.getSetlId()));
        setl.setMedcasno(trimToNull(record.getMedcasno()));
        // 国籍/职业：空默认 01 中国 / 90 其他（对齐老清单 Gate，避免 Validator 卡死）
        String ntly = trimToNull(homepage.getNtlyCode());
        if (ntly == null) {
            ntly = "01";
        }
        setl.setNtly(ntly);
        String prfs = trimToNull(homepage.getPrfsCode());
        if (prfs == null) {
            prfs = "90";
        }
        setl.setPrfs(prfs);
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
        // 入院科室（4101A 国家版本新增）：同口径出站医保码 + 名称
        setl.setAdmDeptCodg(medcasOrgCatyOutboundSupport.resolveOutboundCaty(
                homepage.getAdmCatyCode(), "adm_dept_codg"));
        setl.setAdmDeptName(trimToNull(homepage.getAdmCatyName()));
        setl.setOtpWmDise(trimToNull(homepage.getOtpWmDise()));
        setl.setWmDiseCode(trimToNull(homepage.getWmDiseCode()));
        setl.setOtpTcmDise(trimToNull(homepage.getOtpTcmDise()));
        setl.setTcmDiseCode(trimToNull(homepage.getTcmDiseCode()));
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
        // 31 天再住院计划：对齐老 HAS_PLAN31 null→「1」；目的有则上送
        String rinp = trimToNull(homepage.getRinp31Flag());
        if (rinp == null) {
            rinp = "1";
        }
        setl.setDaysRinpFlag31(rinp);
        setl.setDaysRinpPup31(trimToNull(homepage.getRinp31Purpose()));
        // 主诊医师码：主治医师 Practitioner.ybNo（无 id / 无 yb_no 则不上送，规范非必填）
        setl.setChfpdrCode(resolveChfpdrCode(homepage.getAtddrPractitionerId()));
        // 医保支付方式：本院结算 psn_setlway 固定 01（按项目）→ hi_paymtd=1
        setl.setHiPaymtd(mapHiPaymtdFromPsnSetlway("01"));
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
        payload.setIcuinfo(buildIcuinfo(icus));
        payload.setBldinfo(buildBldinfo(blds));
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
     * 诊断子表 → 4101A diseinfo（对齐老结算清单上传源）。
     *
     * <p>仅出院诊断（入院不上送）；显式 {@code needYbUp=0} 不上送（空按出院缺省「是」）。
     * 主诊按西医/中医各组首条重排（老 {@code sendDrgDiagnose}）；
     * {@code diag_code} 优先目录医保码，无对照回落落库码。</p>
     *
     * @param rows 诊断行
     * @return diseinfo
     */
    private List<MedcasSettleListDiseInfo> buildDiseinfo(List<HomepageDiagnosis> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageDiagnosis> sorted = new ArrayList<>(rows);
        // Pre：出院主诊类型优先，再按 sort_no（对齐老 type=2 按 SEQ_NO，且主诊在前）
        sorted.sort(Comparator.comparing(HomepageDiagnosis::getDiagTypeCode)
                .thenComparing(HomepageDiagnosis::getSortNo));
        List<HomepageDiagnosis> uploadRows = new ArrayList<>();
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : sorted) {
            // Gate：仅出院；needYbUp=0 跳过；无编码跳过
            if (!isDischargeDiagRow(row)) {
                continue;
            }
            if (isNeedYbUpSkipped(row)) {
                continue;
            }
            String clinic = trimToNull(row.getDiagCode());
            if (clinic == null) {
                continue;
            }
            uploadRows.add(row);
            clinicCodes.add(clinic);
        }
        Map<String, MedcasDualCodeResolveResult> dualByClinic =
                medcasDualCodeResolveSupport.resolveDiagnosisBatch(clinicCodes);
        // Pre：中西医归类，驱动 diag_type 1/2/3 与主诊分组
        Map<String, String> kindByCode =
                conditionDefinitionQueryService.mapDiagnosisKindByConditionCodes(clinicCodes);
        // Pre：对齐老 sendDrgDiagnose——西医组、中医组各强制首条 isMain=1
        boolean westernMainAssigned = false;
        boolean tcmMainAssigned = false;
        List<MedcasSettleListDiseInfo> result = new ArrayList<>();
        for (HomepageDiagnosis row : uploadRows) {
            String clinicCode = trimToNull(row.getDiagCode());
            if (clinicCode == null) {
                continue;
            }
            boolean isTcm = DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                    kindByCode.get(clinicCode));
            boolean mainFlag;
            // 业务分支：中医组首条主病；西医组首条主诊；其余强制非主
            if (isTcm) {
                if (tcmMainAssigned) {
                    mainFlag = false;
                } else {
                    mainFlag = true;
                    tcmMainAssigned = true;
                }
            } else if (westernMainAssigned) {
                mainFlag = false;
            } else {
                mainFlag = true;
                westernMainAssigned = true;
            }
            MedcasSettleListDiseInfo item = new MedcasSettleListDiseInfo();
            item.setDiagType(resolve4101ADiagType(isTcm, mainFlag));
            // Asm：diag_code = 医保码优先，无对照回落落库码（老 ifnull(DRG/YB,icd10)）
            item.setDiagCode(resolve4101ADiagOutbound(dualByClinic, kindByCode, clinicCode));
            item.setDiagName(resolve4101ADiagName(dualByClinic, row, clinicCode));
            // 入院病情：空默认 2（老 ifnull(IN_ILL_STATUS,'2')）
            String admCond = trimToNull(row.getAdmCondCode());
            if (admCond == null) {
                admCond = "2";
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
     * 显式 {@code needYbUp=0} 不上送结算清单诊断。
     *
     * <p>空值按出院缺省「是」上送（与首页前端/产品口径一致）；老 type=2 拷贝源亦跳过 0。</p>
     *
     * @param row 诊断行
     * @return true 表示跳过
     */
    private boolean isNeedYbUpSkipped(HomepageDiagnosis row) {
        return "0".equals(trimToNull(row.getNeedYbUp()));
    }

    /**
     * 4101A {@code diag_code}：中医用落库码；西医优先目录医保码，无对照回落落库码。
     *
     * <p>对齐老 {@code ifnull(ifnull(DRG_CODE,YB_CODE),icd10)}（本院库 DRG_CODE 全空，
     * 实效为 YB→ICD）。4101A 不抛未对码，避免与老静默回落/跳过口径冲突。</p>
     *
     * @param dualByClinic 批量双码解析
     * @param kindByCode   中西医归类
     * @param clinicCode   首页落库码
     * @return 非空出站码
     */
    private String resolve4101ADiagOutbound(
            Map<String, MedcasDualCodeResolveResult> dualByClinic,
            Map<String, String> kindByCode,
            String clinicCode) {
        // 业务分支：中医不对国临→医保，出站用目录自身编码
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                kindByCode.get(clinicCode))) {
            return clinicCode;
        }
        MedcasDualCodeResolveResult hit = dualByClinic.get(clinicCode);
        // 业务分支：已对医保码则出站 yb_no
        if (hit != null && hit.isMapped() && trimToNull(hit.getOutboundCode()) != null) {
            return hit.getOutboundCode().trim();
        }
        // 无对照：回落落库码（老 icd10 末档）
        return clinicCode;
    }

    /**
     * 4101A {@code diag_name}：优先目录医保名，否则首页名，并拼接说明（老 WinMessage 名+remark）。
     *
     * @param dualByClinic 批量双码解析
     * @param row          首页诊断行
     * @param clinicCode   落库码
     * @return 诊断名称；可空
     */
    private String resolve4101ADiagName(
            Map<String, MedcasDualCodeResolveResult> dualByClinic,
            HomepageDiagnosis row,
            String clinicCode) {
        MedcasDualCodeResolveResult dualHit = dualByClinic.get(clinicCode);
        String name = null;
        if (dualHit != null) {
            name = trimToNull(dualHit.getYbName());
        }
        if (name == null) {
            name = trimToNull(row.getDiagName());
        }
        String remark = trimToNull(row.getDiagRemark());
        // 无名称也无备注：保持 null
        if (name == null && remark == null) {
            return null;
        }
        // 仅备注：与老系统 concat 行为一致时名称可为空串起头
        if (name == null) {
            return remark;
        }
        if (remark == null) {
            return name;
        }
        return name + remark;
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
     * 手术子表 → 4101A oprninfo（与 4401 同口径：同一条 PD 上的 yb_no；空列表→{@code []}）。
     *
     * <p>首页存国临；出站读该条手术目录 {@code yb_no}。国临空或 yb_no 空则整单失败（EX-MRMS-UP-OP-201/202）。</p>
     *
     * @param rows 手术行
     * @return oprninfo
     */
    private List<MedcasSettleListOprnInfo> buildOprninfo(List<HomepageProcedure> rows) {
        // 无手术行：上送空数组（非 null）
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageProcedure row : sorted) {
            String clinic = trimToNull(row.getOprnOprtCode());
            // 仅收集非空国临码以便按同一条 PD 取 yb_no；空码行在下方抛 EX-202
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, MedcasDualCodeResolveResult> dualByClinic =
                medcasDualCodeResolveSupport.resolveProcedureBatch(clinicCodes);
        List<MedcasSettleListOprnInfo> result = new ArrayList<>();
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
            // Gate：空码→EX-202；同一条 PD 无 yb_no→EX-201（与 4401 同纪律）
            String ybNo = medcasDualCodeResolveSupport.requireProcedureYbOutbound(
                    dualByClinic, clinicCode, hint);
            MedcasSettleListOprnInfo item = new MedcasSettleListOprnInfo();
            // 业务分支：主手术→类型 1，其余→类型 2
            if ("1".equals(trimToNull(row.getMainOprnFlag()))) {
                item.setOprnOprtType("1");
            } else {
                item.setOprnOprtType("2");
            }
            item.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            item.setOprnOprtCode(ybNo);
            // 麻醉方式：空默认 9=其他（对齐老清单；Validator 要求非空）
            String anstWay = trimToNull(row.getAnstWayCode());
            if (anstWay == null) {
                anstWay = "9";
            }
            item.setAnstWay(anstWay);
            item.setOperDrCode(trimToNull(row.getOperDrCode()));
            item.setAnstDrCode(trimToNull(row.getAnstDrCode()));
            item.setOprnOprtBegntime(formatDate(row.getOprnDate()));
            result.add(item);
        }
        return result;
    }

    /**
     * 主诊医师 {@code chfpdr_code}：主治医师主键 → {@code Practitioner.ybNo}。
     *
     * <p>无主键或未维护医保医师码时返回 null（4101A 规范非必填，不上送空串）。</p>
     *
     * @param atddrPractitionerId 首页主治医师主键
     * @return 医保医师码；不可解析则 null
     */
    private String resolveChfpdrCode(Long atddrPractitionerId) {
        if (atddrPractitionerId == null) {
            return null;
        }
        Practitioner doctor = practitionerQueryService.findById(atddrPractitionerId);
        if (doctor == null) {
            return null;
        }
        return trimToNull(doctor.getYbNo());
    }

    /**
     * 个人结算方式 {@code psn_setlway} → 4101A {@code hi_paymtd}（对齐老 WinMessage 映射）。
     *
     * <p>{@code 01}→1 按项目；{@code 02}→7 按定额；{@code 0201}→2 单病种；
     * {@code 0202}→5 按床日；空或其他→9 其他。ZXPlat 住院结算固定 {@code 01}。</p>
     *
     * @param psnSetlway 个人结算方式码
     * @return hi_paymtd 字典码
     */
    private String mapHiPaymtdFromPsnSetlway(String psnSetlway) {
        String code = trimToNull(psnSetlway);
        if (code == null || "01".equals(code)) {
            return "1";
        }
        if ("02".equals(code)) {
            return "7";
        }
        if ("0201".equals(code)) {
            return "2";
        }
        if ("0202".equals(code)) {
            return "5";
        }
        return "9";
    }

    /**
     * 重症监护子表 → 4101A icuinfo（对齐老 IN_HOS_ICUINFO 字段直出）。
     *
     * <p>时长空且进出齐全时按进出差补算 {@code 天/时/分}；无行→{@code []}。</p>
     *
     * @param rows ICU 行
     * @return icuinfo
     */
    private List<MedcasSettleListIcuInfo> buildIcuinfo(List<HomepageIcu> rows) {
        // 无 ICU 行：上送空数组（非 null），与老结算清单同口径
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageIcu> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageIcu::getSortNo, Comparator.nullsLast(Integer::compareTo)));
        List<MedcasSettleListIcuInfo> result = new ArrayList<>();
        // 按序号组装 icuinfo：病房类型/进出时间直出，时长可补算
        for (HomepageIcu row : sorted) {
            MedcasSettleListIcuInfo item = new MedcasSettleListIcuInfo();
            item.setScsCutdWardType(trimToNull(row.getScsCutdWardType()));
            item.setScsCutdInpoolTime(formatDateTime(row.getScsCutdInpoolTime()));
            item.setScsCutdExitTime(formatDateTime(row.getScsCutdExitTime()));
            String dura = trimToNull(row.getScsCutdSumDura());
            // 时长未填则按进出差补算（有进出时比老页默认 01/00/00 更准）
            if (dura == null) {
                dura = computeIcuSumDura(row.getScsCutdInpoolTime(), row.getScsCutdExitTime());
            }
            item.setScsCutdSumDura(dura);
            result.add(item);
        }
        return result;
    }

    /**
     * 输血子表 → 4101A bldinfo（对齐老 IN_HOS_BLDINFO）。
     *
     * @param rows 输血行
     * @return bldinfo；无行→{@code []}
     */
    private List<MedcasSettleListBldInfo> buildBldinfo(List<HomepageBld> rows) {
        // 无输血行：上送空数组（非 null）
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<HomepageBld> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageBld::getSortNo, Comparator.nullsLast(Integer::compareTo)));
        List<MedcasSettleListBldInfo> result = new ArrayList<>();
        // 按序号直出品种/量/单位（不做字典转码）
        for (HomepageBld row : sorted) {
            MedcasSettleListBldInfo item = new MedcasSettleListBldInfo();
            item.setBldCat(trimToNull(row.getBldCat()));
            item.setBldAmt(trimToNull(row.getBldAmt()));
            item.setBldUnt(trimToNull(row.getBldUnt()));
            result.add(item);
        }
        return result;
    }

    /**
     * 按进出 ICU 时间差拼 {@code 天/时/分}（不足位补 0 前导，例 {@code 01/03/05}）。
     *
     * @param inpool 进入时间
     * @param exit   退出时间
     * @return 时长文本；缺任一侧则 null
     */
    private String computeIcuSumDura(LocalDateTime inpool, LocalDateTime exit) {
        // 进出任一侧缺失则无法补算时长
        if (inpool == null || exit == null) {
            return null;
        }
        // Gate：退出早于进入时不上送假时长
        if (exit.isBefore(inpool)) {
            return null;
        }
        long totalMinutes = java.time.Duration.between(inpool, exit).toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d/%02d/%02d", days, hours, minutes);
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
