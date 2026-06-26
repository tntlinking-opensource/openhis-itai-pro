/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BR-IP-TCM-008：住院护士执行中药方时按付数生成/刷新代煎 {@code ORDER_EXECUTE} 待结算费用。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmDecoctionExecuteSupport {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;

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
        BigDecimal doseQuantity
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
        Long activityId = this.requireDecoctionActivityId();
        // Pre：同方同 planTime 先 void 未结算旧代煎费，再按当次付数新建
        this.voidOrderExecuteDecoctionChargeAtPlanTime(encounterId, trimToNull(prescriptionNo), planTime, activityId);
        ChargeItemDefinition saleLine = this.requireDecoctionSaleLine(activityId);
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(patientId);
        item.setEncounterId(encounterId);
        item.setAccountId(accountId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        item.setDefinitionId(saleLine.getId());
        item.setServiceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setServiceId(activityId);
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
        item.setPrescriptionNo(trimToNull(prescriptionNo));
        item.setGenerateSourceEnum(GenerateSource.ORDER_EXECUTE.getCode());
        command.setItems(List.of(item));
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(command);
    }

    /**
     * 清理同就诊同方同计划点下未结算代煎费（按处方号 + occurrence 精确匹配，刷新前调用）。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 处方号（可空时仅按 occurrence+产品匹配）
     * @param planTime       计划执行时刻
     * @param activityId     代煎诊疗项目主键
     */
    private void voidOrderExecuteDecoctionChargeAtPlanTime(
        Long encounterId,
        String prescriptionNo,
        LocalDateTime planTime,
        Long activityId) {
        if (encounterId == null || planTime == null || activityId == null) {
            return;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setEncounterId(encounterId);
        List<ChargeItem> rows = chargeItemQueryService.listChargeItemsByEncounterId(query);
        List<Long> toVoidIds = new ArrayList<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 仅删待结算执行费，已结算行留给 Gate 拒绝
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getGenerateSourceEnum() == null
                || !GenerateSource.ORDER_EXECUTE.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTable())) {
                continue;
            }
            if (!activityId.equals(row.getProductId())) {
                continue;
            }
            if (!planTime.equals(row.getOccurrenceTime())) {
                continue;
            }
            // 有处方号时精确到方级，避免误删他方同刻代煎费
            if (prescriptionNo != null && !prescriptionNo.equals(trimToNull(row.getPrescriptionNo()))) {
                continue;
            }
            toVoidIds.add(row.getId());
        }
        chargeItemDomainService.voidPendingOrderExecuteChargeItemsByChargeItemIds(toVoidIds);
    }

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
