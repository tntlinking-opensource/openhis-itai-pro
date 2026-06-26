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
import com.openhis.whale.chs.jilin.contracts.client.ChsInventoryUploadClient;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterDispenseCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterGainLossCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterInboundCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterPurchaseReturnCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterReturnCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterStocktakeCommand;
import com.openhis.whale.chs.jilin.contracts.command.UploadAfterTransferCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** {@link InventoryThirdPartyUploadExtension} 实现（委托 {@link ChsInventoryUploadClient}）。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsInventoryUploadClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryThirdPartyUploadExtensionImpl implements InventoryThirdPartyUploadExtension {

    private final ChsInventoryUploadClient chsInventoryUploadClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void uploadAfterDispense(InventoryUploadAfterDispenseCommand command) {
        chsInventoryUploadClient.uploadAfterDispense(toChsDispenseCommand(command));
    }

    @Override
    public void uploadAfterReturn(InventoryUploadAfterReturnCommand command) {
        chsInventoryUploadClient.uploadAfterOutpatientReturn(toChsReturnCommand(command));
    }

    @Override
    public void uploadAfterInboundAgree(InventoryUploadAfterInboundCommand command) {
        chsInventoryUploadClient.uploadAfterInboundAgree(toChsInboundCommand(command));
    }

    @Override
    public void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnCommand command) {
        chsInventoryUploadClient.uploadAfterPurchaseReturn(toChsPurchaseReturnCommand(command));
    }

    @Override
    public void uploadAfterStocktakeAgree(InventoryUploadAfterStocktakeCommand command) {
        chsInventoryUploadClient.uploadAfterStocktakeAgree(toChsStocktakeCommand(command));
    }

    @Override
    public void uploadAfterTransferAgree(InventoryUploadAfterTransferCommand command) {
        chsInventoryUploadClient.uploadAfterTransferAgree(toChsTransferCommand(command));
    }

    @Override
    public void uploadAfterGainLossAgree(InventoryUploadAfterGainLossCommand command) {
        chsInventoryUploadClient.uploadAfterGainLossAgree(toChsGainLossCommand(command));
    }

    private static UploadAfterDispenseCommand toChsDispenseCommand(InventoryUploadAfterDispenseCommand command) {
        return new UploadAfterDispenseCommand()
            .setEncounterId(command.getEncounterId())
            .setDispenseIds(command.getDispenseIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    private static UploadAfterReturnCommand toChsReturnCommand(InventoryUploadAfterReturnCommand command) {
        return new UploadAfterReturnCommand()
            .setEncounterId(command.getEncounterId())
            .setDispenseIds(command.getDispenseIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    private static UploadAfterInboundCommand toChsInboundCommand(InventoryUploadAfterInboundCommand command) {
        return new UploadAfterInboundCommand()
            .setInboundBusNo(command.getInboundBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    private static UploadAfterPurchaseReturnCommand toChsPurchaseReturnCommand(
        InventoryUploadAfterPurchaseReturnCommand command) {
        return new UploadAfterPurchaseReturnCommand()
            .setReturnBusNo(command.getReturnBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    private static UploadAfterStocktakeCommand toChsStocktakeCommand(InventoryUploadAfterStocktakeCommand command) {
        return new UploadAfterStocktakeCommand()
            .setStocktakeBusNo(command.getStocktakeBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId())
            .setSourceType(command.getSourceType());
    }

    private static UploadAfterTransferCommand toChsTransferCommand(InventoryUploadAfterTransferCommand command) {
        return new UploadAfterTransferCommand()
            .setTransferBusNo(command.getTransferBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setInvChgType(command.getInvChgType())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    private static UploadAfterGainLossCommand toChsGainLossCommand(InventoryUploadAfterGainLossCommand command) {
        return new UploadAfterGainLossCommand()
            .setGainLossBusNo(command.getGainLossBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }
}
