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

package com.openhis.health.application.outpatient.doctorstation.advice.mapper;

import com.openhis.health.application.common.assembler.advice.ProductDefinitionOrderChargePricingSupport;
import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.PrepickedProductKeyDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveServiceAdviceDraftLinePayload;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.devicerequest.command.ChangeDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RemoveDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignOffDeviceOrderCommand;
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
 * {@link DoctorStationAdviceMapper} 默认实现：将医生站 HTTP 请求形状装配为领域命令/读查询对象。
 *
 * <p><b>职责</b>：字段搬运、默认值（如数量缺省为 1、单位缺省）、资源类型分支前置校验；不实现计价、库存、
 * 医保规则等不变量（由各自 {@code *DomainService} 承担）。</p>
 *
 * <p><b>与 {@link DoctorStationAdviceConstants} 的关系</b>：生成来源、资源类型、行操作等契约字面量集中引用，
 * 避免与前端 IF-DOR 文档漂移。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationAdviceMapperImpl implements DoctorStationAdviceMapper {

    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /**
     * 校验请求中的患者与门诊读模型上的患者是否一致。
     *
     * <p>实现要点：任一侧为 null 或不相等即失败，防止在错误患者卡片上开立医嘱。</p>
     *
     * @param patientId          请求上下文患者主键
     * @param encounterPatientId 就诊读模型中的患者主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 不一致或缺失时抛出
     */
    @Override
    public void validatePatientEncounter(Long patientId, Long encounterPatientId) {
        this.patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounterPatientId,
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "患者标识与就诊读模型不一致");
    }

    /**
     * 将医生站医嘱目录分页请求装配为产品定义读模型分页查询命令（IF-DOR-11 Pre）。
     *
     * <p>实现要点：默认限定三类产品表（药/诊疗活动/耗材定义）；若请求显式传入表名集合则完全尊重前端筛选。
     * 预勾选键用于分页首屏保证某些定义行一定出现（如从上一页带入）。</p>
     *
     * @param request 前端分页与筛选条件
     * @return 下游读模型查询使用的 {@link ProductDefinitionPageQuery}
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageDoctorStationAdviceCatalogRequest request) {
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
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                TableNameConstants.ADM_DEVICE_DEFINITION
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
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    ) {
        if (line.getMedicationPayload() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "药品草稿载荷不能为空");
        }
        CreateMedicationOrderDraftCommand cmd = new CreateMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setEncounterId(parent.getEncounterId());
        cmd.setPatientId(parent.getPatientId());
        cmd.setMedicationId(line.getMedicationPayload().getMedicationDefinitionId());
        cmd.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        BigDecimal medQty = line.getMedicationPayload().getQuantity();
        // 数量缺省为 1：与收费草稿按「至少一笔」计价及域服务默认值对齐。
        if (medQty == null) {
            medQty = BigDecimal.ONE;
        }
        cmd.setQuantity(medQty);
        // 单位缺省 "U"：占位直至主数据补齐单位码；域层可再校验。
        cmd.setUnitCode(StringUtils.firstNonBlank(line.getMedicationPayload().getUnitCode(), "U"));
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
    public ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "药品医嘱更新时 requestId 不能为空");
        }
        if (line.getMedicationPayload() == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "药品草稿载荷不能为空");
        }
        ChangeMedicationOrderDraftCommand cmd = new ChangeMedicationOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        cmd.setId(line.getRequestId());
        cmd.setQuantity(line.getMedicationPayload().getQuantity());
        cmd.setUnitCode(line.getMedicationPayload().getUnitCode());
        cmd.setMethodCode(line.getMedicationPayload().getMethodCode());
        cmd.setRateCode(line.getMedicationPayload().getRateCode());
        cmd.setTherapyEnum(TherapyType.TEMPORARY.getCode());
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
     * 装配「新建耗材医嘱草稿」领域命令（暂存批次 CREATE / 耗材分支）。
     *
     * @param parent     批次父上下文
     * @param line       单行请求，须含 {@code devicePayload}
     * @param operatorId 操作人
     * @return {@link CreateDeviceOrderDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException 耗材载荷为空时抛出
     */
    @Override
    public CreateDeviceOrderDraftCommand toCreateDeviceOrderDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId
    ) {
        if (line.getDevicePayload() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材草稿载荷不能为空");
        }
        CreateDeviceOrderDraftCommand cmd = new CreateDeviceOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand item = new CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand();
        item.setEncounterId(parent.getEncounterId());
        item.setPatientId(parent.getPatientId());
        item.setDeviceDefId(line.getDevicePayload().getDeviceDefinitionId());
        BigDecimal devQty = line.getDevicePayload().getQuantity();
        if (devQty == null) {
            devQty = BigDecimal.ONE;
        }
        item.setQuantity(devQty);
        item.setUnitCode(StringUtils.firstNonBlank(line.getDevicePayload().getUnitCode(), "U"));
        item.setPerformLocation(line.getDevicePayload().getPerformOrgId());
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setSortNumber(line.getSortNumber());
        item.setConditionDefinitionId(line.getDevicePayload().getConditionDefinitionId());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「修改耗材医嘱草稿」领域命令（暂存批次 UPDATE / 耗材分支）。
     *
     * @param line       单行请求，须含 {@code requestId}
     * @param operatorId 操作人
     * @return {@link ChangeDeviceOrderDraftCommand}
     * @throws com.openhis.whale.core.application.exception.BusinessException {@code requestId} 为空时抛出
     */
    @Override
    public ChangeDeviceOrderDraftCommand toChangeDeviceOrderDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_REQUEST_INVALID_ARGUMENT, "耗材医嘱更新时 requestId 不能为空");
        }
        ChangeDeviceOrderDraftCommand cmd = new ChangeDeviceOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand item = new ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand();
        item.setId(line.getRequestId());
        if (line.getDevicePayload() != null) {
            item.setQuantity(line.getDevicePayload().getQuantity());
            item.setUnitCode(line.getDevicePayload().getUnitCode());
            item.setPerformLocation(line.getDevicePayload().getPerformOrgId());
            item.setConditionDefinitionId(line.getDevicePayload().getConditionDefinitionId());
        }
        item.setSortNumber(line.getSortNumber());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配「删除耗材医嘱草稿」领域命令。
     *
     * @param requestId  耗材申请主键
     * @param operatorId 操作人
     * @return {@link RemoveDeviceOrderDraftCommand}
     */
    @Override
    public RemoveDeviceOrderDraftCommand toRemoveDeviceOrderDraftCommand(Long requestId, Long operatorId) {
        RemoveDeviceOrderDraftCommand cmd = new RemoveDeviceOrderDraftCommand();
        cmd.setOperatorId(operatorId);
        RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand item = new RemoveDeviceOrderDraftCommand.RemoveDeviceOrderDraftItemCommand();
        item.setId(requestId);
        cmd.setItem(item);
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
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        ProductDefinitionDisplayDto productRm,
        Long operatorId
    ) {
        if (line.getServicePayload() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "诊疗草稿载荷不能为空");
        }
        if (productRm == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "诊疗活动读模型不能为空");
        }
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item = new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(parent.getEncounterId());
        item.setPatientId(parent.getPatientId());
        SaveServiceAdviceDraftLinePayload servicePayload = line.getServicePayload();
        item.setActivityId(servicePayload.getActivityDefinitionId());
        item.setQuantity(ServiceAdviceDraftCommandSupport.requireQuantity(
            servicePayload.getQuantity(), servicePayload.getChildOverridesJson()));
        item.setUnitCode(ServiceAdviceDraftCommandSupport.requireUnitCode(
            servicePayload.getUnitCode(), servicePayload.getChildOverridesJson(), productRm));
        item.setExecutionCount(ServiceAdviceDraftCommandSupport.requireExecutionCount(
            servicePayload.getExecutionCount(), servicePayload.getChildOverridesJson(), TherapyType.TEMPORARY.getCode()));
        item.setCategoryEnum(StringUtils.firstNonBlank(productRm.getCategoryCode(), "1"));
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
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
    public ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "诊疗医嘱更新时 requestId 不能为空");
        }
        ModifyServiceRequestDraftCommand cmd = new ModifyServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item = new ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand();
        item.setId(line.getRequestId());
        if (line.getServicePayload() != null) {
            SaveServiceAdviceDraftLinePayload servicePayload = line.getServicePayload();
            item.setRemark(servicePayload.getChildOverridesJson());
            item.setConditionDefinitionId(servicePayload.getConditionDefinitionId());
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
     * 再补齐 {@code product_*}、{@code dispense_*}，与 {@link DoctorStationAdviceMapper} 契约一致。</p>
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
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_MEDICATION;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            return ChargeItemDmConstants.CONTEXT_DEVICE;
        }
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
     * 装配耗材医嘱签发命令。
     *
     * @param id         耗材申请主键
     * @param operatorId 操作人
     * @return {@link SignDeviceOrderCommand}
     */
    @Override
    public SignDeviceOrderCommand toSignDeviceOrderCommand(Long id, Long operatorId) {
        SignDeviceOrderCommand cmd = new SignDeviceOrderCommand();
        cmd.setOperatorId(operatorId);
        SignDeviceOrderCommand.SignDeviceOrderItemCommand item = new SignDeviceOrderCommand.SignDeviceOrderItemCommand();
        item.setId(id);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配耗材医嘱签退命令。
     *
     * @param id         耗材申请主键
     * @param operatorId 操作人
     * @return {@link SignOffDeviceOrderCommand}
     */
    @Override
    public SignOffDeviceOrderCommand toSignOffDeviceOrderCommand(Long id, Long operatorId) {
        SignOffDeviceOrderCommand cmd = new SignOffDeviceOrderCommand();
        cmd.setOperatorId(operatorId);
        SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand item = new SignOffDeviceOrderCommand.SignOffDeviceOrderItemCommand();
        item.setId(id);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配药品医嘱成组/清组命令（IF-DOR-17）。
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
     * 解析医嘱绑定查询中的项目主键：优先数值主键，否则将业务项号解析为 Long。
     *
     * @param itemNo 业务项号字符串，可与 {@code itemId} 二选一
     * @param itemId 已解析的主键，非空时直接返回
     * @return 非 null 主键
     * @throws com.openhis.whale.core.application.exception.BusinessException 二者皆空或项号非数字时抛出
     */
    @Override
    public Long parseOrderBindItemId(String itemNo, Long itemId) {
        if (itemId != null) {
            return itemId;
        }
        if (StringUtils.isBlank(itemNo)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "主数据主键不能为空");
        }
        try {
            return Long.parseLong(itemNo.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "主数据项号无法解析为数值主键");
        }
    }
}
