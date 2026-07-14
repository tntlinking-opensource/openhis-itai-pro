/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.advice.OrderChargeDraftAssembler;
import com.openhis.health.application.shared.advice.ServiceAdviceCommandAssembler;
import com.openhis.health.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IF-DOR-37 代煎费写路径编排：同处方号下签发代煎 {@code ServiceRequest} 并按付数挂 {@code PLANNED} 费用。
 *
 * <p>与皮试 companion 类似，代煎为方级诊疗 companion，须落库 {@code wkf_service_request} 后再锚定收费明细。</p>
 */
@Component
@RequiredArgsConstructor
public class TcmDecoctionAssembler {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按付数刷新同处方号下代煎费（先 void 旧项，再签发 ServiceRequest 并升 PLANNED 费用）。
     *
     * <p>BR-TCM-RX-009：方头代煎=是时，数量取中药付数；费用锚定 {@code wkf_service_request}，与 IF-DOR-15 诊疗签发口径一致。</p>
     *
     * @param encounterId               就诊主键
     * @param patientId                 患者主键
     * @param accountId                 自费账户主键
     * @param orgId                     机构主键
     * @param operatorId                操作人（开嘱/签发医师）
     * @param prescriptionNo            中药处方号
     * @param doseQuantity              付数
     * @param anchorRow                 方内代表药行（诊断、长临、执行科室透传）
     * @param anchorMedicationRequestId 方内代表药行主键（{@code based_on} 锚点）
     * @throws BusinessException 租户未配置代煎诊疗项目或未维护零售价时
     */
    public void replacePlannedDecoctionCharge(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        String prescriptionNo,
        BigDecimal doseQuantity,
        MedicationRequestListQueryModel anchorRow,
        Long anchorMedicationRequestId
    ) {
        // Pre：同方重复签发前先清理旧代煎 SR 与费用，避免双挂
        voidDecoctionChargeByPrescriptionNo(encounterId, prescriptionNo, operatorId);
        // Pre：解析租户代煎诊疗项目与零售价
        Long activityId = requireDecoctionActivityId();
        ProductDefinitionDisplayDto activityProductRow = loadDecoctionActivityProductRow(activityId);
        ChargeItemDefinition saleLine = requireDecoctionSaleLine(activityId);
        BigDecimal quantity = doseQuantity;
        String therapyEnum = resolveTherapyEnum(anchorRow);
        String unitCode = ServiceAdviceDraftCommandHelper.requireUnitCode(null, null, activityProductRow);
        // Cmd：落库代煎 ServiceRequest 草稿并立即签发至 ACTIVE
        CreateServiceRequestDraftCommand createCmd = buildDecoctionServiceRequestDraftCommand(
            encounterId, patientId, operatorId, orgId, prescriptionNo, activityId,
            anchorRow, anchorMedicationRequestId, quantity, unitCode, therapyEnum, activityProductRow);
        Long serviceRequestId = serviceRequestDomainService.createServiceRequestDraft(createCmd);
        serviceRequestDomainService.signServiceRequest(buildSignServiceRequestCommand(serviceRequestId, operatorId));
        // Cmd：按 SR 锚点挂费用草稿并升为 PLANNED，供门诊收银预结算
        CreateChargeItemCommand.Item draftItem = OrderChargeDraftAssembler.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId,
            serviceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId,
            saleLine, quantity, unitCode, activityProductRow);
        draftItem.setPrescriptionNo(trimToNull(prescriptionNo));
        CreateChargeItemCommand chargeCmd = new CreateChargeItemCommand();
        chargeCmd.setOperatorId(operatorId);
        chargeCmd.setItem(draftItem);
        chargeItemDomainService.createDraftChargeItemFromOrder(chargeCmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
            encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceRequestId);
    }

    /**
     * 移除同处方号下未计费代煎费及对应 ServiceRequest。
     *
     * <p>签退/取消代煎时对称清理；已签发 SR 先回退 PLANNED 费用再签退并软删草稿。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号
     * @param operatorId     操作人；为 {@code null} 时跳过 SR 写侧清理（仅兼容历史费用锚点）
     */
    public void voidDecoctionChargeByPrescriptionNo(Long encounterId, String prescriptionNo, Long operatorId) {
        // Gate：就诊或处方号缺失时不做 void，避免误删他方费用
        if (encounterId == null || StringUtils.isEmpty(prescriptionNo)) {
            return;
        }
        Long activityId;
        try {
            activityId = requireDecoctionActivityId();
        } catch (RuntimeException ex) {
            // 租户未配置代煎项目时静默跳过，与签发前 Gate 分工
            return;
        }
        // Pre：清理历史错误锚点（service 指向 ActivityDefinition 的旧数据）
        voidLegacyActivityAnchoredDecoctionCharges(encounterId, prescriptionNo, activityId);
        // Cmd：逐条清理同方代煎 SR 及其费用
        for (ServiceRequestListModel row : findDecoctionServiceRequests(encounterId, prescriptionNo, activityId)) {
            if (row == null || row.getId() == null || operatorId == null) {
                // 无操作人时不改写 SR，防止审计链断裂
                continue;
            }
            String status = row.getStatusEnum();
            if (RequestStatus.DRAFT.getCode().equals(status)) {
                // 草稿态：直接作废锚定 SR 的未计费费用
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                        encounterId, TableNameConstants.WKF_SERVICE_REQUEST, row.getId(), operatorId));
            } else {
                // 已签发：先回退 PLANNED 费用，再签退 SR 以便软删
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, row.getId());
                if (RequestStatus.ACTIVE.getCode().equals(status)) {
                    serviceRequestDomainService.withdrawSignedServiceRequest(
                        ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(row.getId(), operatorId));
                }
            }
            serviceRequestDomainService.removeServiceRequestDraft(
                ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(row.getId(), operatorId));
        }
    }

    /**
     * 从方内代表药行解析长临属性；缺省时回落临嘱，与门诊诊疗 companion 口径一致。
     *
     * @param anchorRow 方内代表药行，可空
     * @return 写入代煎 SR 的 {@code therapy_enum} code
     */
    private String resolveTherapyEnum(MedicationRequestListQueryModel anchorRow) {
        String therapyEnum = TherapyType.TEMPORARY.getCode();
        if (anchorRow == null) {
            return therapyEnum;
        }
        String resolved = StringUtils.firstNonBlank(anchorRow.getTherapyEnum(), TherapyType.TEMPORARY.getCode());
        if (resolved != null) {
            therapyEnum = resolved;
        }
        return therapyEnum;
    }

    /**
     * 装配代煎 companion {@code ServiceRequest} 草稿命令。
     *
     * @param encounterId               就诊主键
     * @param patientId                 患者主键
     * @param operatorId                操作人
     * @param orgId                     机构主键
     * @param prescriptionNo            处方号
     * @param activityId                代煎诊疗项目主键
     * @param anchorRow                 方内代表药行
     * @param anchorMedicationRequestId 代表药行主键
     * @param quantity                  付数
     * @param unitCode                  单位 code
     * @param therapyEnum               长临 code
     * @param activityProductRow        诊疗产品读模型
     * @return 领域创建命令
     */
    private CreateServiceRequestDraftCommand buildDecoctionServiceRequestDraftCommand(
        Long encounterId,
        Long patientId,
        Long operatorId,
        Long orgId,
        String prescriptionNo,
        Long activityId,
        MedicationRequestListQueryModel anchorRow,
        Long anchorMedicationRequestId,
        BigDecimal quantity,
        String unitCode,
        String therapyEnum,
        ProductDefinitionDisplayDto activityProductRow
    ) {
        CreateServiceRequestDraftCommand cmd = new CreateServiceRequestDraftCommand();
        cmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setActivityId(activityId);
        item.setQuantity(quantity);
        item.setUnitCode(unitCode);
        item.setExecutionCount(ServiceAdviceDraftCommandHelper.requireExecutionCount(1, null, therapyEnum));
        item.setCategoryEnum(StringUtils.firstNonBlankOrEmpty(activityProductRow.getCategoryCode(), "1"));
        item.setTherapyEnum(therapyEnum);
        item.setOrgId(orgId);
        item.setRequesterId(operatorId);
        item.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        item.setPrescriptionNo(trimToNull(prescriptionNo));
        if (anchorRow != null) {
            // 诊断与执行科室与代表药行对齐，便于护士站/收银展示
            item.setConditionDefinitionId(anchorRow.getConditionDefinitionId());
            item.setEncounterDiagnosisId(anchorRow.getEncounterDiagnosisId());
            if (anchorRow.getOrgId() != null) {
                item.setOrgId(anchorRow.getOrgId());
            }
        }
        if (anchorMedicationRequestId != null) {
            // 方级 companion 锚定代表药行，便于追溯代煎来源
            item.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
            item.setBasedOnId(anchorMedicationRequestId);
        }
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配代煎 ServiceRequest 签发命令。
     *
     * @param serviceRequestId 代煎 SR 主键
     * @param operatorId       签发人
     * @return 领域签发命令
     */
    private SignServiceRequestCommand buildSignServiceRequestCommand(Long serviceRequestId, Long operatorId) {
        SignServiceRequestCommand cmd = new SignServiceRequestCommand();
        cmd.setOperatorId(operatorId);
        SignServiceRequestCommand.SignServiceRequestItemCommand item =
            new SignServiceRequestCommand.SignServiceRequestItemCommand();
        item.setId(serviceRequestId);
        item.setSignerId(operatorId);
        item.setSignTime(LocalDateTime.now());
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 按就诊、处方号与代煎 activityId 筛选 companion ServiceRequest 行。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 处方号
     * @param activityId     代煎诊疗项目主键
     * @return 命中行列表；无匹配时为空列表
     */
    private List<ServiceRequestListModel> findDecoctionServiceRequests(
        Long encounterId,
        String prescriptionNo,
        Long activityId
    ) {
        List<ServiceRequestListModel> rows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        List<ServiceRequestListModel> matched = new ArrayList<>();
        String trimmedPrescriptionNo = prescriptionNo.trim();
        for (ServiceRequestListModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!activityId.equals(row.getActivityId())) {
                // 非代煎 activity 的 SR 不参与本处方 void
                continue;
            }
            if (!trimmedPrescriptionNo.equals(trimToEmpty(row.getPrescriptionNo()))) {
                // 处方号不一致时跳过，避免误删他方同 activity 的 SR
                continue;
            }
            matched.add(row);
        }
        return matched;
    }

    /**
     * 兼容历史错误锚点：清理 {@code service_table=wkf_activity_definition} 的代煎费草稿。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo   处方号
     * @param activityId       代煎诊疗项目主键
     */
    private void voidLegacyActivityAnchoredDecoctionCharges(
        Long encounterId,
        String prescriptionNo,
        Long activityId
    ) {
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterId(encounterId);
        List<ChargeItem> items = chargeItemQueryService.listChargeItemsByEncounterId(query);
        List<Long> activityIds = new ArrayList<>();
        for (ChargeItem item : items) {
            if (item == null) {
                continue;
            }
            if (!prescriptionNo.equals(item.getPrescriptionNo())) {
                continue;
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(item.getProductTable())
                && activityId.equals(item.getProductId())) {
                activityIds.add(activityId);
            }
        }
        if (!activityIds.isEmpty()) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityIds);
        }
    }

    /**
     * 解析租户代煎诊疗项目主键（名称精确匹配 {@link DoctorStationTcmConstants#DECOCTION_ACTIVITY_NAME}）。
     *
     * @return 代煎 activity 主键
     * @throws BusinessException 未配置或名称不唯一时
     */
    private Long requireDecoctionActivityId() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                "代煎诊疗项目未配置，请联系管理员维护");
        }
    }

    /**
     * 加载代煎诊疗产品读模型，供 SR 与挂费单位/类目解析。
     *
     * @param activityId 代煎 activity 主键
     * @return 产品读模型
     * @throws BusinessException 读模型未命中时
     */
    private ProductDefinitionDisplayDto loadDecoctionActivityProductRow(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(
                ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                "未找到代煎诊疗活动定义，请先在基础数据维护「代煎中药」项目。");
        }
        return row;
    }

    /**
     * 解析代煎诊疗项目零售价（SALE）定价行。
     *
     * @param activityId 代煎 activity 主键
     * @return 零售价定价行
     * @throws BusinessException 未配置零售价时
     */
    private ChargeItemDefinition requireDecoctionSaleLine(Long activityId) {
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceQuery.setInstanceIds(List.of(activityId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        for (ChargeItemDefinition line : priceLines) {
            if (line != null && PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                return line;
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
            "代煎诊疗项目未配置零售价，请联系管理员维护");
    }
}
