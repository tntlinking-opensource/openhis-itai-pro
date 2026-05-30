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

package com.openhis.health.application.integration.winning.catalog.orchestration;

import com.openhis.health.application.integration.winning.catalog.WinningCatalogSyncConstants;
import com.openhis.health.application.integration.winning.catalog.dto.WinningActivityCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningDeviceCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningMedicationCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.mapper.WinningCatalogSyncMapper;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 卫宁目录同步后刷新定价行（对齐急诊开立：药耗 {@link PriceType#LOT_NUMBER_PRICE}，诊疗 {@link PriceType#SALE}）。
 */
@Component
@RequiredArgsConstructor
public class WinningCatalogPricingApplier {

    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final WinningCatalogSyncMapper mapper;

    public int applyMedicationPrices(
        List<WinningMedicationCatalogItemDto> items,
        Map<String, Long> instanceIdByBusNo) {
        return applyLotAndSalePrices(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            items,
            instanceIdByBusNo,
            WinningMedicationCatalogItemDto::getBusNo,
            WinningMedicationCatalogItemDto::getExternalItemCode,
            WinningMedicationCatalogItemDto::getLotNumber,
            WinningMedicationCatalogItemDto::getLotSalePrice,
            WinningMedicationCatalogItemDto::getRetailPrice,
            WinningMedicationCatalogItemDto::getChargeTypeCode,
            WinningMedicationCatalogItemDto::getYbType);
    }

    public int applyDevicePrices(
        List<WinningDeviceCatalogItemDto> items,
        Map<String, Long> instanceIdByBusNo) {
        return applyLotAndSalePrices(
            TableNameConstants.ADM_DEVICE_DEFINITION,
            items,
            instanceIdByBusNo,
            WinningDeviceCatalogItemDto::getBusNo,
            WinningDeviceCatalogItemDto::getExternalItemCode,
            WinningDeviceCatalogItemDto::getLotNumber,
            WinningDeviceCatalogItemDto::getLotSalePrice,
            WinningDeviceCatalogItemDto::getRetailPrice,
            WinningDeviceCatalogItemDto::getChargeTypeCode,
            WinningDeviceCatalogItemDto::getYbType);
    }

    public int applyActivityPrices(
        List<WinningActivityCatalogItemDto> items,
        Map<String, Long> instanceIdByBusNo) {
        int applied = 0;
        for (WinningActivityCatalogItemDto item : items) {
            String busNo = mapper.resolveBusNo(item.getBusNo(), item.getExternalItemCode());
            if (busNo == null) {
                continue;
            }
            Long instanceId = instanceIdByBusNo.get(busNo);
            if (instanceId == null || item.getRetailPrice() == null) {
                continue;
            }
            replaceInstancePricing(
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                instanceId,
                item.getChargeTypeCode(),
                item.getYbType(),
                List.of(buildLine(PriceType.SALE.getCode(), null, item.getRetailPrice())));
            applied++;
        }
        return applied;
    }

    private <T> int applyLotAndSalePrices(
        String instanceTable,
        List<T> items,
        Map<String, Long> instanceIdByBusNo,
        java.util.function.Function<T, String> busNoFn,
        java.util.function.Function<T, String> externalFn,
        java.util.function.Function<T, String> lotFn,
        java.util.function.Function<T, BigDecimal> lotPriceFn,
        java.util.function.Function<T, BigDecimal> retailFn,
        java.util.function.Function<T, String> typeCodeFn,
        java.util.function.Function<T, String> ybTypeFn) {
        int applied = 0;
        for (T item : items) {
            String busNo = mapper.resolveBusNo(busNoFn.apply(item), externalFn.apply(item));
            if (busNo == null) {
                continue;
            }
            Long instanceId = instanceIdByBusNo.get(busNo);
            if (instanceId == null) {
                continue;
            }
            BigDecimal lotSale = lotPriceFn.apply(item);
            if (lotSale == null) {
                lotSale = retailFn.apply(item);
            }
            if (lotSale == null) {
                continue;
            }
            String lotNo = StringUtils.firstNonBlank(lotFn.apply(item), WinningCatalogSyncConstants.DEFAULT_LOT_NO_FOR_SYNC);
            List<SaveAllChargeItemDefinitionsCommand.Item> lines = new ArrayList<>();
            lines.add(buildLine(PriceType.LOT_NUMBER_PRICE.getCode(), lotNo, lotSale));
            replaceInstancePricing(instanceTable, instanceId, typeCodeFn.apply(item), ybTypeFn.apply(item), lines);
            applied++;
        }
        return applied;
    }

    private void replaceInstancePricing(
        String instanceTable,
        Long instanceId,
        String typeCode,
        String ybType,
        List<SaveAllChargeItemDefinitionsCommand.Item> lines) {
        if (lines.isEmpty()) {
            return;
        }
        RemovePricingByInstanceCommand remove = new RemovePricingByInstanceCommand();
        remove.setInstanceTable(instanceTable);
        remove.setInstanceId(instanceId);
        chargeItemDefinitionDomainService.removePricingByInstance(remove);

        for (SaveAllChargeItemDefinitionsCommand.Item line : lines) {
            line.setInstanceTable(instanceTable);
            line.setInstanceId(instanceId);
            line.setTypeCode(typeCode);
            line.setYbType(ybType);
        }
        SaveAllChargeItemDefinitionsCommand save = new SaveAllChargeItemDefinitionsCommand();
        save.setItems(lines);
        chargeItemDefinitionDomainService.saveAllChargeItemDefinitions(save);
    }

    private static SaveAllChargeItemDefinitionsCommand.Item buildLine(String priceTypeCode, String lotNo, BigDecimal yuan) {
        SaveAllChargeItemDefinitionsCommand.Item item = new SaveAllChargeItemDefinitionsCommand.Item();
        item.setPriceTypeCode(priceTypeCode);
        item.setLotNo(lotNo);
        item.setPriceValue(yuan);
        return item;
    }
}
