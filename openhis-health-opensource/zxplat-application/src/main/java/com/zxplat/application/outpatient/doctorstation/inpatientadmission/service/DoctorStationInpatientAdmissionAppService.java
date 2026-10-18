/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.service;

import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.application.shared.inpatient.AdmissionDiagnosisSubmitGate;
import com.zxplat.application.shared.inpatient.EncounterInpatientAdmissionOrchestrator;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.dto.AdmissionDiagnosisSubmitDto;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.mapper.DoctorStationInpatientAdmissionMapper;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.request.CheckInpatientAdmissionExistsRequest;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.request.RequestInpatientAdmissionRequest;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.request.WithdrawInpatientAdmissionRequest;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.CheckInpatientAdmissionExistsResponse;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.RequestInpatientAdmissionResponse;
import com.zxplat.application.outpatient.doctorstation.inpatientadmission.response.WithdrawInpatientAdmissionResponse;
import com.zxplat.domain.administration.encounter.command.RequestInpatientAdmissionCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.OpenInpatientLineQuery;
import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ChargeIntent;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.LocationStatus;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.PaymentType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 门诊医生站住院申请编排：门诊转住院、防重校验、撤回待登记申请、意向科室/病区选点、办理意向与可选意向预交金草稿。
 *
 * <p>对应 AS-OP-DOR-005；办理意向仅提示款台；意向预交金仅落 DRAFT，登记确认见 AS-IP-FIN-001。
 * 撤回仅允许 {@code PLANNED}：直接软删住院线并作废意向预交金草稿，款台登记队列随即不可见。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationInpatientAdmissionAppService {

    private final EncounterQueryService encounterQueryService;
    private final EncounterDomainService encounterDomainService;
    private final EncounterInpatientAdmissionOrchestrator encounterInpatientAdmissionOrchestrator;
    private final AdmissionDiagnosisSubmitGate admissionDiagnosisSubmitGate;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final DoctorStationInpatientAdmissionMapper doctorStationInpatientAdmissionMapper;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 发起住院申请（INSERT 新 IMP+PLANNED 住院线，不修改源门诊就诊）。
     *
     * @param request 住院申请表单
     * @return 新建住院就诊标识与业务号
     * @throws BusinessException 参数缺失、源就诊不可转住院、重复申请、科室/病区无效或患者不一致
     */
    public RequestInpatientAdmissionResponse requestInpatientAdmission(RequestInpatientAdmissionRequest request) {
        // Gate：住院申请请求体与入院诊断结构校验
        this.validateRequestInpatientAdmissionRequest(request);
        Long ambEncounterId = request.getAmbEncounterId();
        // Pre：加载源就诊并断言其为可转住院的门诊线（BR-IP-REQ-001）
        EncounterDetailQueryModel ambEncounter = encounterQueryService.mustGetEncounter(ambEncounterId);
        encounterQueryService.assertAmbForInpatientReq(ambEncounterId);
        Long resolvedPatientId = ambEncounter.getPatientId();
        // Branch：若前端传入患者主键，须与源就诊头表一致（BR 患者一致性）
        if (request.getPatientId() != null) {
            if (!request.getPatientId().equals(resolvedPatientId)) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "患者与源就诊不一致，无法办理住院");
            }
        }
        // Gate：同一门诊锚点不得存在未取消住院线（BR-IP-REQ-002）
        OpenInpatientLineQuery existingLine = encounterQueryService.findOpenInpatientLine(ambEncounterId);
        if (existingLine.isExists()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "该门诊就诊已存在住院申请，请勿重复提交");
        }
        // Gate：意向住院科室须存在
        this.assertTargetDepartmentExists(request.getTargetDepartmentId());
        // Branch：若填写意向病区，须为启用中的 WARD 形态位置
        if (request.getWardId() != null) {
            this.assertWardValid(request.getWardId());
        }
        RequestInpatientAdmissionCommand command =
            doctorStationInpatientAdmissionMapper.toRequestInpatientAdmissionCommand(request, resolvedPatientId);
        // Cmd：INSERT 新 IMP+PLANNED 住院线
        Long inpatientEncounterId = encounterDomainService.requestInpatientAdmission(command);
        RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand admissionItem =
            command.resolveItems().get(0);
        encounterInpatientAdmissionOrchestrator.saveAdmissionDiagnoses(
            resolvedPatientId,
            inpatientEncounterId,
            admissionItem.getAdmissionDiagnoses(),
            command.getOperatorId());
        // Pre：回读新建住院线的业务号（BR-IP-REQ-009）
        OpenInpatientLineQuery createdLine = encounterQueryService.findOpenInpatientLine(ambEncounterId);
        if (!createdLine.isExists() || createdLine.getBusNo() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "住院申请已落库但无法回读住院业务号");
        }
        String inpatientBusNo = createdLine.getBusNo();
        // Cmd：可选意向预交金总额 → 仅落 HOSPITAL_DEPOSIT DRAFT（BR-IP-REQ-010/011）
        this.createIntentAdvanceDepositDraftIfRequired(
            request.getAdvanceDepositAmount(),
            inpatientEncounterId,
            resolvedPatientId);
        log.info(
            "住院申请已提交 ambEncounterId={} inpatientEncounterId={} requestDoctorId={} operatorId={} sessionPractitionerId={}",
            ambEncounterId,
            inpatientEncounterId,
            request.getRequestDoctorId(),
            request.getOperatorId(),
            currentPractitionerResolver.findPractitionerIdOrNull()
        );
        return doctorStationInpatientAdmissionMapper.toRequestInpatientAdmissionResponse(
            inpatientEncounterId,
            inpatientBusNo,
            true
        );
    }

    /**
     * 医生填写预交金总额时，对新建住院线落意向 DRAFT（无渠道明细、不开户、不 settle）。
     *
     * @param advanceDepositAmount 意向总额；null 或 ≤0 跳过
     * @param inpatientEncounterId 新建住院就诊主键
     * @param patientId            患者主键
     */
    private void createIntentAdvanceDepositDraftIfRequired(
        BigDecimal advanceDepositAmount,
        Long inpatientEncounterId,
        Long patientId) {
        // 未填或非正数：医生不要求预交金，不落意向草稿
        if (advanceDepositAmount == null || advanceDepositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        CollectAdvanceDepositCommand collectCommand = new CollectAdvanceDepositCommand();
        collectCommand.setEncounterId(inpatientEncounterId);
        collectCommand.setPatientId(patientId);
        collectCommand.setEntererId(currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "无法解析当前医师，不能创建预交金意向"));
        collectCommand.setTenderedAmount(advanceDepositAmount);
        collectCommand.setDisplayAmount(advanceDepositAmount);
        collectCommand.setKindEnum(PaymentKind.HOSPITAL_DEPOSIT);
        collectCommand.setPaymentEnum(PaymentType.PAY.getCode());
        // 医生不关注支付方式：明细留空，登记确认时由收费员补齐多渠道明细
        collectCommand.setPaymentDetails(Collections.emptyList());
        paymentReconciliationDomainService.collectAdvanceDeposit(collectCommand);
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
        // Gate：门诊就诊标识非空
        this.validateCheckInpatientAdmissionExistsRequest(request);
        // Pre：只读查询未取消住院线
        OpenInpatientLineQuery line = encounterQueryService.findOpenInpatientLine(request.getAmbEncounterId());
        // Asm：装配 exists / inpatientEncounterId / statusEnum
        return doctorStationInpatientAdmissionMapper.toCheckInpatientAdmissionExistsResponse(line);
    }

    /**
     * 撤回待登记住院申请（仅 {@code PLANNED}）：作废意向预交金 DRAFT，并直接软删住院线。
     *
     * <p>不迁 {@code CANCELLED}；软删后款台登记队列不可见，医生可在同一「办理住院」入口编辑后重新提交。
     * 已登记（{@code REGISTERED+}）须走住院处取消。</p>
     *
     * @param request 门诊锚点与操作人
     * @return 被撤回住院线标识
     * @throws BusinessException 参数缺失、无开放住院线、非待登记态
     */
    public WithdrawInpatientAdmissionResponse withdrawInpatientAdmission(
        WithdrawInpatientAdmissionRequest request
    ) {
        // Gate：撤回须带门诊锚点与操作人
        this.validateWithdrawInpatientAdmissionRequest(request);
        Long ambEncounterId = request.getAmbEncounterId();
        // Pre：定位该门诊下未取消住院线；无开放线则无可撤回对象
        OpenInpatientLineQuery openLine = encounterQueryService.findOpenInpatientLine(ambEncounterId);
        if (!openLine.isExists() || openLine.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "当前就诊无待撤回的住院申请");
        }
        // Gate：仅待登记（PLANNED）允许医生撤回；已登记须由住院处取消
        if (!EncounterStatus.PLANNED.getCode().equals(openLine.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待登记的住院申请可撤回，已登记请至住院收费窗口取消");
        }
        Long inpatientEncounterId = openLine.getEncounterId();
        String statusBeforeWithdraw = openLine.getStatusEnum();
        // Cmd：作废意向预交金 DRAFT（成功收款单不触碰）
        this.abortHospitalDepositDrafts(inpatientEncounterId);
        // Cmd：直接软删待登记住院线（不走取消登记）
        encounterDomainService.softDeleteWithdrawnInpatientAdmission(
            inpatientEncounterId,
            request.getOperatorId());
        log.info(
            "住院申请已撤回（软删） ambEncounterId={} inpatientEncounterId={} operatorId={}",
            ambEncounterId,
            inpatientEncounterId,
            request.getOperatorId()
        );
        // Asm：返回原状态码，前端解锁办理住院表单供再次编辑提交
        return doctorStationInpatientAdmissionMapper.toWithdrawInpatientAdmissionResponse(
            inpatientEncounterId,
            statusBeforeWithdraw,
            true
        );
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
        // Gate：办理意向若填写须为 ChargeIntent code（医保办/自费办）
        this.validateChargeIntent(request.getChargeIntent());
        this.validateAdmissionDiagnoses(request.getAdmissionDiagnoses());
    }

    /**
     * 校验入院办理意向：可空；非空须为 {@link ChargeIntent} 持久化 code。
     *
     * @param chargeIntent 意向 code；空白视为未指定
     * @throws BusinessException 非空但非法时拒绝提交
     */
    private void validateChargeIntent(String chargeIntent) {
        // 未填：医生不指定办理方式，款台自行选择费用性质
        if (StringUtils.isEmpty(chargeIntent)) {
            return;
        }
        String trimmed = chargeIntent.trim();
        ChargeIntent intent = EnumUtil.fromCode(ChargeIntent.class, trimmed);
        // 非法 code：拒绝落库，避免款台读到无法识别的意向
        if (intent == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "费用性质须为医保或自费");
        }
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
     * 撤回申请请求参数校验。
     *
     * @param request 请求体
     * @throws BusinessException 门诊锚点或操作人为空
     */
    private void validateWithdrawInpatientAdmissionRequest(WithdrawInpatientAdmissionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        if (request.getAmbEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "门诊就诊标识不能为空");
        }
        if (request.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "操作人不能为空");
        }
    }

    /**
     * 软删就诊下住院预交金意向草稿（及历史 CANCELLED 作废稿），流水不可见。
     *
     * <p>与住院登记取消路径同口径；成功收款单不触碰。</p>
     *
     * @param encounterId 住院就诊主键
     */
    private void abortHospitalDepositDrafts(Long encounterId) {
        // 无就诊主键时无法定位草稿，直接跳过
        if (encounterId == null) {
            return;
        }
        // Cmd：当前 DRAFT 意向稿软删
        List<PaymentReconciliationQueryModel> drafts =
            paymentReconciliationQueryService.listHospitalDepositDraftsByEncounter(encounterId);
        for (PaymentReconciliationQueryModel draft : drafts) {
            if (draft == null || draft.getId() == null) {
                continue;
            }
            paymentReconciliationDomainService.softDeleteAdvanceDepositDraft(draft.getId());
        }
        // Cmd：历史 abort 留下的 CANCELLED 预交金也软删，避免预交金页仍可见
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.HOSPITAL_DEPOSIT) {
                continue;
            }
            if (!PaymentStatus.CANCELLED.equals(payment.getStatusEnum())) {
                continue;
            }
            paymentReconciliationDomainService.softDeleteAdvanceDepositDraft(payment.getId());
        }
    }

    /**
     * 入院诊断列表结构校验：非空、主诊断标记、新开/引用二选一。
     *
     * @param diagnoses 入院诊断行
     * @throws BusinessException 列表为空或行内字段不合法
     */
    private void validateAdmissionDiagnoses(List<AdmissionDiagnosisSubmitDto> diagnoses) {
        List<AdmissionDiagnosisSubmitGate.AdmissionDiagnosisRowView> rows = new ArrayList<>();
        if (diagnoses != null) {
            rows.addAll(diagnoses);
        }
        admissionDiagnosisSubmitGate.validateAdmissionDiagnosisRows(
            rows,
            "入院诊断不能为空",
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT);
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
