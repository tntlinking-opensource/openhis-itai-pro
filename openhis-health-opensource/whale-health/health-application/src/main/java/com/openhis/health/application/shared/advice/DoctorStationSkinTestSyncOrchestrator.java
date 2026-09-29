/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
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
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 门诊同步挂操作费草稿，住院开嘱侧不挂费（护士执行皮试检查后计费，皮试 Tab 仅登记结果）。
 * 住院换批重做：同一皮试药可挂多条 companion（{@link #appendCompanionSkinTestServiceRequest}）。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationSkinTestSyncOrchestrator {

    /** 诊疗活动定义只读查询（解析「皮试检查」活动主键）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 诊疗申请只读查询（按 based_on 锚点查找 companion SR）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 诊疗申请写侧领域服务（草稿创建/修改/签退）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;
    /** 检查工单接单编排（皮试检查 companion 签退时同步作废 PacsWorkItem）。 */
    private final PacsOrderIngressOrchestrator pacsOrderIngressOrchestrator;
    /** 费用行写侧领域服务（门诊皮试操作费挂费/作废）。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 临床目录定价解析（皮试检查零售价）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    /** 产品定义读模型批量加载（皮试活动目录行）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

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
     *
     * @param chargePolicy        挂费策略（门诊/住院）
     * @param encounterId         就诊主键
     * @param patientId           患者主键
     * @param accountId           账户主键（门诊挂费）
     * @param orgId               执行机构主键
     * @param operatorId          操作人
     * @param medicationRequestId   皮试药品申请主键
     * @param medicationRequestId   皮试药品申请主键
     * @param payload             皮试药草稿规格（诊断/合同等）
     */
    public void syncAfterMedicationDraftLine(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medicationRequestId,
        SkinTestMedicationDraftSpec payload
    ) {
        if (medicationRequestId == null || payload == null) {
            return;
        }
        // 业务分支：勾选皮试则 upsert companion SR，否则清理草稿态 companion
        if (Boolean.TRUE.equals(payload.getSkinTestFlag())) {
            this.upsertCompanionSkinTestServiceRequest(
                chargePolicy, encounterId, patientId, accountId, orgId, operatorId,
                medicationRequestId, payload);
        } else {
            this.removeDraftCompanionSkinTestServiceRequest(
                chargePolicy, encounterId, medicationRequestId, operatorId);
        }
    }

    /**
     * 软删草稿态 companion 皮试检查 SR；门诊同步作废未计费费用。
     *
     * @param chargePolicy        挂费策略（门诊/住院）
     * @param encounterId         就诊主键
     * @param medicationRequestId 皮试药品申请主键
     * @param operatorId          操作人
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
        // 换批后同药可有多条 companion：取消防皮试时软删全部草稿态联动 SR
        List<ServiceRequest> companions =
            this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
        for (ServiceRequest companion : companions) {
            if (companion == null || companion.getId() == null) {
                continue;
            }
            // 业务分支：已签发 companion 由签退链路处理，暂存删改仅同步清理草稿态
            if (!RequestStatus.DRAFT.getCode().equals(companion.getStatusEnum())) {
                continue;
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
    }

    /**
     * 签发批次含皮试药时，自动将锚定该药的 companion 皮试检查 SR 追加进待签发集合。
     *
     * @param signTargets 原待签发目标列表
     * @return 追加 companion SR 后的列表；无皮试药时返回原列表引用
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
            String productTable = ProductTableNameHelper.normalize(target.getProductTableName());
            if (ProductTableNameHelper.isActivity(productTable)) {
                existingServiceRequestIds.add(target.getRequestId());
            } else if (ProductTableNameHelper.isMedication(productTable)) {
                skinTestMedicationRequestIds.add(target.getRequestId());
            }
        }
        if (skinTestMedicationRequestIds.isEmpty()) {
            return signTargets;
        }
        List<AdviceSignTargetSpec> expanded = new ArrayList<>(signTargets);
        // 换批追加后同药可有多条草稿 companion：签发须全部扩入
        for (Long medicationRequestId : skinTestMedicationRequestIds) {
            List<ServiceRequest> companions =
                this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
            for (ServiceRequest companion : companions) {
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
        }
        return expanded;
    }

    /**
     * 签退皮试药时对称处理锚定该药的 companion 皮试检查 SR；门诊同步回退费用。
     *
     * @param chargePolicy        挂费策略（门诊/住院）
     * @param encounterId         就诊主键
     * @param medicationRequestId 皮试药品申请主键
     * @param operatorId          操作人
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
        List<ServiceRequest> companions =
            this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
        // 先清草稿，再对称签退已签发 companion（换批多条须全部处理）
        this.removeDraftCompanionSkinTestServiceRequest(chargePolicy, encounterId, medicationRequestId, operatorId);
        for (ServiceRequest companion : companions) {
            if (companion == null || companion.getId() == null) {
                continue;
            }
            // 仅已发送态可签退；草稿/已签退跳过，避免同批显式勾选 companion 时二次签退报错
            if (!RequestStatus.ACTIVE.getCode().equals(companion.getStatusEnum())) {
                continue;
            }
            if (chargePolicy == ChargePolicy.OUTPATIENT) {
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, companion.getId());
            }
            serviceRequestDomainService.withdrawSignedServiceRequest(
                ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(companion.getId(), operatorId));
            pacsOrderIngressOrchestrator.withdrawSignedImagingServiceRequest(companion.getId(), operatorId);
        }
    }

    /**
     * 收集皮试药将联动签退的 companion「皮试检查」SR id（同批勾选药+companion 时去重用）。
     *
     * @param medicationRequestIds 本批待签退药品申请主键
     * @return companion SR id 集合；无则空集
     */
    public Set<Long> collectCompanionSkinTestServiceRequestIds(List<Long> medicationRequestIds) {
        Set<Long> companionIds = new HashSet<>();
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return companionIds;
        }
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        for (Long medicationRequestId : medicationRequestIds) {
            if (medicationRequestId == null) {
                continue;
            }
            List<ServiceRequest> companions =
                this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
            for (ServiceRequest companion : companions) {
                if (companion == null || companion.getId() == null) {
                    continue;
                }
                companionIds.add(companion.getId());
            }
        }
        return companionIds;
    }

    /**
     * 住院换批追加 companion「皮试检查」临嘱（IF-IP-DOR-27 / BR-IP-ORD-024③）。
     *
     * <p>已有草稿 companion 时复用其 id（不重复建）；否则新建，{@code based_on} 锚皮试药；
     * 住院不挂费。门诊调用方勿用本方法（门诊仍走勾选联动 + 挂费）。</p>
     *
     * @param encounterId         就诊主键
     * @param patientId           患者主键
     * @param orgId               执行机构（继承皮试药）
     * @param operatorId          操作人
     * @param medicationRequestId 长期皮试药主键（based_on 锚点）
     * @param payload             诊断/合同等继承字段；{@code skinTestFlag} 须为 true
     * @return 新建或复用的 companion 诊疗申请主键
     */
    public Long appendCompanionSkinTestServiceRequest(
        Long encounterId,
        Long patientId,
        Long orgId,
        Long operatorId,
        Long medicationRequestId,
        SkinTestMedicationDraftSpec payload
    ) {
        if (medicationRequestId == null || payload == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "追加皮试检查须指定皮试药与草稿规格");
        }
        // Gate：追加仅针对已勾皮试的药品
        if (!Boolean.TRUE.equals(payload.getSkinTestFlag())) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "仅已勾选皮试的药品可追加皮试检查");
        }
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        // 已有未签发草稿：复用，避免同药堆积未签发皮试检查
        ServiceRequest draftExisting =
            this.findDraftCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
        if (draftExisting != null && draftExisting.getId() != null) {
            return draftExisting.getId();
        }
        ProductDefinitionDisplayDto activityProductRow = this.loadSkinTestActivityProductRow(skinTestActivityId);
        BigDecimal quantity = BigDecimal.ONE;
        String unitCode = ServiceAdviceDraftCommandHelper.requireUnitCode(null, null, activityProductRow);
        String therapyEnum = TherapyType.TEMPORARY.getCode();
        CreateServiceRequestDraftCommand createCmd = this.buildCreateSkinTestServiceRequestCommand(
            encounterId, patientId, operatorId, medicationRequestId, skinTestActivityId,
            activityProductRow, orgId, therapyEnum, payload, quantity, unitCode);
        return serviceRequestDomainService.createServiceRequestDraft(createCmd);
    }

    /**
     * 皮试药 companion 诊疗申请 upsert：无则建草稿（门诊同步挂费），有草稿则修改并重挂费。
     *
     * @param chargePolicy         挂费策略（门诊/住院）
     * @param encounterId          就诊 ID
     * @param patientId            患者 ID
     * @param accountId            账户 ID（门诊挂费）
     * @param orgId                执行机构
     * @param operatorId           操作人
     * @param medicationRequestId  皮试药品请求 ID（based_on 锚点）
     * @param payload              诊断与合同等草稿字段
     */
    private void upsertCompanionSkinTestServiceRequest(
        ChargePolicy chargePolicy,
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long medicationRequestId,
        SkinTestMedicationDraftSpec payload
    ) {
        Long skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
            GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        ProductDefinitionDisplayDto activityProductRow = this.loadSkinTestActivityProductRow(skinTestActivityId);
        // 暂存联动：仅维护草稿 companion；已签发历史留给换批追加（IF-IP-DOR-27）
        ServiceRequest existing =
            this.findDraftCompanionSkinTestServiceRequest(medicationRequestId, skinTestActivityId);
        Long performOrgId = orgId;
        BigDecimal quantity = BigDecimal.ONE;
        String unitCode = ServiceAdviceDraftCommandHelper.requireUnitCode(null, null, activityProductRow);
        // 皮试检查为一次性操作：门诊/住院均固定临嘱（对齐 BR-ORD-025；无长期皮试）
        String therapyEnum = TherapyType.TEMPORARY.getCode();
        if (existing == null) {
            // 业务分支：同药已有已签发 companion 时，勾选暂存不再自动再建（避免无意多开；换批走 append）
            List<ServiceRequest> allCompanions =
                this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
            if (!allCompanions.isEmpty()) {
                return;
            }
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

    /**
     * 组装皮试 companion 诊疗申请创建 Command（医生处方来源、锚定药品请求）。
     *
     * @param encounterId          就诊 ID
     * @param patientId            患者 ID
     * @param operatorId           操作人
     * @param medicationRequestId  药品请求 ID
     * @param skinTestActivityId   皮试检查活动 ID
     * @param activityProductRow   活动产品读模型
     * @param performOrgId         执行机构
     * @param therapyEnum          固定 {@link TherapyType#TEMPORARY}（皮试无长期）
     * @param payload              诊断/合同等
     * @param quantity             数量
     * @param unitCode             单位 code
     * @return 创建草稿 Command
     */
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
        item.setExecutionCount(ServiceAdviceDraftCommandHelper.requireExecutionCount(1, null, therapyEnum));
        item.setCategoryEnum(StringUtils.firstNonBlank(activityProductRow.getCategoryCode(), "1"));
        item.setTherapyEnum(therapyEnum);
        item.setOrgId(performOrgId);
        item.setContractId(payload.getContractId());
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setEncounterDiagnosisIds(payload.getEncounterDiagnosisIds());
        item.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
        item.setBasedOnId(medicationRequestId);
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 组装皮试 companion 诊疗申请修改 Command（仅草稿相位可改数量、单位与诊断锚点）。
     *
     * @param serviceRequestId companion 诊疗申请 ID
     * @param operatorId       操作人
     * @param performOrgId     执行机构
     * @param payload          诊断锚点
     * @param quantity         数量
     * @param unitCode         单位 code
     * @return 修改草稿 Command
     */
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
        item.setEncounterDiagnosisIds(payload.getEncounterDiagnosisIds());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 门诊皮试 companion 挂操作费草稿：解析皮试检查零售价并创建未计费 ChargeItem。
     *
     * @param encounterId        就诊 ID
     * @param patientId          患者 ID
     * @param accountId          账户 ID
     * @param orgId              机构 ID
     * @param operatorId         操作人
     * @param serviceRequestId   companion 诊疗申请 ID
     * @param skinTestActivityId 皮试活动 ID
     * @param activityProductRow 活动产品读模型
     * @param quantity           数量
     * @param unitCode           单位 code
     * @throws BusinessException 未配置皮试检查零售价
     */
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
        ChargeItemDefinition priceLine = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
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

    /**
     * 按药品请求锚点列出全部皮试 companion 诊疗申请（医生处方 + 皮试活动；含草稿与已签发）。
     *
     * @param medicationRequestId 药品请求 ID
     * @param skinTestActivityId  皮试检查活动 ID
     * @return companion 列表；无则空列表
     */
    private List<ServiceRequest> listCompanionSkinTestServiceRequests(
        Long medicationRequestId,
        Long skinTestActivityId
    ) {
        List<ServiceRequest> candidates = serviceRequestQueryService.listServiceRequestsByBasedOnAnchor(
            TableNameConstants.MED_MEDICATION_REQUEST, medicationRequestId);
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<ServiceRequest> result = new ArrayList<>();
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
            result.add(candidate);
        }
        return result;
    }

    /**
     * 查找同药下草稿态皮试 companion（换批多条时暂存/追加优先锚草稿）。
     *
     * @param medicationRequestId 药品请求 ID
     * @param skinTestActivityId  皮试检查活动 ID
     * @return 首条草稿 companion；无则 null
     */
    private ServiceRequest findDraftCompanionSkinTestServiceRequest(
        Long medicationRequestId,
        Long skinTestActivityId
    ) {
        List<ServiceRequest> companions =
            this.listCompanionSkinTestServiceRequests(medicationRequestId, skinTestActivityId);
        for (ServiceRequest companion : companions) {
            if (companion == null) {
                continue;
            }
            if (RequestStatus.DRAFT.getCode().equals(companion.getStatusEnum())) {
                return companion;
            }
        }
        return null;
    }

    /**
     * 加载「皮试检查」诊疗活动产品读模型；缺失时阻断开嘱同步。
     *
     * @param skinTestActivityId 皮试活动定义 ID
     * @return 产品展示 DTO
     * @throws BusinessException 活动定义未维护
     */
    private ProductDefinitionDisplayDto loadSkinTestActivityProductRow(Long skinTestActivityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(skinTestActivityId);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
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
