/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.bridge.prescription.context;

import com.openhis.elep.domain.config.ElepContractConfig;
import com.openhis.elep.domain.config.ElepContractConfigLoader;
import com.openhis.elep.domain.prescription.access.ElepEncounterContextAccess;
import com.openhis.elep.domain.prescription.command.ElepEncounterContextQuery;
import com.openhis.elep.domain.prescription.constants.ElepPrescriptionConstants;
import com.openhis.elep.domain.prescription.queries.model.ElepPlatformMapQueryModel;
import com.openhis.elep.domain.prescription.result.ElepEncounterContextResult;
import com.openhis.elep.domain.prescription.result.ElepPharmacistReviewerResult;
import com.openhis.elep.domain.prescription.service.ElepEncounterPlatformMapQueryService;
import com.openhis.elep.gateway.support.ElepCurrentOperatorResolver;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 电子处方就诊上下文：只读 HIS 就诊/患者/医师/科室 + 挂号 map(11)，禁止写 HIS 与共享 map。
 */
@Component
@RequiredArgsConstructor
public class ElepEncounterContextLoader implements ElepEncounterContextAccess {

    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final ElepEncounterPlatformMapQueryService platformMapQueryService;
    private final ContractQueryService contractQueryService;
    private final ElepCurrentOperatorResolver operatorResolver;

