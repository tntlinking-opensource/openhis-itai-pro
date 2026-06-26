/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory;

import com.openhis.chs.domain.inventory.dto.request.UploadPurchase3503Request;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchaseReturn3504Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSale3505Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSaleReturn3506Request;
import com.openhis.chs.domain.inventory.dto.request.UploadStocktake3501Request;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.facade.ChsInventoryOperations;
import com.openhis.health.integration.insurance.inventory.support.InventoryGainLossUploadContextLoader;
import com.openhis.health.integration.insurance.inventory.support.InventoryInboundUploadContextLoader;
import com.openhis.health.integration.insurance.inventory.support.InventoryPurchaseReturnUploadContextLoader;
import com.openhis.health.integration.insurance.inventory.support.InventoryStocktakeUploadContextLoader;
import com.openhis.health.integration.insurance.inventory.support.InventoryTransferUploadContextLoader;
import com.openhis.health.integration.insurance.inventory.support.InventoryUploadContextLoader;
import com.openhis.whale.chs.jilin.contracts.client.ChsInventoryUploadClient;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterDispenseCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterGainLossCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterInboundCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterPurchaseReturnCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterReturnCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterStocktakeCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterTransferCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 发药/退药/进货 ↔ 吉林进销存 契约实现。CHS 模块启用即自动上报（仅 upload-enabled 可关）。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInventoryUploadClientImpl implements ChsInventoryUploadClient {

    private final InventoryUploadContextLoader contextLoader;
    private final InventoryInboundUploadContextLoader inboundUploadContextLoader;
    private final InventoryPurchaseReturnUploadContextLoader purchaseReturnUploadContextLoader;
    private final InventoryStocktakeUploadContextLoader stocktakeUploadContextLoader;
    private final InventoryTransferUploadContextLoader transferUploadContextLoader;
    private final InventoryGainLossUploadContextLoader gainLossUploadContextLoader;
    private final ChsInventoryOperations inventoryOperations;

    @Value("${openhis.chs.inventory.upload-enabled:true}")
    private boolean uploadEnabled;

    @Value("${openhis.chs.inventory.sale-also-inv-change3502:false}")
    private boolean saleAlsoInvChange3502;

    @Override
    public void uploadAfterDispense(UploadAfterDispenseCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        UploadSale3505Request request = contextLoader.build3505Request(command);
        inventoryOperations.uploadSale3505(request);
        if (saleAlsoInvChange3502) {
            UploadInvChange3502Request invChangeRequest = contextLoader.build3502AfterDispenseRequest(command);
            if (invChangeRequest.getLines() != null && !invChangeRequest.getLines().isEmpty()) {
                inventoryOperations.uploadInvChange3502(invChangeRequest);
            }
        }
    }

    @Override
    @Deprecated
    public void uploadAfterOutpatientDispense(UploadAfterDispenseCommand command) {
        uploadAfterDispense(command);
    }

    @Override
    public void uploadAfterOutpatientReturn(UploadAfterReturnCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        UploadSaleReturn3506Request request = contextLoader.build3506Request(command);
        inventoryOperations.uploadSaleReturn3506(request);
    }

    @Override
    public void uploadAfterInboundAgree(UploadAfterInboundCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getSupplyRequestLineIds() == null || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadPurchase3503Request request = inboundUploadContextLoader.build3503Request(command);
        inventoryOperations.uploadPurchase3503(request);
    }

    @Override
    public void uploadAfterPurchaseReturn(UploadAfterPurchaseReturnCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getSupplyRequestLineIds() == null || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadPurchaseReturn3504Request request = purchaseReturnUploadContextLoader.build3504Request(command);
        inventoryOperations.uploadPurchaseReturn3504(request);
    }

    @Override
    public void uploadAfterStocktakeAgree(UploadAfterStocktakeCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getSupplyRequestLineIds() == null || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadStocktake3501Request request3501 = stocktakeUploadContextLoader.build3501Request(command);
        if (request3501.getLines() != null && !request3501.getLines().isEmpty()) {
            inventoryOperations.uploadStocktake3501(request3501);
        }
        UploadInvChange3502Request request3502 = stocktakeUploadContextLoader.build3502Request(command);
        if (request3502.getLines() != null && !request3502.getLines().isEmpty()) {
            inventoryOperations.uploadInvChange3502(request3502);
        }
    }

    @Override
    public void uploadAfterTransferAgree(UploadAfterTransferCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getSupplyRequestLineIds() == null || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadInvChange3502Request request = transferUploadContextLoader.build3502Request(command);
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            inventoryOperations.uploadInvChange3502(request);
        }
    }

    @Override
    public void uploadAfterGainLossAgree(UploadAfterGainLossCommand command) {
        if (!uploadEnabled) {
            return;
        }
        if (command == null || command.getSupplyRequestLineIds() == null || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadInvChange3502Request request = gainLossUploadContextLoader.build3502Request(command);
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            inventoryOperations.uploadInvChange3502(request);
        }
    }
}
