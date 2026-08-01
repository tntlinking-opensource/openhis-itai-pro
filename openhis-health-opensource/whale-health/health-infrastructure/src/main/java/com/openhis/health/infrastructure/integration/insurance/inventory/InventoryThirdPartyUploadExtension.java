/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inventory;

import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterClearBedSummarySalesReverseExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDischargeSummarySalesExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDispenseExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterGainLossExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterInboundExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterPurchaseReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterReturnExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterStocktakeExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterTransferExtensionIn;
import com.openhis.health.infrastructure.integration.noop.IntegrationAfterCommitSupport;

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
     * 发药/核发成功后上报进销存：先 3502(107) 再 3505；前置失败则跳过 3505。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚发药。</p>
     *
     * @param command 含就诊、发放行 id 与合同信息；扩展未启用时为 no-op
     */
    default void uploadAfterDispense(InventoryUploadAfterDispenseExtensionIn command) {
    }

    /**
     * 住院清床后上报汇总径净销售（3505）。
     *
     * <p>口径：入院～清床间 {@code summary_no} 非空发放行发退相抵，按发药日+药品+批号+库位+单位+单价合并；
     * 药师取发放行 {@code practitioner_id}（汇总核发写入的药师，非护士）。
     * 本地 {@code source_type=CLEAR_BED_SUMMARY}，{@code source_id=encounterId}。</p>
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚清床。</p>
     *
     * @param command 含就诊与可选合同；扩展未启用时为 no-op
     */
    default void uploadAfterDischargeSummarySales(InventoryUploadAfterDischargeSummarySalesExtensionIn command) {
    }

    /**
     * 反结/召回后冲销清床汇总径净销售（3506）。
     *
     * <p>仅当本就诊存在成功 {@code CLEAR_BED_SUMMARY} 3505 时出站；未清床反结（尚无 3505）为 no-op。
     * 冲销按审计报文原样组装，不含发药当时 {@code DISPENSE} 按患者销售。</p>
     *
     * <p><b>异步</b>：与清床 3505 同路径；失败不回滚反结/召回。</p>
     *
     * @param command 含就诊与可选合同；扩展未启用时为 no-op
     */
    default void uploadAfterClearBedSummarySalesReverse(
        InventoryUploadAfterClearBedSummarySalesReverseExtensionIn command) {
    }

    /**
     * 本就诊是否存在已成功上报、尚未被 3506 冲销的清床汇总销售。
     *
     * <p>清床写路径轻门禁：召回后若冲销未成功，禁止再次清床以免平台双报。扩展未启用时 false。</p>
     *
     * @param encounterId 就诊主键
     * @return 存在未冲销成功销售时 true
     */
    default boolean hasUnreversedClearBedSummarySales(Long encounterId) {
        return false;
    }

    /**
     * 退药成功后上报进销存：3506 成功后再 3502(106，批次=退药单号)；伴生失败不回写 3506。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚退药。</p>
     *
     * @param command 含就诊、发放行 id 与合同信息
     */
    default void uploadAfterReturn(InventoryUploadAfterReturnExtensionIn command) {
    }

    /**
     * 入库/进货审批通过后上报进销存采购（3503；成功后伴生 3502/108）。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚入库。</p>
     *
     * @param command 含入库单号与供应行 id 列表
     */
    default void uploadAfterInboundAgree(InventoryUploadAfterInboundExtensionIn command) {
    }

    /**
     * 采购退货审批通过后上报进销存（3504；成功后伴生 3502/109，批次=退供单号）。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚退货。</p>
     *
     * @param command 含退货单号与供应行 id 列表
     */
    default void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnExtensionIn command) {
    }

    /**
     * 盘点审批通过后上报进销存盈亏（3502；3501 仅机构首次初始化）。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚盘点。</p>
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
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站。</p>
     *
     * @param command 含调拨单号、供应类型与行 id 列表
     */
    default void uploadAfterTransferAgree(InventoryUploadAfterTransferExtensionIn command) {
    }

    /**
     * 报损报溢审批通过后上报进销存。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚损益。</p>
     *
     * @param command 含报损报溢单号与供应行 id 列表
     */
    default void uploadAfterGainLossAgree(InventoryUploadAfterGainLossExtensionIn command) {
    }

    /**
     * 科室领用审批通过后上报进销存（3502 其他出库）。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚领用。</p>
     *
     * @param command 含领用单号与供应行 id 列表
     */
    default void uploadAfterDeptIssueAgree(InventoryUploadAfterDeptIssueExtensionIn command) {
    }

    /**
     * 科室退领审批通过后上报进销存（3502 其他入库）。
     *
     * <p><b>异步</b>：事务提交后 {@code @Async} 出站；失败不回滚退领。</p>
     *
     * @param command 含退领单号与供应行 id 列表
     */
    default void uploadAfterDeptIssueReturnAgree(InventoryUploadAfterDeptIssueReturnExtensionIn command) {
    }

    /**
     * 扩展已启用时在事务提交后执行上传动作；异常仅记录 warn，不向上抛出。
     *
     * <p>无活跃事务时退化为立即执行，与 {@link IntegrationAfterCommitSupport} 语义一致。</p>
     */
    default void runUploadIfEnabled(Runnable uploadAction) {
        // 扩展未启用或未传入动作时直接返回，避免空跑 adapter
        if (!isEnabled() || uploadAction == null) {
            return;
        }
        IntegrationAfterCommitSupport.runAfterCommit(() -> {
            try {
                uploadAction.run();
            } catch (RuntimeException ex) {
                // 上报失败不反向回滚本地业务；仅 warn 便于运维排查
                Logger.getLogger(InventoryThirdPartyUploadExtension.class.getName())
                    .log(Level.WARNING, "医保进销存上传失败（本地业务已成功）", ex);
            }
        });
    }
}
