/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler;

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
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-37：代煎诊疗费生成/刷新（按付数 PLANNED）。
 */
@Component
@RequiredArgsConstructor
public class TcmDecoctionAssembler {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 按付数刷新同处方号下代煎费（先 void 旧项再按付数 PLANNED）。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param accountId     自费账户
     * @param orgId         机构主键
     * @param operatorId    操作人
     * @param prescriptionNo 处方号
     * @param doseQuantity  付数
     */
    public void replacePlannedDecoctionCharge(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        String prescriptionNo,
        BigDecimal doseQuantity
    ) {
        // Pre：先清理同处方号旧代煎费，避免重复挂费
        voidDecoctionChargeByPrescriptionNo(encounterId, prescriptionNo);
        Long activityId;
        try {
            activityId = activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            // Gate：租户未配置代煎诊疗项目则拒绝签发后续代煎费
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "代煎诊疗项目未配置，请联系管理员维护");
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceQuery.setInstanceIds(List.of(activityId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        ChargeItemDefinition saleLine = null;
        for (ChargeItemDefinition line : priceLines) {
            if (line != null && PriceType.SALE.getCode().equals(line.getPriceTypeCode())) {
                saleLine = line;
                break;
            }
        }
        if (saleLine == null) {
            // Gate：代煎项目须配置零售价
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "代煎诊疗项目未配置零售价，请联系管理员维护");
        }
        CreateChargeItemCommand cmd = new CreateChargeItemCommand();
        cmd.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(patientId);
        item.setEncounterId(encounterId);
        item.setAccountId(accountId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        item.setDefinitionId(saleLine.getId());
        item.setQuantityValue(doseQuantity);
        item.setUnitPrice(saleLine.getPriceValue());
        if (saleLine.getPriceValue() != null && doseQuantity != null) {
            item.setTotalPrice(saleLine.getPriceValue().multiply(doseQuantity));
        }
        item.setServiceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setServiceId(activityId);
        item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        item.setProductId(activityId);
        cmd.setItem(item);
        chargeItemDomainService.createDraftChargeItemFromOrder(cmd);
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, List.of(activityId));
    }

    /**
     * 移除同处方号下未计费代煎费（按活动定义产品锚点软删草稿）。
     *
     * @param encounterId    就诊主键
     * @param prescriptionNo 处方号
     */
    public void voidDecoctionChargeByPrescriptionNo(Long encounterId, String prescriptionNo) {
        if (encounterId == null || StringUtils.isBlank(prescriptionNo)) {
            return;
        }
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
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(item.getProductTable()) && item.getProductId() != null) {
                activityIds.add(item.getProductId());
            }
        }
        if (!activityIds.isEmpty()) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                encounterId, TableNameConstants.WKF_ACTIVITY_DEFINITION, activityIds);
        }
    }
}
