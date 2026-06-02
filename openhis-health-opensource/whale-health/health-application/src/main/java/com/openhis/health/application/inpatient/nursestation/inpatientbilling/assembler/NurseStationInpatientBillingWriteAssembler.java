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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-PRC-02/04 写路径专项装配：吸氧/心电时长 Gate（BR-IP-PRC-010）与套餐子项展开（BR-IP-PRC-011）。
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingWriteAssembler {

    private final ChargeItemDomainService chargeItemDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final NurseStationInpatientBillingCatalogAssembler nurseStationInpatientBillingCatalogAssembler;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;

    /**
     * 吸氧/心电计时类：当日上限 + 在院余量 Gate（BR-IP-PRC-010 / BR-SRQ-018）。
     *
     * @param row 待写入诊疗行
     * @throws BusinessException 超限时 EX-IP-PRC-203
     */
    public void assertOxygenOrEcgDuration(NurseStationPricingWriteItemDto row) {
        if (row == null || row.getEncounterId() == null || row.getActivityId() == null) {
            return;
        }
        // 优先用选品带回的 Gate 类型，否则按活动 id 查产品名解析
        String gateKind = row.getDurationGateKind();
        if (StringUtils.isBlank(gateKind)) {
            gateKind = this.resolveDurationGateKindByActivityId(row.getActivityId());
        }
        if (StringUtils.isBlank(gateKind)) {
            return;
        }
        BigDecimal add = row.getQuantity();
        if (add == null) {
            add = BigDecimal.ZERO;
        }
        // 当日累计：按就诊 + 活动 + 自然日汇总 wkf_service_request.quantity（小时）
        long accumulatedToday = this.serviceRequestQueryService.sumQuantityHoursByEncounterAndActivity(
            row.getEncounterId(),
            row.getActivityId(),
            LocalDate.now());

        // 在院累计：同口径不加日期，用于 BR-SRQ-018 在院余量
        long accumulatedStay = this.serviceRequestQueryService.sumQuantityHoursByEncounterAndActivity(
            row.getEncounterId(),
            row.getActivityId(),
            null);

        int dailyMax = InpatientNurseStationConstants.OXYGEN_DAILY_DURATION_MAX_HOURS;
        if (InpatientNurseStationConstants.DURATION_GATE_KIND_ECG.equals(gateKind)) {
            dailyMax = InpatientNurseStationConstants.ECG_DAILY_DURATION_MAX_HOURS;
        }
        BigDecimal todayTotal = BigDecimal.valueOf(accumulatedToday).add(add);
        if (todayTotal.compareTo(BigDecimal.valueOf(dailyMax)) > 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_203,
                "吸氧/心电监测时长超出当日上限，请核对数量");
        }

        // 在院上限：入院至当前小时数（至少 1h），累计+本次不得超过
        long inpatientHours = this.resolveInpatientHoursCap(row.getEncounterId());
        BigDecimal stayTotal = BigDecimal.valueOf(accumulatedStay).add(add);
        if (stayTotal.compareTo(BigDecimal.valueOf(inpatientHours)) > 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_203,
                "吸氧/心电监测时长超出在院可用余量，请核对数量");
        }
    }

    /**
     * 套餐父诊疗确认后展开子项请求与费用（BR-IP-PRC-011）。
     *
     * @param parentRow           父诊疗写行
     * @param operatorId          操作人
     * @param requesterId         申请人
     * @param orgId               机构
     * @param createdServiceIds   汇总新建诊疗 id
     */
    public void expandPackageChildrenIfPresent(
        NurseStationPricingWriteItemDto parentRow,
        Long operatorId,
        Long requesterId,
        Long orgId,
        List<Long> createdServiceIds) {
        if (parentRow == null || parentRow.getActivityId() == null) {
            return;
        }
        // 无子表配置则视为非套餐，由父项单独落库
        List<ActivityDefinitionChildQueryModel> tableChildren =
            activityDefinitionQueryService.listChildrenByActivityId(parentRow.getActivityId());
        if (tableChildren.isEmpty()) {
            return;
        }
        BigDecimal parentQty = parentRow.getQuantity();
        if (parentQty == null) {
            parentQty = BigDecimal.ONE;
        }
        String pricingSource = DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE;
        for (ActivityDefinitionChildQueryModel child : tableChildren) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            // 子项数量 = 父数量 × childrenNum（默认 1）
            BigDecimal multiplier = BigDecimal.ONE;
            if (child.getChildrenNum() != null) {
                multiplier = BigDecimal.valueOf(child.getChildrenNum());
            }
            NurseStationPricingWriteItemDto childRow = new NurseStationPricingWriteItemDto();
            childRow.setEncounterId(parentRow.getEncounterId());
            childRow.setPatientId(parentRow.getPatientId());
            childRow.setAccountId(parentRow.getAccountId());
            childRow.setActivityId(child.getChildrenId());
            childRow.setQuantity(parentQty.multiply(multiplier));
            childRow.setUnitCode(parentRow.getUnitCode());
            childRow.setParentRequestAnchor(parentRow.getParentRequestAnchor());
            nurseStationInpatientBillingCatalogAssembler.applyChildActivityPricing(
                childRow, child.getChildrenId(), pricingSource);
            this.assertWriteRowPricingComplete(childRow);
            CreateFromNursePricingCommand childCmd = nurseStationInpatientBillingMapper.toCreateFromNursePricingCommand(
                List.of(childRow), operatorId, requesterId);
            List<Long> childIds = serviceRequestDomainService.createFromNursePricing(childCmd);
            if (childIds.isEmpty()) {
                continue;
            }
            createdServiceIds.add(childIds.get(0));
            CreateChargeItemCommand chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                childRow,
                TableNameConstants.WKF_SERVICE_REQUEST,
                childIds.get(0),
                null,
                null,
                operatorId,
                orgId);
            chargeItemDomainService.priceByNurseStation(chargeCmd);
        }
    }

    /**
     * 按活动定义 id 解析吸氧/心电 Gate 类型（与选品目录口径一致）。
     *
     * @param activityId 诊疗项目 id
     * @return OXYGEN / ECG 或 null
     */
    public String resolveDurationGateKindByActivityId(Long activityId) {
        if (activityId == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> products =
            productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        ProductDefinitionDisplayDto product = products.get(key);
        if (product == null || StringUtils.isBlank(product.getProductName())) {
            return null;
        }
        return nurseStationInpatientBillingCatalogAssembler.resolveDurationGateKind(product.getProductName());
    }

    /**
     * 在院可用小时上限：入院时间至当前（至少 1 小时）。
     *
     * @param encounterId 就诊 id
     * @return 可用于吸氧/心电 Gate 的小时数
     */
    private long resolveInpatientHoursCap(Long encounterId) {
        var context = encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
        if (context == null || context.getStartTime() == null) {
            return InpatientNurseStationConstants.ECG_DAILY_DURATION_MAX_HOURS;
        }
        long hours = Duration.between(context.getStartTime(), LocalDateTime.now()).toHours();
        if (hours < 1L) {
            hours = 1L;
        }
        return hours;
    }

    /**
     * 套餐子项展开前校验价目与数量完整（与 AppService#validateWriteRow 同口径）。
     *
     * @param row 子项写行
     */
    private void assertWriteRowPricingComplete(NurseStationPricingWriteItemDto row) {
        if (row.getAccountId() == null || row.getChargeItemDefinitionId() == null
            || row.getQuantity() == null || row.getUnitPrice() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
    }
}
