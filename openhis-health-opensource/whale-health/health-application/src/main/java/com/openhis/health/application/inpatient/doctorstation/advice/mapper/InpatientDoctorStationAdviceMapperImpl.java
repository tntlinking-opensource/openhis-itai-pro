/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.mapper;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.advice.ServiceAdviceCommandAssembler;
import com.openhis.health.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.openhis.health.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrator;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.CancelStopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.PrepickedProductKeyDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientServiceAdviceDraftLinePayload;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ChangeInpatientDoctorStationAdviceBusinessTimeRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CreateDischargeInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CreateDischargeInpatientDoctorStationAdviceResponse;
import com.openhis.health.domain.custom.encounterhistory.command.RecordDischargeMilestoneCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CancelStopLongTermMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationRequestBusinessTimeCommand;
import com.openhis.health.domain.medication.medicationrequest.command.StopLongTermMedicationOrderCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.command.CancelStopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ChangeServiceRequestBusinessTimeCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateDischargeRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.shared.validator.InpatientPendingSettlementClinicalGate;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link InpatientDoctorStationAdviceMapper} 默认实现：将医生站 HTTP 请求形状装配为领域命令/读查询对象。
 *
 * <p><b>职责</b>：字段搬运、默认值（如数量缺省为 1、单位缺省）、资源类型分支前置校验；不实现计价、库存、
 * 医保规则等不变量（由各自 {@code *DomainService} 承担）。</p>
 *
 * <p><b>与 {@link DoctorStationAdviceConstants} 的关系</b>：生成来源、资源类型、行操作等契约字面量集中引用
 * {@code domain.share.constants}，与 AS-IP-DOR-003 / IF-IP-DOR 契约对齐。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceMapperImpl implements InpatientDoctorStationAdviceMapper {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;
    private final InpatientPendingSettlementClinicalGate inpatientPendingSettlementClinicalGate;

    /**
     * Gate：校验请求患者与住院读模型快照患者一致（复用 {@link PatientEncounterConsistencyGate}）。
     *
     * @param snapshot         住院信息读模型快照
     * @param requestPatientId 请求携带的患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 快照缺失或患者不一致时抛出
     */
    @Override
    public void validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel snapshot, Long requestPatientId) {
        // 未加载到住院读模型：与诊断模块同口径，统一提示先选患者。
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, DoctorStationAdviceConstants.MSG_SELECT_PATIENT_FIRST);
        }
        if (requestPatientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SELECT_PATIENT_FIRST);
        }
        this.patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            requestPatientId,
            snapshot.getPatientId(),
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_PATIENT_ENCOUNTER_MISMATCH);
    }

    /**
     * Gate：住院就诊须为 IMP（与诊断模块一致：能加载 {@link InpatientInfoReadModel} 即视为 IMP）。
     *
     * @param snapshot 住院信息读模型快照
     * @throws com.openhis.whale.core.application.exception.BusinessException 快照或就诊锚点缺失时抛出
     */
    @Override
    public void validateInpatientEncounterClassImp(InpatientEncounterContextQueryModel snapshot) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, DoctorStationAdviceConstants.MSG_SELECT_PATIENT_FIRST);
        }
        if (snapshot.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, DoctorStationAdviceConstants.MSG_SELECT_PATIENT_FIRST);
        }
    }

    /**
     * Gate：住院就诊须处于医生站可写状态（IN_PROGRESS 或 ON_HOLD）。
     *
     * @param snapshot 住院信息读模型快照
     * @throws com.openhis.whale.core.application.exception.BusinessException 不可写时抛出
     */
    @Override
    public void validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel snapshot) {
        if (snapshot == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, DoctorStationAdviceConstants.MSG_SELECT_PATIENT_FIRST);
        }
        String statusCode = snapshot.getStatusEnum();
        // 待结算/已结算/已离院：禁止开立/签发/签退等医嘱变更（EX-IP-ORD-107）。
        inpatientPendingSettlementClinicalGate.assertEncounterAllowsOrderClinicalMutation(
            statusCode,
            DoctorStationAdviceConstants.EX_IP_ORD_107,
            InpatientNurseStationConstants.MSG_ENCOUNTER_PENDING_SETTLEMENT_ORDER_FORBIDDEN);
        // 在院进行中：允许开立/暂存/签发。
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            return;
        }
        // 留观/挂起：住院医生站仍允许补录医嘱（与 AS BR-IP-ORD 可写门控一致）。
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, DoctorStationAdviceConstants.MSG_ENCOUNTER_NOT_EDITABLE_FOR_ADVICE);
    }

    /**
     * Gate：列表请求必填。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 缺失时抛出
     */
    @Override
    public void validateListRequest(Long encounterId, Long patientId) {
        // 列表读路径仍要求显式就诊+患者，便于与卡片门控对拍。
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED);
        }
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED);
        }
    }

    /**
     * 将医生站医嘱目录分页请求装配为产品定义读模型分页查询命令（IF-IP-DOR-11 Pre）。
     *
     * <p>实现要点：默认限定三类产品表（药/诊疗活动/耗材定义）；若请求显式传入表名集合则完全尊重前端筛选。
     * 预勾选键用于分页首屏保证某些定义行一定出现（如从上一页带入）。</p>
     *
     * @param request 前端分页与筛选条件
     * @return 下游读模型查询使用的 {@link ProductDefinitionPageQuery}
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageInpatientDoctorStationAdviceCatalogRequest request) {
        ProductDefinitionPageQuery q = new ProductDefinitionPageQuery();
        PageQueryBinder.bind(request, q);
        q.setPricingFlag(request.getPricingFlag());
        // 目录页只展示启用定义，与医生站「可开立」语义一致。
        q.setActiveFlag(Boolean.TRUE);
        if (request.getProductTableNames() != null && !request.getProductTableNames().isEmpty()) {
            q.setProductTableNames(new ArrayList<>(request.getProductTableNames()));
        } else {
            q.setProductTableNames(Arrays.asList(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                TableNameConstants.WKF_ACTIVITY_DEFINITION
            ));
        }
        if (request.getPrepickedProductKeys() != null && !request.getPrepickedProductKeys().isEmpty()) {
            // 组套 IF-08 / 历史行 ids 收窄目录（IF-IP-DOR-11 prepickedProductKeys）
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (PrepickedProductKeyDto k : request.getPrepickedProductKeys()) {
                ProductDefinitionBatchLookup l = new ProductDefinitionBatchLookup();
                l.setProductTableName(k.getProductTableName());
                l.setProductDefinitionId(k.getProductDefinitionId());
                lookups.add(l);
            }
            q.setPrepickedProductKeys(lookups);
        }
        // Pre：西药开立目录排除中草药等 medication_category_code（与门诊 IF-DOR-11 一致）
        if (request.getExcludeMedicationCategoryCodes() != null && !request.getExcludeMedicationCategoryCodes().isEmpty()) {
            q.setExcludeMedicationCategoryCodes(new ArrayList<>(request.getExcludeMedicationCategoryCodes()));
        }
        if (StringUtils.isNotEmpty(request.getCategoryEnum())) {
            String categoryRaw = request.getCategoryEnum().trim();
            // IF-IP-DOR-11 护理级别（category 26）为 ActivityDef 类目，非申请单 21/22/23。
            if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING.equals(categoryRaw)) {
                q.setCategoryCode(categoryRaw);
            } else {
                q.setCategoryCode(ServiceApplyFormOrchestrator.resolveApplyFormCategoryCode(request.getCategoryEnum()));
            }
        }
        return q;
    }

    /**
     * 装配「新建药品医嘱草稿」领域命令（暂存批次 CREATE / 药品分支）。
     *
     * @param parent     批次父上下文（就诊、患者）
     * @param line       单行请求，须含 {@code medicationPayload}
     * @param operatorId 操作人，写入记录者与操作者
     * @param orgId      开立机构，落库药品申请需要
     * @return {@link CreateMedicationOrderDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException 药品载荷为空时抛出
     */
    @Override
    public CreateMedicationOrderDraftCommand toCreateMedicationOrderDraftCommand(
        SaveInpatientDoctorStationAdviceDraftBatchRequest parent,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    ) {
        if (line.getMedicationPayload() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_MEDICATION_DRAFT_PAYLOAD_REQUIRED);
        }
        CreateMedicationOrderDraftCommand cmd = new CreateMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(parent.getEncounterId());
        cmd.setPatientId(parent.getPatientId());
        cmd.setMedicationId(line.getMedicationPayload().getMedicationDefinitionId());
        cmd.setTherapyEnum(this.resolveTherapyTypeCode(line));
        BigDecimal medQty = line.getMedicationPayload().getQuantity();
        // 数量缺省为 1：与收费草稿按「至少一笔」计价及域服务默认值对齐。
        if (medQty == null) {
            medQty = BigDecimal.ONE;
        }
        cmd.setQuantity(medQty);
        // 单位缺省 "U"：占位直至主数据补齐单位码；域层可再校验。
        cmd.setUnitCode(StringUtils.firstNonBlank(line.getMedicationPayload().getUnitCode(),
            DoctorStationAdviceConstants.DEFAULT_MEDICATION_REQUEST_UNIT_CODE_PLACEHOLDER));
        cmd.setMethodCode(line.getMedicationPayload().getMethodCode());
        cmd.setRateCode(line.getMedicationPayload().getRateCode());
        cmd.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        cmd.setOrgId(orgId);
        cmd.setRecorderId(operatorId);
        cmd.setSortNumber(line.getSortNumber());
        cmd.setGroupId(line.getMedicationPayload().getGroupId());
        cmd.setDose(line.getMedicationPayload().getDose());
        cmd.setDoseUnitCode(line.getMedicationPayload().getDoseUnitCode());
        // 首日用量仅长期可写；临时/出院带药强制清空（BR-IP-ORD-030 / BR-MRQ-010）
        this.applyFirstDoseToCreateCommand(cmd, line);
        cmd.setDispensePerDuration(line.getMedicationPayload().getDispensePerDuration());
        cmd.setSkinTestFlag(line.getMedicationPayload().getSkinTestFlag());
        cmd.setEncounterDiagnosisIds(line.getMedicationPayload().getEncounterDiagnosisIds());
        cmd.setContractId(line.getMedicationPayload().getContractId());
        // 医嘱备注：原样透传（可空；空串由域层按「显式传入」覆盖）
        cmd.setRemark(line.getMedicationPayload().getRemark());
        return cmd;
    }

    /**
     * 按 therapy 写入或清空首日用量（创建草稿）。
     *
     * @param cmd  创建命令
     * @param line 草稿行
     */
    private void applyFirstDoseToCreateCommand(
        CreateMedicationOrderDraftCommand cmd,
        InpatientDoctorStationAdviceDraftLineRequest line) {
        if (TherapyType.LONG_TERM.getCode().equals(this.resolveTherapyTypeCode(line))) {
            cmd.setFirstDose(line.getMedicationPayload().getFirstDose());
            cmd.setFirstDoseUnitCode(line.getMedicationPayload().getFirstDoseUnitCode());
            return;
        }
        // 非长期：显式清空，防止前端误传
        cmd.setFirstDose(null);
        cmd.setFirstDoseUnitCode(null);
    }

    /**
     * 装配「修改药品医嘱草稿」领域命令（暂存批次 UPDATE / 药品分支）。
     *
     * @param line       单行请求，须含 {@code requestId} 与药品载荷
     * @param operatorId 操作人
     * @return {@link ChangeMedicationOrderDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException {@code requestId} 或载荷缺失时抛出
     */
    @Override
    public ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftCommand(InpatientDoctorStationAdviceDraftLineRequest line, Long operatorId) {
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_MEDICATION_UPDATE_REQUEST_ID_REQUIRED);
        }
        if (line.getMedicationPayload() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_MEDICATION_DRAFT_PAYLOAD_REQUIRED);
        }
        ChangeMedicationOrderDraftCommand cmd = new ChangeMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(line.getRequestId());
        cmd.setQuantity(line.getMedicationPayload().getQuantity());
        cmd.setUnitCode(line.getMedicationPayload().getUnitCode());
        cmd.setMethodCode(line.getMedicationPayload().getMethodCode());
        cmd.setRateCode(line.getMedicationPayload().getRateCode());
        cmd.setTherapyEnum(this.resolveTherapyTypeCode(line));
        cmd.setSortNumber(line.getSortNumber());
        cmd.setGroupId(line.getMedicationPayload().getGroupId());
        cmd.setDose(line.getMedicationPayload().getDose());
        cmd.setDoseUnitCode(line.getMedicationPayload().getDoseUnitCode());
        // 首日用量仅长期可写；非长期清空（BR-IP-ORD-030）
        this.applyFirstDoseToChangeCommand(cmd, line);
        cmd.setDispensePerDuration(line.getMedicationPayload().getDispensePerDuration());
        cmd.setSkinTestFlag(line.getMedicationPayload().getSkinTestFlag());
        cmd.setEncounterDiagnosisIds(line.getMedicationPayload().getEncounterDiagnosisIds());
        cmd.setContractId(line.getMedicationPayload().getContractId());
        // 医嘱备注：原样透传（可空；空串由域层按「显式传入」覆盖）
        cmd.setRemark(line.getMedicationPayload().getRemark());
        return cmd;
    }

    /**
     * 按 therapy 写入或清空首日用量（修改草稿）。
     *
     * @param cmd  修改命令
     * @param line 草稿行
     */
    private void applyFirstDoseToChangeCommand(
        ChangeMedicationOrderDraftCommand cmd,
        InpatientDoctorStationAdviceDraftLineRequest line) {
        if (TherapyType.LONG_TERM.getCode().equals(this.resolveTherapyTypeCode(line))) {
            cmd.setFirstDose(line.getMedicationPayload().getFirstDose());
            cmd.setFirstDoseUnitCode(line.getMedicationPayload().getFirstDoseUnitCode());
            return;
        }
        cmd.setFirstDose(null);
        cmd.setFirstDoseUnitCode(null);
    }

    /**
     * 装配「删除药品医嘱草稿」领域命令。
     *
     * @param requestId  药品申请主键
     * @param operatorId 操作人
     * @return {@link RemoveMedicationOrderDraftCommand}
     */
    @Override
    public RemoveMedicationOrderDraftCommand toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId) {
        RemoveMedicationOrderDraftCommand cmd = new RemoveMedicationOrderDraftCommand();
        cmd.setId(requestId);
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    /**
     * 装配「新建诊疗医嘱草稿」领域命令（暂存批次 CREATE / 诊疗分支）。
     *
     * <p>嘱托行：强制 {@code category_enum=52}，并透传 {@code entrustContent}
     *（AS-IP-DOR-003 · BR-IP-ORD-028；正文必填由 {@code InpatientEntrustOrderGateSupport} 前置）。</p>
     *
     * @param parent     批次父上下文
     * @param line       单行请求，须含 {@code servicePayload}
     * @param productRm  活动定义对应的产品读模型行（单价/单位/分类等展示与计价元数据）
     * @param operatorId 操作人，兼作申请人
     * @return {@link CreateServiceRequestDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException 载荷或读模型为空时抛出
     */
    @Override
    public CreateServiceRequestDraftCommand toCreateServiceRequestDraftCommand(
        SaveInpatientDoctorStationAdviceDraftBatchRequest parent,
        InpatientDoctorStationAdviceDraftLineRequest line,
        ProductDefinitionDisplayDto productRm,
        Long operatorId
    ) {
        // 诊疗 CREATE 须有 servicePayload
        if (line.getServicePayload() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_DRAFT_PAYLOAD_REQUIRED);
        }
        // 活动定义读模型须已由 AppService 预加载
        if (productRm == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_ACTIVITY_READ_MODEL_REQUIRED);
        }
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item = new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(parent.getEncounterId());
        item.setPatientId(parent.getPatientId());
        SaveInpatientServiceAdviceDraftLinePayload servicePayload = line.getServicePayload();
        item.setActivityId(servicePayload.getActivityDefinitionId());
        item.setQuantity(ServiceAdviceDraftCommandHelper.requireQuantity(
            servicePayload.getQuantity(), servicePayload.getChildOverridesJson()));
        item.setUnitCode(ServiceAdviceDraftCommandHelper.requireUnitCode(
            servicePayload.getUnitCode(), servicePayload.getChildOverridesJson(), productRm));
        String therapyEnum = this.resolveTherapyTypeCode(line);
        item.setTherapyEnum(therapyEnum);
        item.setExecutionCount(ServiceAdviceDraftCommandHelper.requireExecutionCount(
            servicePayload.getExecutionCount(), servicePayload.getChildOverridesJson(), therapyEnum));
        // 嘱托：类目强制 52，与目录名「嘱托」双锚一致（BR-IP-ORD-028①/⑥）
        String categoryEnum = StringUtils.firstNonBlank(productRm.getCategoryCode(), "1");
        if (ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST.equals(StringUtils.trimToNull(categoryEnum))
            || GeneralSharedConstants.ENTRUST_ACTIVITY_NAME.equals(StringUtils.trimToNull(productRm.getProductName()))) {
            categoryEnum = ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST;
        }
        item.setCategoryEnum(categoryEnum);
        item.setRateCode(this.resolveServiceRateCodeForTherapy(therapyEnum, servicePayload.getRateCode()));
        item.setOrgId(servicePayload.getPerformOrgId());
        item.setContractId(servicePayload.getContractId());
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // note：套餐子项覆盖优先；无覆盖时写入用户医嘱备注（与嘱托正文 entrustContent 分立）
        item.setNote(this.resolveServiceDraftNote(servicePayload));
        // 嘱托正文透传 entrust_content；常规诊疗可选备注嘱托
        item.setEntrustContent(StringUtils.trimToNull(servicePayload.getEntrustContent()));
        item.setSortNumber(line.getSortNumber());
        item.setEncounterDiagnosisIds(servicePayload.getEncounterDiagnosisIds());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「修改诊疗医嘱草稿」领域命令（暂存批次 UPDATE / 诊疗分支）。
     *
     * <p>透传 {@code entrustContent}；域层仅在非 null 时覆盖 {@code entrust_content}
     *（嘱托空正文由 Gate 拒绝，见 BR-IP-ORD-028）。</p>
     *
     * @param line       单行请求，须含 {@code requestId}；备注来自套餐覆盖或 {@code servicePayload.remark}
     * @param operatorId 操作人
     * @return {@link ModifyServiceRequestDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException {@code requestId} 为空时抛出
     */
    @Override
    public ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(InpatientDoctorStationAdviceDraftLineRequest line, Long operatorId) {
        // UPDATE 须锚定已有 ServiceRequest 主键
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_UPDATE_REQUEST_ID_REQUIRED);
        }
        ModifyServiceRequestDraftCommand cmd = new ModifyServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item = new ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand();
        item.setId(line.getRequestId());
        String therapyEnum = this.resolveTherapyTypeCode(line);
        item.setTherapyEnum(therapyEnum);
        // 仅当携带诊疗载荷时覆盖可改字段；空载荷表示仅改 therapy/排序等行级字段
        if (line.getServicePayload() != null) {
            SaveInpatientServiceAdviceDraftLinePayload servicePayload = line.getServicePayload();
            // 套餐覆盖优先；否则用户备注（null 表示本字段不变更）
            item.setRemark(this.resolveServiceDraftNote(servicePayload));
            // 嘱托正文透传；域层仅在非 null 时覆盖 entrust_content
            item.setEntrustContent(StringUtils.trimToNull(servicePayload.getEntrustContent()));
            item.setEncounterDiagnosisIds(servicePayload.getEncounterDiagnosisIds());
            item.setRateCode(this.resolveServiceRateCodeForTherapy(therapyEnum, servicePayload.getRateCode()));
            item.setOrgId(servicePayload.getPerformOrgId());
            BigDecimal quantity = ServiceAdviceDraftCommandHelper.resolveQuantityForModify(
                servicePayload.getQuantity(), servicePayload.getChildOverridesJson());
            // 数量显式且为正才覆盖，避免 0/负值误写
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                item.setQuantity(quantity);
            }
            String unitCode = ServiceAdviceDraftCommandHelper.resolveUnitCodeForModify(
                servicePayload.getUnitCode(), servicePayload.getChildOverridesJson());
            if (unitCode != null) {
                item.setUnitCode(unitCode);
            }
            Integer executionCount = ServiceAdviceDraftCommandHelper.resolveExecutionCountForModify(
                servicePayload.getExecutionCount(), servicePayload.getChildOverridesJson());
            if (executionCount != null) {
                item.setExecutionCount(executionCount);
            }
        }
        item.setSortNumber(line.getSortNumber());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 诊疗草稿 note/remark：套餐子项覆盖 JSON 优先；否则用户医嘱备注。
     *
     * @param servicePayload 诊疗草稿载荷
     * @return 落库 note/remark；均空时 null（修改路径表示不覆盖）
     */
    private String resolveServiceDraftNote(SaveInpatientServiceAdviceDraftLinePayload servicePayload) {
        if (servicePayload == null) {
            return null;
        }
        String overrides = StringUtils.trimToNull(servicePayload.getChildOverridesJson());
        if (overrides != null) {
            return overrides;
        }
        // 显式传空串时保留，供修改路径清空备注
        if (servicePayload.getRemark() != null) {
            return servicePayload.getRemark();
        }
        return null;
    }

    /**
     * 长期诊疗须带频次 code；临时传空由域层清空 rate_code。
     */
    private String resolveServiceRateCodeForTherapy(String therapyEnum, String rateCode) {
        if (TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            if (StringUtils.isEmpty(rateCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_LONG_TERM_SERVICE_RATE_CODE_REQUIRED);
            }
            return rateCode.trim();
        }
        return "";
    }

    /**
     * 装配「删除诊疗医嘱草稿」领域命令。
     *
     * @param requestId  诊疗申请主键
     * @param operatorId 操作人
     * @return {@link RemoveServiceRequestDraftCommand}
     */
    @Override
    public RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(Long requestId, Long operatorId) {
        return ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(requestId, operatorId);
    }

    /**
     * 装配药品医嘱签发命令（批次号 + 处方号）。
     *
     * @param id             药品申请主键
     * @param prescriptionNo 处方号（按日序列）
     * @param operatorId     操作人
     * @return {@link SignMedicationOrderCommand}
     */
    @Override
    public SignMedicationOrderCommand toSignMedicationOrderCommand(Long id, String prescriptionNo, Long operatorId) {
        SignMedicationOrderCommand c = new SignMedicationOrderCommand();
        c.setId(id);
        c.setOperatorId(operatorId);
        c.setPrescriptionNo(prescriptionNo);
        return c;
    }

    /**
     * 装配药品医嘱签退命令。
     *
     * @param id         药品申请主键
     * @param operatorId 操作人
     * @return {@link SignOffMedicationOrderCommand}
     */
    @Override
    public SignOffMedicationOrderCommand toSignOffMedicationOrderCommand(Long id, Long operatorId) {
        SignOffMedicationOrderCommand c = new SignOffMedicationOrderCommand();
        c.setId(id);
        c.setOperatorId(operatorId);
        return c;
    }

    /**
     * 装配诊疗申请签发命令（批次号写入服务申请）。
     *
     * @param id         诊疗申请主键
     * @param operatorId 操作人，兼作签名人
     * @return {@link SignServiceRequestCommand}
     */
    @Override
    public SignServiceRequestCommand toSignServiceRequestCommand(Long id, Long operatorId) {
        SignServiceRequestCommand cmd = new SignServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        SignServiceRequestCommand.SignServiceRequestItemCommand item = new SignServiceRequestCommand.SignServiceRequestItemCommand();
        item.setId(id);
        item.setSignerId(operatorId);
        item.setSignTime(LocalDateTime.now());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配已签发诊疗申请的签退命令。
     *
     * @param id         诊疗申请主键
     * @param operatorId 操作人
     * @return {@link WithdrawSignedServiceRequestCommand}
     */
    @Override
    public WithdrawSignedServiceRequestCommand toWithdrawServiceRequestCommand(Long id, Long operatorId) {
        return ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(id, operatorId);
    }

    /**
     * 装配药品医嘱成组/清组命令（IF-IP-DOR-17）。
     *
     * @param ids        药品申请主键列表；{@code groupId==null} 时表示清空组号
     * @param groupId    目标组号，可为 null
     * @param operatorId 操作人
     * @return {@link AdjustMedicationOrderGroupCommand}
     */
    @Override
    public AdjustMedicationOrderGroupCommand toAdjustMedicationGroupCommand(List<Long> ids, Long groupId, Long operatorId) {
        AdjustMedicationOrderGroupCommand c = new AdjustMedicationOrderGroupCommand();
        c.setOperatorId(operatorId);
        c.setIds(ids);
        c.setGroupId(groupId);
        return c;
    }

    /**
     * 装配长期药品停嘱命令（IF-IP-DOR-20）。
     *
     * @param line       停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public StopLongTermMedicationOrderCommand toStopLongTermMedicationOrderCommand(
        StopLongTermAdviceLineRequest line,
        Long operatorId
    ) {
        StopLongTermMedicationOrderCommand cmd = new StopLongTermMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(line.getRequestId());
        if (line.getStopTime() != null) {
            cmd.setStopTime(line.getStopTime());
        } else {
            cmd.setStopTime(LocalDateTime.now());
        }
        return cmd;
    }

    /**
     * 装配诊疗停嘱命令（IF-IP-DOR-20）。
     *
     * @param line       停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public StopServiceRequestCommand toStopServiceRequestCommand(StopLongTermAdviceLineRequest line, Long operatorId) {
        StopServiceRequestCommand cmd = new StopServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        StopServiceRequestCommand.StopServiceRequestItemCommand item = new StopServiceRequestCommand.StopServiceRequestItemCommand();
        item.setId(line.getRequestId());
        if (line.getStopTime() != null) {
            item.setStopTime(line.getStopTime());
        } else {
            item.setStopTime(LocalDateTime.now());
        }
        cmd.setItems(List.of(item));
        return cmd;
    }

    /**
     * 装配长期药品取消停嘱命令（IF-IP-DOR-25）。
     *
     * @param line       取消停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CancelStopLongTermMedicationOrderCommand toCancelStopLongTermMedicationOrderCommand(
        CancelStopLongTermAdviceLineRequest line,
        Long operatorId
    ) {
        CancelStopLongTermMedicationOrderCommand cmd = new CancelStopLongTermMedicationOrderCommand();
        cmd.setOperatorId(operatorId);
        // 取消停嘱仅恢复状态，不携带停嘱时间等附加字段。
        cmd.setId(line.getRequestId());
        return cmd;
    }

    /**
     * 装配诊疗取消停嘱命令（IF-IP-DOR-25）。
     *
     * @param line       取消停嘱行
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CancelStopServiceRequestCommand toCancelStopServiceRequestCommand(
        CancelStopLongTermAdviceLineRequest line,
        Long operatorId
    ) {
        CancelStopServiceRequestCommand cmd = new CancelStopServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        // 取消停嘱仅恢复状态，不携带停嘱时间等附加字段。
        cmd.setId(line.getRequestId());
        return cmd;
    }

    /**
     * 装配药品改业务时间命令（IF-IP-DOR-26）。
     *
     * @param request    改时请求
     * @param operatorId 操作人（审计；不落「改时人」列）
     * @return 领域命令
     */
    @Override
    public ChangeMedicationRequestBusinessTimeCommand toChangeMedicationRequestBusinessTimeCommand(
        ChangeInpatientDoctorStationAdviceBusinessTimeRequest request,
        Long operatorId
    ) {
        ChangeMedicationRequestBusinessTimeCommand cmd = new ChangeMedicationRequestBusinessTimeCommand();
        // 审计操作人；领域仅写开立/停嘱列，不改开立人/停嘱人。
        cmd.setOperatorId(operatorId);
        cmd.setId(request.getRequestId());
        cmd.setTimeKind(request.getTimeKind());
        cmd.setNewTime(request.getNewTime());
        return cmd;
    }

    /**
     * 装配诊疗改业务时间命令（IF-IP-DOR-26）。
     *
     * @param request    改时请求
     * @param operatorId 操作人（审计；不落「改时人」列）
     * @return 领域命令
     */
    @Override
    public ChangeServiceRequestBusinessTimeCommand toChangeServiceRequestBusinessTimeCommand(
        ChangeInpatientDoctorStationAdviceBusinessTimeRequest request,
        Long operatorId
    ) {
        ChangeServiceRequestBusinessTimeCommand cmd = new ChangeServiceRequestBusinessTimeCommand();
        // 审计操作人；领域仅写开立/停嘱列，不改开立人/停嘱人。
        cmd.setOperatorId(operatorId);
        cmd.setId(request.getRequestId());
        cmd.setTimeKind(request.getTimeKind());
        cmd.setNewTime(request.getNewTime());
        return cmd;
    }

    /**
     * 装配出院服务申请命令（IF-IP-DOR-21）。
     *
     * @param request    出院请求
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CreateDischargeRequestCommand toCreateDischargeRequestCommand(
        CreateDischargeInpatientDoctorStationAdviceRequest request,
        Long operatorId
    ) {
        CreateDischargeRequestCommand cmd = new CreateDischargeRequestCommand();
        cmd.setOperatorId(operatorId);
        CreateDischargeRequestCommand.CreateDischargeRequestItemCommand item =
            new CreateDischargeRequestCommand.CreateDischargeRequestItemCommand();
        item.setEncounterId(request.getEncounterId());
        item.setPatientId(request.getPatientId());
        if (request.getPlannedDischargeTime() != null) {
            item.setDischargeTime(request.getPlannedDischargeTime());
        } else {
            item.setDischargeTime(LocalDateTime.now());
        }
        item.setReasonText(request.getReasonText());
        cmd.setItems(List.of(item));
        return cmd;
    }

    /**
     * 装配离院里程碑命令（IF-IP-DOR-21）。
     *
     * @param serviceRequestId 出院申请主键
     * @param request          出院请求
     * @param operatorId       操作人
     * @return 领域命令
     */
    @Override
    public RecordDischargeMilestoneCommand toRecordDischargeMilestoneCommand(
        Long serviceRequestId,
        CreateDischargeInpatientDoctorStationAdviceRequest request,
        Long operatorId
    ) {
        RecordDischargeMilestoneCommand cmd = new RecordDischargeMilestoneCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(request.getEncounterId());
        cmd.setRequestId(serviceRequestId);
        cmd.setRequestTableName(TableNameConstants.WKF_SERVICE_REQUEST);
        cmd.setOutWayCode(request.getOutWayCode());
        cmd.setOriginalOrganizationId(request.getSourceOrgId());
        cmd.setTargetOrganizationId(request.getTargetOrgId());
        cmd.setOriginalLocationId(request.getSourceLocationId());
        cmd.setTargetLocationId(request.getTargetLocationId());
        return cmd;
    }

    /**
     * 装配出院开立成功响应。
     *
     * @param serviceRequestId   出院 ServiceRequest 主键
     * @param encounterHistoryId 里程碑主键
     * @return 响应
     */
    @Override
    public CreateDischargeInpatientDoctorStationAdviceResponse toCreateDischargeResponse(
        Long serviceRequestId,
        Long encounterHistoryId
    ) {
        CreateDischargeInpatientDoctorStationAdviceResponse resp = new CreateDischargeInpatientDoctorStationAdviceResponse();
        resp.setServiceRequestId(serviceRequestId);
        resp.setEncounterHistoryId(encounterHistoryId);
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 从批次行解析长临医嘱类型 code，非法时抛业务异常。
     *
     * @param line 暂存批次行
     * @return {@link TherapyType#getCode()}
     */
    private String resolveTherapyTypeCode(InpatientDoctorStationAdviceDraftLineRequest line) {
        String code = trimToEmpty(line.getTherapyTypeCode());
        if (StringUtils.isEmpty(code)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_THERAPY_TYPE_REQUIRED);
        }
        // 仅接受域枚举已注册的 LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION code，禁止前端自造字面量落库。
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, code);
        if (therapyType == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_INVALID_THERAPY_TYPE_PREFIX + code);
        }
        return therapyType.getCode();
    }

    /**
     * 解析 IF-IP-DOR-12 绑定查询来源项键：用法走 {@code method_code}，诊疗走活动定义主键字符串。
     *
     * @param bindType 绑定类型业务码
     * @param itemNo   用法术语 code 或诊疗主键字符串
     * @param itemId   诊疗活动定义主键
     * @return 模板 {@code item_id} 匹配键
     */
    @Override
    public String resolveOrderBindSourceItemKey(String bindType, String itemNo, Long itemId) {
        if (DoctorStationAdviceConstants.BIND_TYPE_USAGE.equals(bindType)) {
            // 用法绑定：模板 item_id 存 method_code，禁止再转 concept 主键
            if (StringUtils.isNotEmpty(itemNo)) {
                return itemNo.trim();
            }
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_BIND_SOURCE_METHOD_CODE_REQUIRED);
        }
        if (DoctorStationAdviceConstants.BIND_TYPE_ACTIVITY.equals(bindType)) {
            if (itemId != null) {
                return String.valueOf(itemId);
            }
            if (StringUtils.isEmpty(itemNo)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_BIND_SOURCE_ITEM_ID_REQUIRED);
            }
            String trimmed = itemNo.trim();
            if (StringUtils.isEmpty(trimmed) || !trimmed.chars().allMatch(Character::isDigit)) {
                throw new BusinessException(
                    ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                    DoctorStationAdviceConstants.MSG_BIND_SOURCE_ITEM_NO_NOT_NUMERIC);
            }
            return trimmed;
        }
        throw new BusinessException(
            ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_BIND_SOURCE_ITEM_ID_REQUIRED);
    }

}
