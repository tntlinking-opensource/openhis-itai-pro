/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.module.orchestration.advice;

import com.openhis.health.module.common.assembler.advice.OrderChargeDraftAssembler;
import com.openhis.health.module.common.assembler.advice.ServiceAdviceCommandAssembler;
import com.openhis.health.module.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.module.common.assembler.advice.model.AdviceSignTargetSpec;
import com.openhis.health.module.common.assembler.advice.model.SkinTestMedicationDraftSpec;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.assembler.product.ProductTableNameSupport;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊/住院医生站皮试联动写编排（AS-OP-DOR-003 / AS-IP-DOR-003 · BR-ORD-025 / BR-IP-ORD-024）。
 *
 * <p>皮试药 {@code skinTestFlag=true} 时联动 doctor-prescription「皮试检查」{@code ServiceRequest}；
 * 门诊同步挂操作费草稿，住院开嘱侧不挂费（护士皮试 Tab 登记时锚既有 doctor SR 计费）。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationSkinTestSyncOrchestrationSupport {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /**
     * 皮试挂费策略：门诊开嘱侧挂操作费草稿；住院不挂费。
     */
    public enum ChargePolicy {
        /** 住院：不挂费。 */
        INPATIENT,
        /** 门诊：挂操作费草稿。 */
        OUTPATIENT
    }

    /**
     * 药品草稿保存后按皮试标志创建/维持或清理 companion 皮试检查 SR。
     */
    public void syncAfterMedicationDraftLine(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medicationRequestId,
        String therapyTypeCode,
        SkinTestMedicationDraftSpec payload
    ) {
        if (medicationRequestId == null || payload == null) {
            return;
        }
        // 业务分支：勾选皮试则 upsert companion SR，否则清理草稿态 companion
        if (Boolean.TRUE.equals(payload.getSkinTestFlag())) {
            this.upsertCompanionSkinTestServiceRequest(
                chargePolicy, encounterId, patientId, accountId, orgId, operatorId,
                medicationRequestId, therapyTypeCode, payload);
        } else {
            this.removeDraftCompanionSkinTestServiceRequest(
                chargePolicy, encounterId, medicationRequestId, operatorId);
        }
    }

    /**
     * 软删草稿态 companion 皮试检查 SR；门诊同步作废未计费费用。
     */
    public void removeDraftCompanionSkinTestServiceRequest(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long medicationRequestId,
        Long operatorId
    ) {
        if (medicationRequestId == null) {
            return;
        }
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        ServiceRequest companion = this.findCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
        if (companion == null) {
            return;
        }
        // 业务分支：已签发 companion 由签退链路处理，暂存删改仅同步清理草稿态
        if (!RequestStatus.DRAFT.getCode().equals(companion.getStatusEnum())) {
            return;
        }
        // 业务分支：门诊须先作废锚定 SR 的未计费草稿费用
        if (chargePolicy == ChargePolicy.OUTPATIENT) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, companion.getId(), operatorId));
        }
        serviceRequestDomainService.removeServiceRequestDraft(
            ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(companion.getId(), operatorId));
    }

    /**
     * 签发批次含皮试药时，自动将锚定该药的 companion 皮试检查 SR 追加进待签发集合。
     */
    public List<AdviceSignTargetSpec> expandSignTargetsWithLinkedSkinTestServiceRequests(
        List<AdviceSignTargetSpec> signTargets
    ) {
        if (signTargets == null || signTargets.isEmpty()) {
            return signTargets;
        }
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        Set<Long> existingServiceRequestIds = new HashSet<>();
        List<Long> skinTestMedicationRequestIds = new ArrayList<>();
        for (AdviceSignTargetSpec target : signTargets) {
            if (target == null || target.getRequestId() == null) {
                continue;
            }
            String productTable = ProductTableNameSupport.normalize(target.getProductTableName());
            if (ProductTableNameSupport.isActivity(productTable)) {
                existingServiceRequestIds.add(target.getRequestId());
            } else if (ProductTableNameSupport.isMedication(productTable)) {
                skinTestMedicationRequestIds.add(target.getRequestId());
            }
        }
        if (skinTestMedicationRequestIds.isEmpty()) {
            return signTargets;
        }
        List<AdviceSignTargetSpec> expanded = new ArrayList<>(signTargets);
        for (Long medicationRequestId : skinTestMedicationRequestIds) {
            ServiceRequest companion = this.findCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
            if (companion == null || companion.getId() == null) {
                continue;
            }
            if (!RequestStatus.DRAFT.getCode().equals(companion.getStatusEnum())) {
                continue;
            }
            if (existingServiceRequestIds.contains(companion.getId())) {
                continue;
            }
            AdviceSignTargetSpec extra = new AdviceSignTargetSpec();
            extra.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            extra.setRequestId(companion.getId());
            expanded.add(extra);
            existingServiceRequestIds.add(companion.getId());
        }
        return expanded;
    }

    /**
     * 签退皮试药时对称处理锚定该药的 companion 皮试检查 SR；门诊同步回退费用。
     */
    public void withdrawLinkedSkinTestServiceRequestIfPresent(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long medicationRequestId,
        Long operatorId
    ) {
        if (medicationRequestId == null) {
            return;
        }
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        ServiceRequest companion = this.findCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
        if (companion == null || companion.getId() == null) {
            return;
        }
        String status = companion.getStatusEnum();
        if (RequestStatus.DRAFT.getCode().equals(status)) {
            this.removeDraftCompanionSkinTestServiceRequest(chargePolicy, encounterId, medicationRequestId, operatorId);
            return;
        }
        if (chargePolicy == ChargePolicy.OUTPATIENT) {
            chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                encounterId, TableNameConstants.WKF_SERVICE_REQUEST, companion.getId());
        }
        serviceRequestDomainService.withdrawSignedServiceRequest(
            ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(companion.getId(), operatorId));
    }

    private void upsertCompanionSkinTestServiceRequest(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medicationRequestId,
        String therapyTypeCode,
        SkinTestMedicationDraftSpec payload
    ) {
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        ProductDefinitionDisplayDto activityProductRow = this.loadSkinTestActivityProductRow(skinTestActivityId);
        ServiceRequest existing = this.findCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
        Long performOrgId = orgId;
        BigDecimal quantity = BigDecimal.ONE;
        String unitCode = ServiceAdviceDraftCommandSupport.requireUnitCode(null, null, activityProductRow);
        String therapyEnum = this.resolveTherapyEnumForSkinTestCompanion(chargePolicy, therapyTypeCode);
        if (existing == null) {
            CreateServiceRequestDraftCommand createCmd = this.buildCreateSkinTestServiceRequestCommand(
                encounterId, patientId, operatorId, medicationRequestId, skinTestActivityId,
                activityProductRow, performOrgId, therapyEnum, payload, quantity, unitCode);
            Long serviceRequestId = serviceRequestDomainService.createServiceRequestDraft(createCmd);
            // 业务分支：门诊开嘱侧同步挂皮试检查操作费草稿
            if (chargePolicy == ChargePolicy.OUTPATIENT) {
                this.createSkinTestChargeDraft(
                    encounterId, patientId, accountId, orgId, operatorId,
                    serviceRequestId, skinTestActivityId, activityProductRow, quantity, unitCode);
            }
            return;
        }
        if (!RequestStatus.DRAFT.getCode().equals(existing.getStatusEnum())) {
            // 已签发 companion 不在暂存相位改写，避免与护士登记/计费事实冲突
            return;
        }
        // 业务分支：门诊 modify 前先作废旧草稿费用，避免双挂
        if (chargePolicy == ChargePolicy.OUTPATIENT) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, existing.getId(), operatorId));
        }
        ModifyServiceRequestDraftCommand modifyCmd = this.buildModifySkinTestServiceRequestCommand(
            existing.getId(), operatorId, performOrgId, payload, quantity, unitCode);
        serviceRequestDomainService.modifyServiceRequestDraft(modifyCmd);
        if (chargePolicy == ChargePolicy.OUTPATIENT) {
            this.createSkinTestChargeDraft(
                encounterId, patientId, accountId, orgId, operatorId,
                existing.getId(), skinTestActivityId, activityProductRow, quantity, unitCode);
        }
    }

    private CreateServiceRequestDraftCommand buildCreateSkinTestServiceRequestCommand(
        Long encounterId,
        Long patientId,
        Long operatorId,
        Long medicationRequestId,
        Long skinTestActivityId,
        ProductDefinitionDisplayDto activityProductRow,
        Long performOrgId,
        String therapyEnum,
        SkinTestMedicationDraftSpec payload,
        BigDecimal quantity,
        String unitCode
    ) {
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setActivityId(skinTestActivityId);
        item.setQuantity(quantity);
        item.setUnitCode(unitCode);
        item.setExecutionCount(ServiceAdviceDraftCommandSupport.requireExecutionCount(1, null, therapyEnum));
        item.setCategoryEnum(StringUtils.firstNonBlank(activityProductRow.getCategoryCode(), "1"));
        item.setTherapyEnum(therapyEnum);
        item.setOrgId(performOrgId);
        item.setContractId(payload.getContractId());
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setConditionDefinitionId(payload.getConditionDefinitionId());
        item.setEncounterDiagnosisId(payload.getEncounterDiagnosisId());
        item.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
        item.setBasedOnId(medicationRequestId);
        cmd.setItem(item);
        return cmd;
    }

    private ModifyServiceRequestDraftCommand buildModifySkinTestServiceRequestCommand(
        Long serviceRequestId,
        Long operatorId,
        Long performOrgId,
        SkinTestMedicationDraftSpec payload,
        BigDecimal quantity,
        String unitCode
    ) {
        ModifyServiceRequestDraftCommand cmd = new ModifyServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand item =
            new ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand();
        item.setId(serviceRequestId);
        item.setQuantity(quantity);
        item.setUnitCode(unitCode);
        item.setOrgId(performOrgId);
        item.setConditionDefinitionId(payload.getConditionDefinitionId());
        item.setEncounterDiagnosisId(payload.getEncounterDiagnosisId());
        cmd.setItem(item);
        return cmd;
    }

    private void createSkinTestChargeDraft(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long serviceRequestId,
        Long skinTestActivityId,
        ProductDefinitionDisplayDto activityProductRow,
        BigDecimal quantity,
        String unitCode
    ) {
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolveSupport.resolveDraftChargePriceLine(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            skinTestActivityId,
            activityProductRow,
            orgId,
            encounterId,
            null,
            null);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置皮试检查收费定价（须零售价 SALE），请先维护后再保存皮试医嘱。");
        }
        CreateChargeItemCommand.Item draftItem = OrderChargeDraftAssembler.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId,
            serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, skinTestActivityId,
            priceLine, quantity, unitCode, activityProductRow);
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(List.of(draftItem));
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    private ServiceRequest findCompanionSkinTestServiceRequest(Long medicationRequestId, Long skinTestActivityId) {
        List<ServiceRequest> candidates = serviceRequestQueryService.listServiceRequestsByBasedOnAnchor(
            TableNameConstants.MED_MEDICATION_REQUEST, medicationRequestId);
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (ServiceRequest candidate : candidates) {
            if (candidate == null || Boolean.TRUE.equals(candidate.getIsDeleted())) {
                continue;
            }
            if (!doctorSource.equals(candidate.getGenerateSourceEnum())) {
                continue;
            }
            if (!skinTestActivityId.equals(candidate.getActivityId())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /**
     * 解析皮试 companion SR 的长临属性：住院继承皮试药同行，门诊固定临嘱。
     *
     * @param chargePolicy    挂费策略
     * @param therapyTypeCode 皮试药长临 code（住院侧）
     * @return 写入 companion SR 的 therapy code
     */
    private String resolveTherapyEnumForSkinTestCompanion(ChargePolicy chargePolicy, String therapyTypeCode) {
        if (chargePolicy == ChargePolicy.INPATIENT) {
            return StringUtils.firstNonBlank(therapyTypeCode, TherapyType.TEMPORARY.getCode());
        }
        return TherapyType.TEMPORARY.getCode();
    }

    private ProductDefinitionDisplayDto loadSkinTestActivityProductRow(Long skinTestActivityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(skinTestActivityId);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, skinTestActivityId);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "未找到皮试检查诊疗活动定义，请先在基础数据维护「皮试检查」项目。");
        }
        return row;
    }
}
