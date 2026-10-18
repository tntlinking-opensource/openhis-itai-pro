/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.context;

import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3502Command;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterGainLossCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterStocktakeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HIS 主数据组装损益库存变更上报请求（损益单 supply_request → CHS 3502）。
 *
 * <p>将损益命令转换为盘点上下文后，复用
 * {@link InventoryStocktakeUploadContextLoader#build3502Request} 的盘盈盘亏行组装逻辑；
 * sourceType 标记为 {@code GAIN_LOSS}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryGainLossUploadContextLoader {

    private final InventoryStocktakeUploadContextLoader stocktakeUploadContextLoader;

    /** 组装 3502 损益库存变更上报命令。 */
    public InvUpload3502Command build3502Request(UploadAfterGainLossCommand command) {
        UploadAfterStocktakeCommand stocktakeCommand = toStocktakeCommand(command);
        return stocktakeUploadContextLoader.build3502Request(stocktakeCommand);
    }

    /**
     * 将损益命令转换为盘点上下文命令。
     *
     * @param command 损益上报命令
     * @return 盘点上报命令
     */
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
        stocktakeCommand.setSourceType(ChsInventoryConstants.SOURCE_TYPE_GAIN_LOSS);
        return stocktakeCommand;
    }
}
