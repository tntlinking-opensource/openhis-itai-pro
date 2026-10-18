/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 进销存上报单价解析：日常 3502 跟批号售价；无批号价时回落单据行价。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryUploadLotPriceSupport {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 解析 3502 操作单位单价：优先批号售价（包装锚）→ 操作单位；无批号价则回落行价刻度。
     *
     * @param medication        药品
     * @param lotNumber         批号
     * @param operationUnitCode 操作单位
     * @param fallbackLinePrice 单据行价（已与操作单位同口径时可直接回落）
     * @return 操作单位单价；均无则 null
     */
    public BigDecimal resolve3502OperationUnitPrice(
        MedicationDefinition medication,
        String lotNumber,
        String operationUnitCode,
        BigDecimal fallbackLinePrice
    ) {
        if (medication == null || medication.getId() == null) {
            return MonetaryAmountSupport.scaleCalc(fallbackLinePrice);
        }
        // 日常 3502：批号售价（包装单位）→ 操作单位
        if (isNotEmpty(lotNumber)) {
            BigDecimal lotPackagePrice = chargeItemDefinitionQueryService.resolveFirstLotNumberPriceValue(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medication.getId(),
                lotNumber);
            if (lotPackagePrice != null) {
                return InventoryUploadQuantityAssembler.convertUnitPricePackageAnchored(
                    medication,
                    medication.getPackageUnitCode(),
                    lotPackagePrice,
                    operationUnitCode);
            }
        }
        // 无批号售价：回落单据行价
        return MonetaryAmountSupport.scaleCalc(fallbackLinePrice);
    }
}
