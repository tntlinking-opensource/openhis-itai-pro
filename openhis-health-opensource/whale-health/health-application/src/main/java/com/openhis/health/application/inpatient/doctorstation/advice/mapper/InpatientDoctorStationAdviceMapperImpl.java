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

package com.openhis.health.application.inpatient.doctorstation.advice.mapper;

import com.openhis.health.application.common.assembler.advice.ProductDefinitionOrderChargePricingSupport;
import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.PrepickedProductKeyDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.SaveInpatientServiceAdviceDraftLinePayload;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CreateDischargeInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CreateDischargeInpatientDoctorStationAdviceResponse;
import com.openhis.health.domain.custom.encounterhistory.command.RecordDischargeMilestoneCommand;
import com.openhis.health.domain.medication.medicationrequest.command.StopLongTermMedicationOrderCommand;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.servicerequest.command.CreateDischargeRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.StopServiceRequestCommand;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.common.validator.PatientEncounterConsistencyGate;
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
import org.apache.commons.lang3.StringUtils;
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
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (PrepickedProductKeyDto k : request.getPrepickedProductKeys()) {
                ProductDefinitionBatchLookup l = new ProductDefinitionBatchLookup();
                l.setProductTableName(k.getProductTableName());
                l.setProductDefinitionId(k.getProductDefinitionId());
                lookups.add(l);
            }
            q.setPrepickedProductKeys(lookups);
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
        cmd.setDispensePerDuration(line.getMedicationPayload().getDispensePerDuration());
        cmd.setSkinTestFlag(line.getMedicationPayload().getSkinTestFlag());
        cmd.setConditionDefinitionId(line.getMedicationPayload().getConditionDefinitionId());
        return cmd;
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
        cmd.setDispensePerDuration(line.getMedicationPayload().getDispensePerDuration());
        cmd.setSkinTestFlag(line.getMedicationPayload().getSkinTestFlag());
        cmd.setConditionDefinitionId(line.getMedicationPayload().getConditionDefinitionId());
        return cmd;
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
        if (line.getServicePayload() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_DRAFT_PAYLOAD_REQUIRED);
        }
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
        item.setQuantity(ServiceAdviceDraftCommandSupport.requireQuantity(
            servicePayload.getQuantity(), servicePayload.getChildOverridesJson()));
        item.setUnitCode(ServiceAdviceDraftCommandSupport.requireUnitCode(
            servicePayload.getUnitCode(), servicePayload.getChildOverridesJson(), productRm));
        String therapyEnum = this.resolveTherapyTypeCode(line);
        item.setTherapyEnum(therapyEnum);
        item.setExecutionCount(ServiceAdviceDraftCommandSupport.requireExecutionCount(
            servicePayload.getExecutionCount(), servicePayload.getChildOverridesJson(), therapyEnum));
        item.setCategoryEnum(StringUtils.firstNonBlank(productRm.getCategoryCode(), "1"));
        item.setRateCode(this.resolveServiceRateCodeForTherapy(therapyEnum, servicePayload.getRateCode()));
        item.setOrgId(servicePayload.getPerformOrgId());
        item.setContractId(servicePayload.getContractId());
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setNote(servicePayload.getChildOverridesJson());
        item.setSortNumber(line.getSortNumber());
        item.setConditionDefinitionId(servicePayload.getConditionDefinitionId());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「修改诊疗医嘱草稿」领域命令（暂存批次 UPDATE / 诊疗分支）。
     *
     * @param line       单行请求，须含 {@code requestId}；备注来自 {@code servicePayload.childOverridesJson}
     * @param operatorId 操作人
     * @return {@link ModifyServiceRequestDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException {@code requestId} 为空时抛出
     */
    @Override
    public ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(InpatientDoctorStationAdviceDraftLineRequest line, Long operatorId) {
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_UPDATE_REQUEST_ID_REQUIRED);
        }
        ModifyServiceRequestDraftCommand cmd = new ModifyServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item = new ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand();
        item.setId(line.getRequestId());
        String therapyEnum = this.resolveTherapyTypeCode(line);
        item.setTherapyEnum(therapyEnum);
        if (line.getServicePayload() != null) {
            SaveInpatientServiceAdviceDraftLinePayload servicePayload = line.getServicePayload();
            item.setRemark(servicePayload.getChildOverridesJson());
            item.setConditionDefinitionId(servicePayload.getConditionDefinitionId());
            item.setRateCode(this.resolveServiceRateCodeForTherapy(therapyEnum, servicePayload.getRateCode()));
            item.setOrgId(servicePayload.getPerformOrgId());
            BigDecimal quantity = ServiceAdviceDraftCommandSupport.resolveQuantityForModify(
                servicePayload.getQuantity(), servicePayload.getChildOverridesJson());
            if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                item.setQuantity(quantity);
            }
            String unitCode = ServiceAdviceDraftCommandSupport.resolveUnitCodeForModify(
                servicePayload.getUnitCode(), servicePayload.getChildOverridesJson());
            if (unitCode != null) {
                item.setUnitCode(unitCode);
            }
            Integer executionCount = ServiceAdviceDraftCommandSupport.resolveExecutionCountForModify(
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
     * 长期诊疗须带频次 code；临时传空由域层清空 rate_code。
     */
    private String resolveServiceRateCodeForTherapy(String therapyEnum, String rateCode) {
        if (TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            if (StringUtils.isBlank(rateCode)) {
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
        RemoveServiceRequestDraftCommand cmd = new RemoveServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand item = new RemoveServiceRequestDraftCommand.RemoveServiceRequestDraftItemCommand();
        item.setId(requestId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「按医嘱锚点作废未计费草稿费用」命令（更新/删除医嘱前调用）。
     *
     * @param encounterId  就诊主键
     * @param serviceTable 医嘱申请物理表名，与 {@link TableNameConstants} 一致
     * @param serviceId    医嘱申请主键
     * @param operatorId   操作人
     * @return {@link VoidUnbilledDraftChargeItemsByServiceAnchorCommand}
     */
    @Override
    public VoidUnbilledDraftChargeItemsByServiceAnchorCommand toVoidDraftChargeAnchor(Long encounterId, String serviceTable, Long serviceId, Long operatorId) {
        VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd = new VoidUnbilledDraftChargeItemsByServiceAnchorCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(encounterId);
        cmd.setServiceTable(serviceTable);
        cmd.setServiceId(serviceId);
        return cmd;
    }

    /**
     * 装配「由定价行生成就诊费用草稿」命令（医嘱暂存后尝试挂费）。
     *
     * @param encounterId  就诊
     * @param patientId    患者
     * @param accountId    自费账户锚点
     * @param orgId        机构
     * @param operatorId   操作人
     * @param requestId              医嘱申请主键，写入 {@code serviceId}
     * @param serviceTable           医嘱申请表名
     * @param productDefinitionTable 产品定义表名（三大目录之一），写入 {@code product_table}
     * @param productDefinitionId    产品定义主键，写入 {@code product_id}
     * @param priceLine              已选定价行，单价来源
     * @param quantity               计价数量，诊疗等可为 null（本实现按 1 处理）
     * @return {@link CreateChargeItemCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException 定价行为 null 时抛出
     */
    @Override
    public CreateChargeItemCommand toCreateChargeItemDraftFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateChargeItemCommand cmd = new CreateChargeItemCommand();
        cmd.setOperatorId(operatorId);
        cmd.setItem(this.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId, requestId, serviceTable,
            productDefinitionTable, productDefinitionId, priceLine, quantity, quantityUnit, productRow));
        return cmd;
    }

    /**
     * 装配单条费用草稿命令项；定价行校验失败时抛出，与单笔医嘱草稿路径一致。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              账户主键
     * @param orgId                  机构主键
     * @param serviceId              上游锚点主键
     * @param serviceTable           上游表名
     * @param productDefinitionTable 产品定义表名（三大目录之一）
     * @param productDefinitionId    产品定义主键
     * @param priceLine              定价行
     * @param quantity               计价数量
     * @return 命令项
     * @throws BusinessException 定价行为空
     */
    @Override
    public CreateChargeItemCommand.Item toCreateChargeItemDraftItemFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long serviceId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateChargeItemCommand.Item it = new CreateChargeItemCommand.Item();
        it.setPatientId(patientId);
        it.setEncounterId(encounterId);
        it.setAccountId(accountId);
        ProductDefinitionOrderChargePricingSupport.applyOrderChargePricing(
            it, productRow, priceLine, quantity, quantityUnit);
        it.setOccurrenceTime(LocalDateTime.now());
        it.setServiceTable(serviceTable);
        it.setServiceId(serviceId);
        // 产品锚点：仅三大目录表名 + 定义主键（DM-ADM-011 / BR-CHG-001；与护士站费用明细 BR-IP-CDT-007 对齐）。
        this.applyProductCatalogAnchor(it, productDefinitionTable, productDefinitionId);
        it.setOrgId(orgId);
        it.setRequestingOrgId(orgId);
        it.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        it.setContextEnum(resolveChargeContext(serviceTable));
        return it;
    }

    /**
     * 装配签发 FIFO 单段费用草稿命令项。
     *
     * <p>先调用 {@link #toCreateChargeItemDraftItemFromOrder} 写入就诊上下文、金额与 {@code service_*}（医嘱请求锚点），
     * 再补齐 {@code product_*}、{@code dispense_*}，与 {@link InpatientDoctorStationAdviceMapper} 契约一致。</p>
     *
     * @param encounterId           就诊主键
     * @param patientId             患者主键
     * @param accountId             账户主键
     * @param orgId                 机构主键
     * @param requestId             医嘱申请主键
     * @param requestServiceTable   医嘱请求表名
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionId   产品定义主键
     * @param dispenseTable         发放表名
     * @param dispenseId            发放主键
     * @param priceLine             定价行
     * @param quantity              计价数量
     * @return 费用草稿命令项
     * @throws BusinessException 定价行校验失败时抛出，语义同 {@link #toCreateChargeItemDraftItemFromOrder}
     */
    @Override
    public CreateChargeItemCommand.Item toCreateChargeItemDraftItemForFifoDispenseSegment(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        Long dispenseId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        CreateChargeItemCommand.Item it = this.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId, requestId, requestServiceTable,
            productDefinitionTable, productDefinitionId, priceLine, quantity, quantityUnit, productRow);
        // FIFO 分段：在 product_* 已落库基础上补 dispense_* 锚点（DM-ADM-011）。
        it.setDispenseTable(dispenseTable);
        it.setDispenseId(dispenseId);
        return it;
    }

    /**
     * 写入产品定义锚点：仅接受三大目录表名与对应定义主键。
     *
     * @param item                   费用草稿命令项
     * @param productDefinitionTable 产品定义表名
     * @param productDefinitionId    产品定义主键
     */
    private void applyProductCatalogAnchor(
        CreateChargeItemCommand.Item item,
        String productDefinitionTable,
        Long productDefinitionId) {
        if (item == null || productDefinitionId == null || StringUtils.isBlank(productDefinitionTable)) {
            return;
        }
        String normalizedTable = productDefinitionTable.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(normalizedTable)
            && !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(normalizedTable)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(normalizedTable)) {
            return;
        }
        item.setProductTable(normalizedTable);
        item.setProductId(productDefinitionId);
    }

    /**
     * 由医嘱锚点物理表名解析收费项上下文枚举，写入费用草稿供报表/结算分桶。
     *
     * @param serviceTable {@link TableNameConstants} 中与 {@code ChargeItem.serviceTable} 一致的表名
     * @return {@link ChargeItemDmConstants} 中上下文码；未识别表名时按诊疗活动上下文处理
     */
    private static String resolveChargeContext(String serviceTable) {
        // 药品请求/发放：费用上下文归入药品桶，便于结算报表分轨。
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_MEDICATION;
        }
        // 耗材请求/发放：归入耗材桶（住院医生站当前不开立耗材，保留与 DM 表名对齐）。
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_DEVICE;
        }
        // 诊疗及其它未识别锚点：默认诊疗活动上下文。
        return ChargeItemDmConstants.CONTEXT_ACTIVITY;
    }

    /**
     * 装配药品医嘱签发命令（批次号 + 处方号）。
     *
     * @param id             药品申请主键
     * @param signCode       签发批次号
     * @param prescriptionNo 处方号（按日序列）
     * @param operatorId     操作人
     * @return {@link SignMedicationOrderCommand}
     */
    @Override
    public SignMedicationOrderCommand toSignMedicationOrderCommand(Long id, String signCode, String prescriptionNo, Long operatorId) {
        SignMedicationOrderCommand c = new SignMedicationOrderCommand();
        c.setId(id);
        c.setOperatorId(operatorId);
        c.setSignCode(signCode);
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
     * @param signCode   签发批次号
     * @param operatorId 操作人，兼作签名人
     * @return {@link SignServiceRequestCommand}
     */
    @Override
    public SignServiceRequestCommand toSignServiceRequestCommand(Long id, String signCode, Long operatorId) {
        SignServiceRequestCommand cmd = new SignServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        SignServiceRequestCommand.SignServiceRequestItemCommand item = new SignServiceRequestCommand.SignServiceRequestItemCommand();
        item.setId(id);
        item.setSignCode(signCode);
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
        WithdrawSignedServiceRequestCommand cmd = new WithdrawSignedServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand item = new WithdrawSignedServiceRequestCommand.WithdrawSignedServiceRequestItemCommand();
        item.setId(id);
        cmd.setItem(item);
        return cmd;
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
        String code = StringUtils.trimToEmpty(line.getTherapyTypeCode());
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_THERAPY_TYPE_REQUIRED);
        }
        // 仅接受域枚举已注册的 LONG_TERM / TEMPORARY code，禁止前端自造字面量落库。
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, code);
        if (therapyType == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_INVALID_THERAPY_TYPE_PREFIX + code);
        }
        return therapyType.getCode();
    }

    /**
     * 解析医嘱绑定查询中的项目主键：优先数值主键，否则将业务项号解析为 Long。
     *
     * @param itemNo 业务项号字符串，可与 {@code itemId} 二选一
     * @param itemId 已解析的主键，非空时直接返回
     * @return 非 null 主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 二者皆空或项号非数字时抛出
     */
    @Override
    public Long parseOrderBindItemId(String itemNo, Long itemId) {
        // 前端已解析数字主键时直接采用，避免重复 parse。
        if (itemId != null) {
            return itemId;
        }
        if (StringUtils.isBlank(itemNo)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_BIND_SOURCE_ITEM_ID_REQUIRED);
        }
        try {
            // 业务项号与主键同口径：须为可解析的 Long 字符串（与门诊 IF-DOR-12 一致）。
            return Long.parseLong(itemNo.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_BIND_SOURCE_ITEM_NO_NOT_NUMERIC);
        }
    }
}
