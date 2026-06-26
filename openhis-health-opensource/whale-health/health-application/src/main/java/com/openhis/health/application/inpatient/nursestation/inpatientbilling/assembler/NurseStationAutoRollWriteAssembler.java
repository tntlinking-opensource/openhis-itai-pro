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

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.AutoRollChargeLineResolved;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogUnitPriceCommand;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-ARG-08：住院滚费定价解析与 Charge 行构建支撑。
 */
@Component
@RequiredArgsConstructor
public class NurseStationAutoRollWriteAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;

    /**
     * 按已锚定的定价 id 解析滚费入账行（床位配置分支）。
     *
     * @param chargeDefinitionId 配置上的定价 id
     * @param activityId         床位服务 activityId
     * @param quantity           数量
     * @param organizationId     机构（定价择优上下文）
     * @return 解析结果
     */
    public AutoRollChargeLineResolved resolveAutoRollChargeLine(
        Long chargeDefinitionId,
        Long activityId,
        BigDecimal quantity,
        Long organizationId) {
        AutoRollChargeLineResolved resolved = new AutoRollChargeLineResolved();
        resolved.setResolved(false);
        if (chargeDefinitionId == null || activityId == null) {
            return resolved;
        }
        // Pre：按配置锚定的定价 id 加载定义行
        Map<Long, ChargeItemDefinition> definitionMap =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(Collections.singleton(chargeDefinitionId));
        ChargeItemDefinition definition = definitionMap.get(chargeDefinitionId);
        if (!this.isChargeDefinitionUsable(definition)) {
            return resolved;
        }
        BigDecimal qty = quantity;
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = BigDecimal.ONE;
        }
        BigDecimal unitPrice = definition.getPriceValue();
        // Pre：定价行无显式单价时，走目录择优（同 BR-IP-PRC-017）
        if (unitPrice == null) {
            ClinicalCatalogUnitPriceCommand priceCmd = ClinicalCatalogUnitPriceCommand.builder()
                .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
                .productDefinitionId(activityId)
                .organizationId(organizationId)
                .priceLines(chargeItemDefinitionQueryService.findSaleItemByInstanceId(
                    this.buildInstanceQuery(activityId)))
                .build();
            ChargeItemDefinition picked = clinicalCatalogPriceResolveSupport.resolveCatalogUnitPrice(priceCmd);
            if (picked == null || picked.getPriceValue() == null) {
                return resolved;
            }
            unitPrice = picked.getPriceValue();
            definition = picked;
        }
        resolved.setResolved(true);
        resolved.setChargeDefinitionId(definition.getId());
        resolved.setActivityId(activityId);
        resolved.setUnitCode(ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT);
        resolved.setUnitPrice(unitPrice);
        resolved.setTotalPrice(unitPrice.multiply(qty));
        return resolved;
    }

    /**
     * 护理分支：按 activityId 反查默认定价 id。
     *
     * @param activityId 护理 activityId
     * @return 定价 id；无则 null
     */
    public Long resolveNursingChargeDefinitionId(Long activityId) {
        if (activityId == null) {
            return null;
        }
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findSaleItemByInstanceId(
            this.buildInstanceQuery(activityId));
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        ClinicalCatalogUnitPriceCommand priceCmd = ClinicalCatalogUnitPriceCommand.builder()
            .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .productDefinitionId(activityId)
            .priceLines(lines)
            .build();
        ChargeItemDefinition picked = clinicalCatalogPriceResolveSupport.resolveCatalogUnitPrice(priceCmd);
        if (picked == null || !this.isChargeDefinitionUsable(picked)) {
            return null;
        }
        return picked.getId();
    }

    /**
     * 定价行是否可用于滚费落账。
     *
     * @param definition 定价行
     * @return 可用时 true
     */
    private boolean isChargeDefinitionUsable(ChargeItemDefinition definition) {
        if (definition == null || Boolean.TRUE.equals(definition.getIsDeleted())) {
            return false;
        }
        if (StringUtils.isNotBlank(definition.getStatusEnum())
            && PublicationStatus.DISCARDED.getCode().equals(definition.getStatusEnum())) {
            return false;
        }
        return true;
    }

    /**
     * 构建按 activity 实例查价命令。
     *
     * @param activityId 活动定义 id
     * @return 查询命令
     */
    private ChargeItemDefinitionQueryCommand buildInstanceQuery(Long activityId) {
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        cmd.setInstanceId(activityId);
        return cmd;
    }
}
