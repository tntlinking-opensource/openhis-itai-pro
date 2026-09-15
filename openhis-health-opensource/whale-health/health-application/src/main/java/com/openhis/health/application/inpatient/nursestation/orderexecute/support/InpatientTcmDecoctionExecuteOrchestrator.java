/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.shared.advice.ServiceAdviceCommandAssembler;
import com.openhis.health.application.shared.advice.ServiceAdviceDraftCommandHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BR-IP-TCM-008：住院护士执行中药方时按付数生成/刷新代煎 {@code ORDER_EXECUTE} 待结算费用。
 *
 * <p>与门诊同构：代煎 companion 落 {@code wkf_service_request} 后锚定收费；关联用 {@code based_on}→药嘱，
 * <b>不</b>写 SR/费用 {@code prescription_no}（该列在 SR 仅申请单 AR）。{@code occurrence} 落计划槽位 {@code planTime}。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmDecoctionExecuteOrchestrator {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 若方头代煎=是，按当次付数刷新同方同计划点代煎费（先 void 未结算旧项再新建）。
     *
     * @param encounterId    就诊主键
     * @param patientId      患者主键
     * @param accountId      账户主键
     * @param orgId          机构主键
     * @param operatorId     操作人
     * @param procedureId    代表味药行 Procedure 主键（锚定本次执行）
     * @param planTime       计划执行时刻
     * @param prescriptionNo 处方号
     * @param sufferingFlag  代煎标志
     * @param doseQuantity   付数
     * @param anchorMed      方内代表药行（诊断/长临/合同/锚点透传）
     */
    public void replaceOrderExecuteDecoctionChargeIfNeeded(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long procedureId,
        LocalDateTime planTime,
        String prescriptionNo,
        Boolean sufferingFlag,
        BigDecimal doseQuantity,
        MedicationRequestListQueryModel anchorMed
    ) {
        if (!Boolean.TRUE.equals(sufferingFlag)) {
            // 方头未勾选代煎则本计划点不落代煎费
            return;
        }
        if (encounterId == null || patientId == null || accountId == null || procedureId == null || planTime == null) {
            // 锚点不完整时跳过，避免脏数据挂账
            return;
        }
        BigDecimal doses = doseQuantity;
        if (doses == null || doses.compareTo(BigDecimal.ZERO) <= 0) {
            // 付数无效则不生成代煎费
            return;
        }
        // Gate：方级 companion 必须 based_on 锚定代表药行
        if (anchorMed == null || anchorMed.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "住院执行代煎费时代表药行主键不能为空");
        }
        Long activityId = this.requireDecoctionActivityId();
        ProductDefinitionDisplayDto activityProductRow = this.loadDecoctionActivityProductRow(activityId);
        // Pre：同方同 planTime 先 void 未结算旧代煎费及 companion SR，再按当次付数新建
        this.voidOrderExecuteDecoctionChargeAtPlanTime(
            encounterId, trimToNull(prescriptionNo), planTime, activityId, operatorId);
        ChargeItemDefinition saleLine = this.requireDecoctionSaleLine(activityId);
        String therapyEnum = this.resolveTherapyEnum(anchorMed);
        String unitCode = ServiceAdviceDraftCommandHelper.requireUnitCode(null, null, activityProductRow);
        Long serviceRequestId = serviceRequestDomainService.createServiceRequestDraft(
            this.buildDecoctionServiceRequestDraftCommand(
                encounterId, patientId, operatorId, orgId,
                activityId, anchorMed, doses, unitCode, therapyEnum, activityProductRow));
        serviceRequestDomainService.signServiceRequest(
            this.buildSignServiceRequestCommand(serviceRequestId, operatorId));
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(patientId);
        item.setEncounterId(encounterId);
        item.setAccountId(accountId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        item.setDefinitionId(saleLine.getId());
        item.setServiceTable(TableNameConstants.WKF_SERVICE_REQUEST);
        item.setServiceId(serviceRequestId);
        item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setProductId(activityId);
        item.setQuantityValue(doses);
        item.setUnitPrice(saleLine.getPriceValue());
        if (saleLine.getPriceValue() != null) {
            item.setTotalPrice(saleLine.getPriceValue().multiply(doses));
        }
        item.setBaseAmount(item.getTotalPrice());
        item.setDiscountAmount(item.getTotalPrice());
        item.setSurchargeAmount(BigDecimal.ZERO);
        item.setOccurrenceTime(planTime);
        item.setProcedureId(procedureId);
        // 代煎费用不写处方号；方关联在 SR.based_on
        item.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        command.setItems(List.of(item));
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(command);
    }

    /**
     * 清理同就诊同方同计划点下未结算代煎费及 companion SR（刷新前调用）。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 处方号（可空时仅按 occurrence+产品匹配）
     * @param planTime       计划执行时刻
     * @param activityId     代煎诊疗项目主键
     * @param operatorId     操作人；为 null 时仅 void 费用、不清理 SR
     */
    private void voidOrderExecuteDecoctionChargeAtPlanTime(
        Long encounterId,
        String prescriptionNo,
        LocalDateTime planTime,
        Long activityId,
        Long operatorId) {
        if (encounterId == null || planTime == null || activityId == null) {
            return;
        }
        Set<Long> medicationRequestIds = new LinkedHashSet<>();
        if (prescriptionNo != null) {
            medicationRequestIds = this.collectMedicationRequestIdsForPrescription(encounterId, prescriptionNo);
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterId(encounterId);
        query.setExcludeSupersededSettledLines(true);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(query);
        List<Long> toVoidIds = new ArrayList<>();
        Set<Long> serviceRequestIdsToClean = new LinkedHashSet<>();
        Set<Long> serviceRequestIdsToResolve = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!this.isDecoctionOrderExecuteChargeRow(row, activityId)) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (!planTime.equals(row.getOccurrenceTime())) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())
                && row.getServiceId() != null) {
                serviceRequestIdsToResolve.add(row.getServiceId());
            }
        }
        Map<Long, ServiceRequestListModel> serviceRequestById = Map.of();
        if (!serviceRequestIdsToResolve.isEmpty()) {
            serviceRequestById = this.serviceRequestQueryService.mapListModelsByIds(serviceRequestIdsToResolve);
        }
        for (ChargeItem row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!this.isDecoctionOrderExecuteChargeRow(row, activityId)) {
                continue;
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (!planTime.equals(row.getOccurrenceTime())) {
                continue;
            }
            // Gate：有方号时仅按 based_on 精确到方，避免误删他方同刻代煎费
            if (prescriptionNo != null
                && !this.matchesPrescriptionForOrderExecuteVoid(
                    row, medicationRequestIds, serviceRequestById)) {
                continue;
            }
            toVoidIds.add(row.getId());
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getServiceTable())
                && row.getServiceId() != null) {
                serviceRequestIdsToClean.add(row.getServiceId());
            }
        }
        chargeItemDomainService.voidPendingOrderExecuteChargeItemsByChargeItemIds(toVoidIds);
        if (operatorId == null || serviceRequestIdsToClean.isEmpty()) {
            return;
        }
        for (Long serviceRequestId : serviceRequestIdsToClean) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                ServiceAdviceCommandAssembler.toVoidDraftChargeAnchor(
                    encounterId, TableNameConstants.WKF_SERVICE_REQUEST, serviceRequestId, operatorId));
            serviceRequestDomainService.withdrawSignedServiceRequest(
                ServiceAdviceCommandAssembler.toWithdrawServiceRequestCommand(serviceRequestId, operatorId));
            serviceRequestDomainService.removeServiceRequestDraft(
                ServiceAdviceCommandAssembler.toRemoveServiceRequestDraftCommand(serviceRequestId, operatorId));
        }
    }

    /**
     * 住院执行代煎 void 方级匹配：仅 SR.based_on→同方药嘱。
     *
     * @param row                  收费行
     * @param medicationRequestIds 同方药嘱 id
     * @param serviceRequestById   SR 字典
     * @return 属于该方时 true
     */
    private boolean matchesPrescriptionForOrderExecuteVoid(
        ChargeItem row,
        Set<Long> medicationRequestIds,
        Map<Long, ServiceRequestListModel> serviceRequestById
    ) {
        if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(trimToEmpty(row.getServiceTable()))
            || row.getServiceId() == null) {
            return false;
        }
        ServiceRequestListModel serviceRequest = serviceRequestById.get(row.getServiceId());
        if (serviceRequest == null) {
            return false;
        }
        if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(trimToEmpty(serviceRequest.getBasedOnTable()))) {
            return false;
        }
        return serviceRequest.getBasedOnId() != null
            && medicationRequestIds.contains(serviceRequest.getBasedOnId());
    }

    /**
     * 解析就诊下同中药处方号可匹配代煎 based_on 的药嘱主键集合。
     *
     * <p>含该方正向药嘱，以及 {@code refund_medicine_id} 指向这些正向单的负向退药嘱（退代煎绑退药嘱时仍可按原方 CP 命中）。</p>
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 中药处方号
     * @return 药嘱主键集合
     */
    private Set<Long> collectMedicationRequestIdsForPrescription(Long encounterId, String prescriptionNo) {
        Set<Long> ids = new LinkedHashSet<>();
        List<MedicationRequestListQueryModel> rows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(
                List.of(prescriptionNo));
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!encounterId.equals(row.getEncounterId())) {
                continue;
            }
            ids.add(row.getId());
        }
        if (ids.isEmpty()) {
            return ids;
        }
        // Pre：退代煎 may based_on 负向退药嘱，一并纳入匹配集
        List<MedicationRequest> refundRows =
            this.medicationRequestQueryService.listByRefundMedicineIdIn(ids);
        for (MedicationRequest refundRow : refundRows) {
            if (refundRow == null || refundRow.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(refundRow.getIsDeleted())) {
                continue;
            }
            if (!encounterId.equals(refundRow.getEncounterId())) {
                continue;
            }
            ids.add(refundRow.getId());
        }
        return ids;
    }

    /**
     * 判定是否为住院执行代煎费行（须锚定 {@code wkf_service_request}）。
     *
     * @param row        收费明细
     * @param activityId 代煎 activity 主键
     * @return true 表示代煎 ORDER_EXECUTE 候选行
     */
    private boolean isDecoctionOrderExecuteChargeRow(ChargeItem row, Long activityId) {
        if (row.getGenerateSourceEnum() == null
            || !GenerateSource.ORDER_EXECUTE.getCode().equals(row.getGenerateSourceEnum())) {
            return false;
        }
        if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTable())) {
            return false;
        }
        if (!activityId.equals(row.getProductId())) {
            return false;
        }
        return TableNameConstants.WKF_SERVICE_REQUEST.equals(StringUtils.trimToEmpty(row.getServiceTable()));
    }

    /**
     * 装配代煎 companion {@code ServiceRequest} 草稿命令（住院执行路径）。
     *
     * <p>{@code prescription_no} 留空；关联仅 based_on → 代表药行。</p>
     *
     * @param encounterId        就诊主键
     * @param patientId          患者主键
     * @param operatorId         操作人
     * @param orgId              机构主键
     * @param activityId         代煎 activity 主键
     * @param anchorMed          方内代表药行
     * @param quantity           付数
     * @param unitCode           单位
     * @param therapyEnum        长临
     * @param activityProductRow 诊疗产品读模型
     * @return 创建草稿命令
     */
    private CreateServiceRequestDraftCommand buildDecoctionServiceRequestDraftCommand(
        Long encounterId,
        Long patientId,
        Long operatorId,
        Long orgId,
        Long activityId,
        MedicationRequestListQueryModel anchorMed,
        BigDecimal quantity,
        String unitCode,
        String therapyEnum,
        ProductDefinitionDisplayDto activityProductRow) {
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
        item.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        if (anchorMed != null) {
            item.setEncounterDiagnosisIds(anchorMed.getEncounterDiagnosisIds());
            if (anchorMed.getContractId() != null) {
                item.setContractId(anchorMed.getContractId());
            }
            if (anchorMed.getOrgId() != null) {
                item.setOrgId(anchorMed.getOrgId());
            }
            // 方级 companion 唯一关联：based_on → 代表药行
            item.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
            item.setBasedOnId(anchorMed.getId());
        }
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 装配代煎 ServiceRequest 签发命令。
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
     * 从方内代表药行解析长临属性；缺省时回落临嘱。
     */
    private String resolveTherapyEnum(MedicationRequestListQueryModel anchorMed) {
        if (anchorMed == null) {
            return TherapyType.TEMPORARY.getCode();
        }
        String resolved = StringUtils.firstNonBlank(anchorMed.getTherapyEnum(), TherapyType.TEMPORARY.getCode());
        if (resolved != null) {
            return resolved;
        }
        return TherapyType.TEMPORARY.getCode();
    }

    /**
     * 加载代煎诊疗产品读模型。
     */
    private ProductDefinitionDisplayDto loadDecoctionActivityProductRow(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> map =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
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
     * 加载代煎诊疗活动定义 id；未配置时抛 {@link BusinessException}。
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
     * 代煎活动须存在 SALE 定价行；否则抛 {@link BusinessException}。
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
