/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory;

import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterDispenseCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterGainLossCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterInboundCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterPurchaseReturnCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterReturnCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterStocktakeCommand;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterTransferCommand;

/**
 * 进销存 ↔ 第三方平台上报扩展点（省份/厂商无关）。
 */
public interface InventoryThirdPartyUploadExtension {

    default boolean isEnabled() {
        return false;
    }

    default void uploadAfterDispense(InventoryUploadAfterDispenseCommand command) {
    }

    default void uploadAfterReturn(InventoryUploadAfterReturnCommand command) {
    }

    default void uploadAfterInboundAgree(InventoryUploadAfterInboundCommand command) {
    }

    default void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnCommand command) {
    }

    default void uploadAfterStocktakeAgree(InventoryUploadAfterStocktakeCommand command) {
    }

    default void uploadAfterTransferAgree(InventoryUploadAfterTransferCommand command) {
    }

    default void uploadAfterGainLossAgree(InventoryUploadAfterGainLossCommand command) {
    }
}
