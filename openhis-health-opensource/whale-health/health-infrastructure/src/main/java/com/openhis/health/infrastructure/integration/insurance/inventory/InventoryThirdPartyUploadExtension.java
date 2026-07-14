/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inventory;

import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDispenseExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterGainLossExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterInboundExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterPurchaseReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterStocktakeExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterTransferExtensionIn;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 进销存 ↔ 第三方平台上报扩展点（省份/厂商无关）。
 *
 * <p>追溯码策略判定与上报前校验内聚于 adapter（{@code *UploadContextLoader} +
 * {@code InventoryThirdPartyUploadOrchestrator}），Health 侧仅调用本 Extension 的上报方法，
 * 不再单独暴露追溯门控 SPI。</p>
 */
public interface InventoryThirdPartyUploadExtension {

    /**
     * 当前运行时是否启用第三方进销存上报（由 adapter 读取配置）。
     *
     * @return {@code true} 时 {@link #runUploadIfEnabled} 才会执行上传逻辑
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 发药/核发成功后上报进销存销售（3505；库存变动不另报 3502）。
     *
     * @param command 含就诊、发放行 id 与合同信息；扩展未启用时为 no-op
     */
    default void uploadAfterDispense(InventoryUploadAfterDispenseExtensionIn command) {
    }

    /**
     * 退药成功后上报进销存销售退货（3506；不调 3502），与 {@link #uploadAfterDispense} 成对。
     *
     * @param command 含就诊、发放行 id 与合同信息
     */
    default void uploadAfterReturn(InventoryUploadAfterReturnExtensionIn command) {
    }

    /**
     * 入库/进货审批通过后上报进销存采购（3503；库存变动已含于 3503，不调 3502）。
     *
     * @param command 含入库单号与供应行 id 列表
     */
    default void uploadAfterInboundAgree(InventoryUploadAfterInboundExtensionIn command) {
    }

    /**
     * 采购退货审批通过后上报进销存（3504；不调 3502）。
     *
     * @param command 含退货单号与供应行 id 列表
     */
    default void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnExtensionIn command) {
    }

    /**
     * 盘点审批通过后上报进销存盈亏（3502；3501 仅机构首次初始化）。
     *
     * @param command 含盘点单号、供应类型与差异行 id
     */
    default void uploadAfterStocktakeAgree(InventoryUploadAfterStocktakeExtensionIn command) {
    }

    /**
     * 调拨审批通过后上报进销存（出/入库侧由 {@code invChgType} 区分）。
     *
     * <p>院内库位间调拨/出库不再由 Health 业务 AppService 自动触发；本方法保留供运维手工补传等场景。</p>
     *
     * @param command 含调拨单号、供应类型与行 id 列表
     */
    default void uploadAfterTransferAgree(InventoryUploadAfterTransferExtensionIn command) {
    }

    /**
     * 报损报溢审批通过后上报进销存。
     *
     * @param command 含报损报溢单号与供应行 id 列表
     */
    default void uploadAfterGainLossAgree(InventoryUploadAfterGainLossExtensionIn command) {
    }

    /**
     * 科室领用审批通过后上报进销存（3502 其他出库）。
     *
     * @param command 含领用单号与供应行 id 列表
     */
    default void uploadAfterDeptIssueAgree(InventoryUploadAfterDeptIssueExtensionIn command) {
    }

    /**
     * 科室退领审批通过后上报进销存（3502 其他入库）。
     *
     * @param command 含退领单号与供应行 id 列表
     */
    default void uploadAfterDeptIssueReturnAgree(InventoryUploadAfterDeptIssueReturnExtensionIn command) {
    }

    /**
     * 扩展已启用时执行上传动作；异常仅记录 warn，不向上抛出。
     */
    default void runUploadIfEnabled(Runnable uploadAction) {
        // 扩展未启用或未传入动作时直接返回，避免空跑 adapter
        if (!isEnabled() || uploadAction == null) {
            return;
        }
        try {
            uploadAction.run();
        } catch (RuntimeException ex) {
            // 上报失败不反向回滚本地业务；仅 warn 便于运维排查
            Logger.getLogger(InventoryThirdPartyUploadExtension.class.getName())
                .log(Level.WARNING, "医保进销存上传失败（本地业务已成功）", ex);
        }
    }
}
