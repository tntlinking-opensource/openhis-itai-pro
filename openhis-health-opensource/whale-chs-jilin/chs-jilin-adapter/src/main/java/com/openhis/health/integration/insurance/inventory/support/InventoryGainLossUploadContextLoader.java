/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterGainLossCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterStocktakeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 损益单组装 3502 上报请求（复用盘点盈亏行逻辑）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryGainLossUploadContextLoader {

    private final InventoryStocktakeUploadContextLoader stocktakeUploadContextLoader;

    public UploadInvChange3502Request build3502Request(UploadAfterGainLossCommand command) {
        UploadAfterStocktakeCommand stocktakeCommand = toStocktakeCommand(command);
        return stocktakeUploadContextLoader.build3502Request(stocktakeCommand);
    }

    private static UploadAfterStocktakeCommand toStocktakeCommand(UploadAfterGainLossCommand command) {
        UploadAfterStocktakeCommand stocktakeCommand = new UploadAfterStocktakeCommand();
        if (command == null) {
            return stocktakeCommand;
        }
        stocktakeCommand.setStocktakeBusNo(command.getGainLossBusNo());
        stocktakeCommand.setSupplyTypeCode(command.getSupplyTypeCode());
        stocktakeCommand.setSupplyRequestLineIds(command.getSupplyRequestLineIds());
        stocktakeCommand.setContractBusNo(command.getContractBusNo());
        stocktakeCommand.setContractId(command.getContractId());
        stocktakeCommand.setSourceType(com.openhis.chs.domain.inventory.ChsInventoryConstants.SOURCE_TYPE_GAIN_LOSS);
        return stocktakeCommand;
    }
}