    /**
     * 装载当前就诊只读上下文并判定能否开方。
     *
     * @param query 就诊、患者、契约
     * @return 上下文；{@code allowed=false} 时仍尽量带出已有展示字段
     */
    @Override
    public ElepEncounterContextResult load(ElepEncounterContextQuery query) {
        ElepEncounterContextResult result = new ElepEncounterContextResult();
        if (query == null || query.getEncounterId() == null || query.getPatientId() == null) {
            return result.setAllowed(false).setBlockReason("请先选择当前医保就诊患者");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.loadEncounterById(query.getEncounterId());
        if (!query.getPatientId().equals(encounter.getPatientId())) {
            return result.setAllowed(false).setBlockReason("患者与就诊不一致，禁止开电子处方");
        }
        result.setEncounterId(encounter.getId());
        result.setPatientId(encounter.getPatientId());
        result.setEncounterBusNo(trimToNull(encounter.getBusNo()));

        PatientDetailQueryModel patientDetail = patientQueryService.loadPatientDetail(query.getPatientId());
        PatientCoreQueryModel patient = patientDetail.getPatient();
        fillPatient(result, patient);
        fillDept(result, encounter.getOrganizationId());
        fillDoctor(result);
        fillVisitTime(result, encounter);
        fillPlatformMap(result, encounter);
        fillContractFromMap(result, query.getContractBusNo());
        fillFixmedins(result, result.getContractBusNo());
        applyOpenGate(result);
        return result;
    }

    /**
     * 按当前登录用户装载审方药师（{@code adm_practitioner.yb_no}）。
     *
     * @return 药师投影；缺医保码时 {@code allowed=false}
     */
    @Override
    public ElepPharmacistReviewerResult loadCurrentPharmacist() {
        ElepPharmacistReviewerResult result = new ElepPharmacistReviewerResult();
        Long userId = operatorResolver.requireOperatorId();
        Practitioner pharmacist = practitionerQueryService.findByUserId(userId);
        if (pharmacist == null) {
            return result.setAllowed(false).setBlockReason("当前账号未关联医务人员，无法审方");
        }
        result.setPharName(trimToNull(pharmacist.getName()));
        result.setPharCode(trimToNull(pharmacist.getYbNo()));
        result.setPharPracCertNo(trimToNull(pharmacist.getPharPracCertNo()));
        if (pharmacist.getDrProfttlCode() != null) {
            result.setPharProfttlCodg(String.valueOf(pharmacist.getDrProfttlCode()));
            result.setPharProfttlName(String.valueOf(pharmacist.getDrProfttlCode()));
        }
        fillPharmacistDept(result, pharmacist.getOrgId());
        if (isEmpty(result.getPharCode())) {
            return result.setAllowed(false)
                    .setBlockReason("审方药师未维护国家医保码，请在用户管理中维护");
        }
        return result.setAllowed(true);
    }

    /**
     * 按文档约定拦截：自费/无 map(11)、缺医师或科室医保码、缺契约。
     * 开发阶段先放开上述门禁，只保证就诊上下文能装上，便于走通选诊断、加药、存草稿。
     *
     * @param result 已填充的上下文
     */
    private void applyOpenGate(ElepEncounterContextResult result) {
        // 开发阶段：不因医保挂号号、医师/科室医保码、elep-contract 缺失拦开方界面。
        result.setBlockReason(null);
        result.setAllowed(true);
    }

    /**
     * 填充患者姓名、证件、年龄与平台性别。
     *
     * @param result  上下文出参
     * @param patient 患者核心模型
     */
    private void fillPatient(ElepEncounterContextResult result, PatientCoreQueryModel patient) {
        if (patient == null) {
            throw new BusinessException("ELEP_PATIENT_REQUIRED", "患者不存在");
        }
        result.setPatientName(trimToNull(patient.getName()));
        result.setGenderEnum(trimToNull(patient.getGenderEnum()));
        result.setGend(toPlatformGend(patient.getGenderEnum()));
        result.setIdCard(trimToNull(patient.getIdCard()));
        result.setPsnCertType(ElepPrescriptionConstants.PSN_CERT_TYPE_ID_CARD);
        result.setMdtrtCertType(ElepPrescriptionConstants.MDTRT_CERT_TYPE_ID_CARD);
        result.setMdtrtCertNo(trimToNull(patient.getIdCard()));
        result.setPatnAge(toAgeYears(patient.getBirthDate()));
    }

    /**
     * 填充就诊科室名称与医保码。
     *
     * @param result         上下文出参
     * @param organizationId 就诊科室主键
     */
    private void fillDept(ElepEncounterContextResult result, Long organizationId) {
        if (organizationId == null) {
            return;
        }
        Organization dept = organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (dept == null) {
            return;
        }
        result.setDeptName(trimToNull(dept.getName()));
        result.setDeptYbNo(trimToNull(dept.getYbNo()));
    }

    /**
     * 按当前登录用户填充开方医师及医师所属科室。
     *
     * @param result 上下文出参
     */
    private void fillDoctor(ElepEncounterContextResult result) {
        Long userId = operatorResolver.requireOperatorId();
        Practitioner doctor = practitionerQueryService.findByUserId(userId);
        if (doctor == null) {
            result.setBlockReason("当前账号未关联医师身份，无法开电子处方");
            return;
        }
        result.setDoctorName(trimToNull(doctor.getName()));
        result.setDoctorYbNo(trimToNull(doctor.getYbNo()));
        if (doctor.getDrProfttlCode() != null) {
            result.setDoctorTitleCode(String.valueOf(doctor.getDrProfttlCode()));
            result.setDoctorTitleName(String.valueOf(doctor.getDrProfttlCode()));
        }
        if (doctor.getOrgId() == null) {
            return;
        }
        Organization doctorDept = organizationQueryService.loadOrganizationByIdForDisplay(doctor.getOrgId());
        if (doctorDept == null) {
            return;
        }
        result.setDoctorDeptName(trimToNull(doctorDept.getName()));
        result.setDoctorDeptYbNo(trimToNull(doctorDept.getYbNo()));
    }

    /**
     * 填充就诊时间与门诊住院标识。
     *
     * @param result    上下文出参
     * @param encounter 就诊详情
     */
    private void fillVisitTime(ElepEncounterContextResult result, EncounterDetailQueryModel encounter) {
        result.setOtpIptFlag(toOtpIptFlag(encounter.getClassEnum()));
        LocalDateTime visitTime = encounter.getReceptionTime();
        if (visitTime == null) {
            visitTime = encounter.getStartTime();
        }
        if (visitTime == null) {
            visitTime = LocalDateTime.now();
        }
        result.setMdtrtTime(visitTime.format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER));
    }

