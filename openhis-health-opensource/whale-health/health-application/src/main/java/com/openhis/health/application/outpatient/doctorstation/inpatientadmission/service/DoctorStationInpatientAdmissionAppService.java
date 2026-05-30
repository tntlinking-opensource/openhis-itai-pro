/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.service;

import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.mapper.DoctorStationInpatientAdmissionMapper;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request.CheckInpatientAdmissionExistsRequest;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request.RequestInpatientAdmissionRequest;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.CheckInpatientAdmissionExistsResponse;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.response.RequestInpatientAdmissionResponse;
import com.openhis.health.domain.administration.encounter.command.RequestInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OpenInpatientLineQuery;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 门诊医生站住院申请编排：门急诊转住院、防重校验与意向科室/病区选点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationInpatientAdmissionAppService {

    private final EncounterQueryService encounterQueryService;
    private final EncounterDomainService encounterDomainService;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final DoctorStationInpatientAdmissionMapper doctorStationInpatientAdmissionMapper;
    private final ICurrentUser currentUser;

    /**
     * 发起住院申请（INSERT 新 IMP+PLANNED 住院线，不修改源门诊就诊）。
     *
     * @param request 住院申请表单
     * @return 新建住院就诊标识与业务号
     * @throws BusinessException 参数缺失、源就诊不可转住院、重复申请、科室/病区无效或患者不一致
     */
    public RequestInpatientAdmissionResponse requestInpatientAdmission(RequestInpatientAdmissionRequest request) {
        this.validateRequestInpatientAdmissionRequest(request);
        Long ambEncounterId = request.getAmbEncounterId();
        // 加载源就诊并断言其为可转住院的门急诊线（BR-IP-REQ-001）。
        EncounterDetailQueryModel ambEncounter = encounterQueryService.mustGetEncounter(ambEncounterId);
        encounterQueryService.assertAmbForInpatientReq(ambEncounterId);
        Long resolvedPatientId = ambEncounter.getPatientId();
        // 若前端传入患者主键，须与源就诊头表一致，防止跨患者误申请（BR 患者一致性）。
        if (request.getPatientId() != null) {
            if (!request.getPatientId().equals(resolvedPatientId)) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "患者与源就诊不一致，无法办理住院");
            }
        }
        // 同一门诊锚点不得存在未取消住院线（BR-IP-REQ-002 / EX-IP-REQ-201）。
        OpenInpatientLineQuery existingLine = encounterQueryService.findOpenInpatientLine(ambEncounterId);
        if (existingLine.isExists()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "该门诊就诊已存在住院申请，请勿重复提交");
        }
        // 意向住院科室须存在且可用于展示选点。
        this.assertTargetDepartmentExists(request.getTargetDepartmentId());
        // 若填写意向病区，须为启用中的 WARD 形态位置。
        if (request.getWardId() != null) {
            this.assertWardValid(request.getWardId());
        }
        RequestInpatientAdmissionCommand command =
            doctorStationInpatientAdmissionMapper.toRequestInpatientAdmissionCommand(request, resolvedPatientId);
        Long inpatientEncounterId = encounterDomainService.requestInpatientAdmission(command);
        // 回读新建住院线的业务号（BR-IP-REQ-009 独立住院前缀），供前端展示住院号。
        OpenInpatientLineQuery createdLine = encounterQueryService.findOpenInpatientLine(ambEncounterId);
        if (!createdLine.isExists() || createdLine.getBusNo() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "住院申请已落库但无法回读住院业务号");
        }
        String inpatientBusNo = createdLine.getBusNo();
        log.info(
            "住院申请已提交 ambEncounterId={} inpatientEncounterId={} requestDoctorId={} operatorId={} sessionPractitionerId={}",
            ambEncounterId,
            inpatientEncounterId,
            request.getRequestDoctorId(),
            request.getOperatorId(),
            currentUser.getCurrentPractitionerId()
        );
        return doctorStationInpatientAdmissionMapper.toRequestInpatientAdmissionResponse(
            inpatientEncounterId,
            inpatientBusNo,
            true
        );
    }

    /**
     * 按门诊就诊查询是否已存在未取消的住院申请（只读，供按钮禁用）。
     *
     * @param request 含门诊就诊锚点
     * @return 是否存在住院线及状态码
     * @throws BusinessException 门诊就诊标识为空时抛出
     */
    public CheckInpatientAdmissionExistsResponse checkInpatientAdmissionExistsByAmbEncounter(
        CheckInpatientAdmissionExistsRequest request
    ) {
        this.validateCheckInpatientAdmissionExistsRequest(request);
        // 只读查询未取消住院线，不写库。
        OpenInpatientLineQuery line = encounterQueryService.findOpenInpatientLine(request.getAmbEncounterId());
        // 装配 exists / inpatientEncounterId / statusEnum。
        return doctorStationInpatientAdmissionMapper.toCheckInpatientAdmissionExistsResponse(line);
    }

    /**
     * 住院申请请求体与入院诊断明细校验。
     *
     * @param request 请求体
     * @throws BusinessException 必填项缺失或诊断结构不合法
     */
    private void validateRequestInpatientAdmissionRequest(RequestInpatientAdmissionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getAmbEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "门诊就诊标识不能为空");
        }
        if (request.getTargetDepartmentId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "意向住院科室不能为空");
        }
        if (request.getRequestDoctorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "申请医师不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "操作人不能为空");
        }
        this.validateAdmissionDiagnoses(request.getAdmissionDiagnoses());
    }

    /**
     * 查询请求参数校验。
     *
     * @param request 请求体
     * @throws BusinessException 门诊就诊标识为空
     */
    private void validateCheckInpatientAdmissionExistsRequest(CheckInpatientAdmissionExistsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getAmbEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "门诊就诊标识不能为空");
        }
    }

    /**
     * 入院诊断列表结构校验：非空、主诊断标记、新开/引用二选一。
     *
     * @param diagnoses 入院诊断行
     * @throws BusinessException 列表为空或行内字段不合法
     */
    private void validateAdmissionDiagnoses(List<AdmissionDiagnosisSubmitDto> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断不能为空");
        }
        boolean hasPrimary = false;
        for (AdmissionDiagnosisSubmitDto row : diagnoses) {
            if (row == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断行不能为空");
            }
            if (row.getPrimaryFlag() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "是否主诊断不能为空");
            }
            if (row.getPrimaryFlag()) {
                hasPrimary = true;
            }
            if (row.getConditionId() == null) {
                if (row.getConditionDefinitionId() == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                        "新开入院诊断须指定诊断目录"
                    );
                }
            }
        }
        if (!hasPrimary) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断须至少一条主诊断");
        }
    }

    /**
     * 断言意向住院科室存在。
     *
     * @param targetDepartmentId 科室主键
     * @throws BusinessException 科室不存在
     */
    private void assertTargetDepartmentExists(Long targetDepartmentId) {
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(targetDepartmentId);
        // 意向科室须在机构主数据中存在，避免写入无效 organization_id。
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_NOT_FOUND, "意向住院科室不存在");
        }
    }

    /**
     * 断言意向病区为启用中的 WARD 形态位置。
     *
     * @param wardId 病区位置主键
     * @throws BusinessException 位置不存在、非病区或已停用
     */
    private void assertWardValid(Long wardId) {
        Optional<Location> locationOpt = locationQueryService.findOptionalById(wardId);
        // 病区位置须存在（SDD §2.3 loadLocationById 等价只读能力）。
        if (locationOpt.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "意向病区不存在");
        }
        Location location = locationOpt.get();
        // 形态须为 WARD，禁止误选床位或药房等非病区位置。
        if (!LocationForm.WARD.getCode().equals(location.getFormEnum())) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "意向位置须为病区形态");
        }
        // 病区须处于启用态，避免写入已停用位置轨迹。
        if (!LocationStatus.ACTIVE.getCode().equals(location.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "意向病区未启用");
        }
    }
}
