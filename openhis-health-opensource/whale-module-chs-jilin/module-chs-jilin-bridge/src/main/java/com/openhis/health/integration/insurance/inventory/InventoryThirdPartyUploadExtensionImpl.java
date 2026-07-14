/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.inventory;

import com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterGainLossCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterInboundCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterPurchaseReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterStocktakeCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterTransferCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDispenseExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterGainLossExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterInboundExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterPurchaseReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterStocktakeExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterTransferExtensionIn;
import com.openhis.chs.bridge.inventory.context.InventoryDeptIssueUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryGainLossUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryInboundUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryPurchaseReturnUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryStocktakeUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryTransferUploadContextLoader;
import com.openhis.chs.bridge.inventory.context.InventoryUploadContextLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林医保进销存第三方上报 SPI 实现。
 *
 * <p><b>3502 搭配（吉林省）</b>：仅 3501 首次初始化成对调 3502；3503/3504/3505/3506 调完<strong>不再</strong>调 3502。
 * 详见 whale-doc {@code 09-医保进销存-详细设计.md} §2.6。</p>
 *
 * <p><b>追溯码</b>：策略与上报前校验内聚于 {@code *UploadContextLoader} 与
 * {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator}（
 * {@link com.openhis.chs.bridge.inventory.support.InventoryUploadTraceLineSupport}），Health 仅调用本 SPI。</p>
 *
 * <p><b>SPI 接口</b>：{@link InventoryThirdPartyUploadExtension}</p>
 * <p><b>Health 调用方</b>：门诊/住院发退药 AppService（{@code OutpatientPharmacyDispenseAppService}、
 * {@code InpatientPharmacyDispenseAppService} 等）及库房/药房/科室进销存 AppService</p>
 * <p><b>Bridge 委托</b>：各 {@code *UploadContextLoader} 组装请求 →
 * {@link InventoryThirdPartyUploadOrchestrator} 出站上报</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(InventoryThirdPartyUploadOrchestrator.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryThirdPartyUploadExtensionImpl implements InventoryThirdPartyUploadExtension {

    private final InventoryThirdPartyUploadOrchestrator uploadOrchestrator;
    private final InventoryUploadContextLoader dispenseUploadContextLoader;
    private final InventoryInboundUploadContextLoader inboundUploadContextLoader;
    private final InventoryPurchaseReturnUploadContextLoader purchaseReturnUploadContextLoader;
    private final InventoryStocktakeUploadContextLoader stocktakeUploadContextLoader;
    private final InventoryTransferUploadContextLoader transferUploadContextLoader;
    private final InventoryGainLossUploadContextLoader gainLossUploadContextLoader;
    private final InventoryDeptIssueUploadContextLoader deptIssueUploadContextLoader;

    @Value("${openhis.chs.inventory.upload-enabled:true}")
    private boolean uploadEnabled;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 【3505】发药后上报商品销售（库存变动由 3505 覆盖，不再伴生 3502）。 */
    @Override
    public void uploadAfterDispense(InventoryUploadAfterDispenseExtensionIn command) {
        if (!uploadEnabled || command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        UploadAfterDispenseCommand domain = toDispenseCommand(command);
        uploadOrchestrator.uploadSale3505(dispenseUploadContextLoader.build3505Request(domain));
    }

    /** 【3506】退药后上报商品销售退货（不调 3502）。 */
    @Override
    public void uploadAfterReturn(InventoryUploadAfterReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        uploadOrchestrator.uploadSaleReturn3506(
            dispenseUploadContextLoader.build3506Request(toReturnCommand(command)));
    }

    /** 【3503】入库审核通过后上报商品采购（不调 3502）。 */
    @Override
    public void uploadAfterInboundAgree(InventoryUploadAfterInboundExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        uploadOrchestrator.uploadPurchase3503(
            inboundUploadContextLoader.build3503Request(toInboundCommand(command)));
    }

    /** 【3504】采购退货审核通过后上报商品采购退货（不调 3502）。 */
    @Override
    public void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        uploadOrchestrator.uploadPurchaseReturn3504(
            purchaseReturnUploadContextLoader.build3504Request(toPurchaseReturnCommand(command)));
    }

    /** 【3502】盘点审核通过后上报盘盈盘亏（3501 仅首次初始化，日常盘点不报 3501）。 */
    @Override
    public void uploadAfterStocktakeAgree(InventoryUploadAfterStocktakeExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        UploadAfterStocktakeCommand domain = toStocktakeCommand(command);
        InvUpload3502Command request3502 = stocktakeUploadContextLoader.build3502Request(domain);
        if (request3502.getLines() != null && !request3502.getLines().isEmpty()) {
            uploadOrchestrator.uploadInvChange3502(request3502);
        }
    }

    /** 【3502】调拨审核通过后上报商品库存变更。 */
    @Override
    public void uploadAfterTransferAgree(InventoryUploadAfterTransferExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        InvUpload3502Command request = transferUploadContextLoader.build3502Request(toTransferCommand(command));
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            uploadOrchestrator.uploadInvChange3502(request);
        }
    }

    /** 【3502】损益审核通过后上报商品库存变更。 */
    @Override
    public void uploadAfterGainLossAgree(InventoryUploadAfterGainLossExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        InvUpload3502Command request = gainLossUploadContextLoader.build3502Request(toGainLossCommand(command));
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            uploadOrchestrator.uploadInvChange3502(request);
        }
    }

    /** 【3502】科室领用审核通过后上报其他出库。 */
    @Override
    public void uploadAfterDeptIssueAgree(InventoryUploadAfterDeptIssueExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        InvUpload3502Command request = deptIssueUploadContextLoader.build3502DeptIssueRequest(
            toDeptIssueCommand(command));
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            uploadOrchestrator.uploadInvChange3502(request);
        }
    }

    /** 【3502】科室退领审核通过后上报其他入库。 */
    @Override
    public void uploadAfterDeptIssueReturnAgree(InventoryUploadAfterDeptIssueReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        InvUpload3502Command request = deptIssueUploadContextLoader.build3502DeptIssueReturnRequest(
            toDeptIssueReturnCommand(command));
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            uploadOrchestrator.uploadInvChange3502(request);
        }
    }

    /**
     * 将发药上报 ExtensionIn 映射为 CHS 发药上报命令。
     *
     * @param command 发药上报扩展入参
     * @return CHS 发药上报命令
     */
    private static UploadAfterDispenseCommand toDispenseCommand(InventoryUploadAfterDispenseExtensionIn command) {
        return new UploadAfterDispenseCommand()
            .setEncounterId(command.getEncounterId())
            .setDispenseIds(command.getDispenseIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将退药上报 ExtensionIn 映射为 CHS 退药上报命令。
     *
     * @param command 退药上报扩展入参
     * @return CHS 退药上报命令
     */
    private static UploadAfterReturnCommand toReturnCommand(InventoryUploadAfterReturnExtensionIn command) {
        return new UploadAfterReturnCommand()
            .setEncounterId(command.getEncounterId())
            .setDispenseIds(command.getDispenseIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将入库上报 ExtensionIn 映射为 CHS 入库上报命令。
     *
     * @param command 入库上报扩展入参
     * @return CHS 入库上报命令
     */
    private static UploadAfterInboundCommand toInboundCommand(InventoryUploadAfterInboundExtensionIn command) {
        return new UploadAfterInboundCommand()
            .setInboundBusNo(command.getInboundBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将采购退货上报 ExtensionIn 映射为 CHS 采购退货上报命令。
     *
     * @param command 采购退货上报扩展入参
     * @return CHS 采购退货上报命令
     */
    private static UploadAfterPurchaseReturnCommand toPurchaseReturnCommand(
        InventoryUploadAfterPurchaseReturnExtensionIn command) {
        return new UploadAfterPurchaseReturnCommand()
            .setReturnBusNo(command.getReturnBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将盘点上报 ExtensionIn 映射为 CHS 盘点上报命令。
     *
     * @param command 盘点上报扩展入参
     * @return CHS 盘点上报命令
     */
    private static UploadAfterStocktakeCommand toStocktakeCommand(InventoryUploadAfterStocktakeExtensionIn command) {
        return new UploadAfterStocktakeCommand()
            .setStocktakeBusNo(command.getStocktakeBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId())
            .setSourceType(command.getSourceType());
    }

    /**
     * 将调拨上报 ExtensionIn 映射为 CHS 调拨上报命令。
     *
     * @param command 调拨上报扩展入参
     * @return CHS 调拨上报命令
     */
    private static UploadAfterTransferCommand toTransferCommand(InventoryUploadAfterTransferExtensionIn command) {
        return new UploadAfterTransferCommand()
            .setTransferBusNo(command.getTransferBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setInvChgType(command.getInvChgType())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将损益上报 ExtensionIn 映射为 CHS 损益上报命令。
     *
     * @param command 损益上报扩展入参
     * @return CHS 损益上报命令
     */
    private static UploadAfterGainLossCommand toGainLossCommand(InventoryUploadAfterGainLossExtensionIn command) {
        return new UploadAfterGainLossCommand()
            .setGainLossBusNo(command.getGainLossBusNo())
            .setSupplyTypeCode(command.getSupplyTypeCode())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将科室领用上报 ExtensionIn 映射为 CHS 领用上报命令。
     *
     * @param command 领用上报扩展入参
     * @return CHS 领用上报命令
     */
    private static UploadAfterDeptIssueCommand toDeptIssueCommand(InventoryUploadAfterDeptIssueExtensionIn command) {
        return new UploadAfterDeptIssueCommand()
            .setDeptIssueBusNo(command.getDeptIssueBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将科室退领上报 ExtensionIn 映射为 CHS 退领上报命令。
     *
     * @param command 退领上报扩展入参
     * @return CHS 退领上报命令
     */
    private static UploadAfterDeptIssueReturnCommand toDeptIssueReturnCommand(
        InventoryUploadAfterDeptIssueReturnExtensionIn command) {
        return new UploadAfterDeptIssueReturnCommand()
            .setDeptIssueReturnBusNo(command.getDeptIssueReturnBusNo())
            .setSupplyRequestLineIds(command.getSupplyRequestLineIds())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }
}