    /**
     * 填充定点机构编码/名称（仅读 elep-contract，不从前端收密钥）。
     *
     * @param result         上下文出参
     * @param contractBusNo  挂号 map 回填的契约 bus_no
     */
    private void fillFixmedins(ElepEncounterContextResult result, String contractBusNo) {
        String busNo = trimToNull(contractBusNo);
        if (busNo == null) {
            return;
        }
        ElepContractConfig config = ElepContractConfigLoader.getByBusNo(busNo);
        if (config == null || !config.isEnabled()) {
            return;
        }
        result.setFixmedinsCode(trimToNull(config.getFixmedinsCode()));
        result.setFixmedinsName(trimToNull(config.getFixmedinsName()));
    }

    /**
     * 只读挂号 map(11)，禁止补 2201、禁止写共享表。
     *
     * @param result    上下文出参
     * @param encounter 就诊详情
     */
    private void fillPlatformMap(ElepEncounterContextResult result, EncounterDetailQueryModel encounter) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        ElepPlatformMapQueryModel map = platformMapQueryService
                .findGeneralOutpatientMap(encounter.getId(), tenantId)
                .orElse(null);
        if (map == null) {
            return;
        }
        result.setMdtrtId(trimToNull(map.getMdtrtId()));
        result.setPsnNo(trimToNull(map.getPsnNo()));
        String iptOtpNo = trimToNull(map.getIptOtpNo());
        if (iptOtpNo == null) {
            iptOtpNo = trimToNull(encounter.getBusNo());
        }
        result.setIptOtpNo(iptOtpNo);
        result.setContractId(map.getContractId());
    }

    /**
     * 契约只取挂号 map 上的费用性质，不让医生选省医保/市医保/工伤。
     *
     * @param result             已带 map 契约主键的上下文
     * @param fallbackContractBusNo 仅 map 无契约时的兜底 bus_no
     */
    private void fillContractFromMap(ElepEncounterContextResult result, String fallbackContractBusNo) {
        if (result.getContractId() != null) {
            Contract contract = contractQueryService.findOptionalContractEntityById(result.getContractId())
                    .orElse(null);
            if (contract != null) {
                result.setContractBusNo(trimToNull(contract.getBusNo()));
            }
        }
        if (!isEmpty(result.getContractBusNo())) {
            return;
        }
        result.setContractBusNo(trimToNull(fallbackContractBusNo));
    }

    /**
     * 填充审方药师科室。
     *
     * @param result 药师出参
     * @param orgId  药师默认科室
     */
    private void fillPharmacistDept(ElepPharmacistReviewerResult result, Long orgId) {
        if (orgId == null) {
            return;
        }
        Organization dept = organizationQueryService.loadOrganizationByIdForDisplay(orgId);
        if (dept == null) {
            return;
        }
        result.setPharDeptName(trimToNull(dept.getName()));
        result.setPharDeptCode(trimToNull(dept.getYbNo()));
    }

    /**
     * HIS 行政性别转平台 {@code gend}。
     *
     * @param genderEnum HIS 性别 code
     * @return 1 男 / 2 女 / 9 未知
     */
    private static String toPlatformGend(String genderEnum) {
        if (ElepPrescriptionConstants.HIS_GENDER_MALE.equals(genderEnum)) {
            return ElepPrescriptionConstants.GEND_MALE;
        }
        if (ElepPrescriptionConstants.HIS_GENDER_FEMALE.equals(genderEnum)) {
            return ElepPrescriptionConstants.GEND_FEMALE;
        }
        return ElepPrescriptionConstants.GEND_UNKNOWN;
    }

    /**
     * HIS 就诊类别转门诊住院标识。
     *
     * @param classEnum 就诊类别
     * @return 1 门诊 / 2 住院
     */
    private static String toOtpIptFlag(String classEnum) {
        if (ElepPrescriptionConstants.HIS_ENCOUNTER_CLASS_AMB.equals(classEnum)) {
            return ElepPrescriptionConstants.OTP_IPT_FLAG_OUTPATIENT;
        }
        return ElepPrescriptionConstants.OTP_IPT_FLAG_INPATIENT;
    }

    /**
     * 按出生日期计算周岁。
     *
     * @param birthDate 出生日期
     * @return 年龄字符串；未知则空
     */
    private static String toAgeYears(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        int years = Period.between(birthDate, LocalDate.now()).getYears();
        if (years < 0) {
            return "0";
        }
        return String.valueOf(years);
    }
}
