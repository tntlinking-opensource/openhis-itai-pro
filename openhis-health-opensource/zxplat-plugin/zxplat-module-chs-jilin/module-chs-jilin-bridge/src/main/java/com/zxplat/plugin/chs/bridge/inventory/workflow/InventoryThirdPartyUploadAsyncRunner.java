/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.workflow;

import com.zxplat.plugin.chs.bridge.inventory.context.InventoryClearBedSummaryReverseUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryDeptIssueUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryDischargeSummaryUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryGainLossUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryInboundUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryPurchaseReturnUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryStocktakeUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryTransferUploadContextLoader;
import com.zxplat.plugin.chs.bridge.inventory.context.InventoryUploadContextLoader;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3502Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3503Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3504Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3505Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3506Command;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterClearBedSummarySalesReverseCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterDeptIssueCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterDeptIssueReturnCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterDischargeSummarySalesCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterGainLossCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterInboundCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterPurchaseReturnCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterStocktakeCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterTransferCommand;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 进销存第三方上报异步出站 Runner（3501–3506 业务 Extension 全路径）。
 *
 * <p>所有上报在事务提交后由 {@link com.zxplat.plugin.chs.bridge.integration.insurance.inventory.InventoryThirdPartyUploadExtensionImpl}
 * 委托本类 {@code @Async} 执行；失败仅记 warn，不回滚 HIS 主流程。</p>
 *
 * <p><b>上下文（对齐发票）</b>：调用线程捕获 {@code tenantId} + {@code operatorId}/{@code operatorName}
 * 写入 {@code InvUpload*} 命令；异步线程只还原租户，操作人从命令读取——同博思
 * {@code BossIssueSnapshot.operatorId}，不依赖异步线程 SecurityContext。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(InventoryThirdPartyUploadOrchestrator.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryThirdPartyUploadAsyncRunner {

    private final InventoryThirdPartyUploadOrchestrator uploadOrchestrator;
    private final InventoryUploadContextLoader dispenseUploadContextLoader;
    private final InventoryInboundUploadContextLoader inboundUploadContextLoader;
    private final InventoryPurchaseReturnUploadContextLoader purchaseReturnUploadContextLoader;
    private final InventoryStocktakeUploadContextLoader stocktakeUploadContextLoader;
    private final InventoryTransferUploadContextLoader transferUploadContextLoader;
    private final InventoryGainLossUploadContextLoader gainLossUploadContextLoader;
    private final InventoryDeptIssueUploadContextLoader deptIssueUploadContextLoader;
    private final InventoryDischargeSummaryUploadContextLoader dischargeSummaryUploadContextLoader;
    private final InventoryClearBedSummaryReverseUploadContextLoader clearBedSummaryReverseUploadContextLoader;
    private final TenantContext tenantContext;

    /**
     * 异步上报发药销售 3505；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键（必填）
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      发药上报命令
     */
    @Async
    public void uploadAfterDispense(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterDispenseCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
                return;
            }
            // Pre：组装 3505，写入调用线程操作人
            InvUpload3505Command request = dispenseUploadContextLoader.build3505Request(command);
            applyOperator(request, operatorId, operatorName);
            // Cmd：出站
            uploadOrchestrator.uploadSale3505(request);
        }, "发药3505", summarizeDispenseCommand(command));
    }

    /**
     * 异步上报住院清床/无床直结净销售合并 3505；失败仅记日志。
     *
     * <p>出站走 {@link InventoryThirdPartyUploadOrchestrator#uploadSale3505}，与门诊发药同路径：
     * **3502(107)→3505**；3502 失败仍继续 3505；3505 失败不回写 3502。</p>
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      清床/无床直结合并命令
     */
    @Async
    public void uploadAfterDischargeSummarySales(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterDischargeSummarySalesCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getEncounterId() == null) {
                return;
            }
            // Pre：按就诊发退相抵并按药+批+包装锚价分桶后组装 3505
            InvUpload3505Command request = dischargeSummaryUploadContextLoader.build3505Request(command);
            // 净量为空：无可报销售行，静默结束
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            // Cmd：与门诊发药同 Orchestrator（含前置 3502 单向隔离）
            uploadOrchestrator.uploadSale3505(request);
        }, "清床合并3505", summarizeEncounterCommand(command));
    }

    /**
     * 异步冲销清床/无床直结合并净销售 3506；无成功 3505 时 no-op；失败仅记日志。
     *
     * <p>出站走 {@link InventoryThirdPartyUploadOrchestrator#uploadSaleReturn3506}，与门诊退药同路径：
     * **3506 成功后再 3502(106)**；伴生失败不回写 3506；组装侧不写 HIS 发放表。</p>
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      冲销命令
     */
    @Async
    public void uploadAfterClearBedSummarySalesReverse(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterClearBedSummarySalesReverseCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getEncounterId() == null) {
                return;
            }
            // Pre：按已成功合并 3505 审计组装 3506（不写 MedicationDispense）
            InvUpload3506Command request =
                clearBedSummaryReverseUploadContextLoader.build3506ReverseRequest(command);
            // 无成功清床 3505 可冲销时为空
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            // Cmd：与门诊退药同 Orchestrator（含后置 3502 单向隔离）
            uploadOrchestrator.uploadSaleReturn3506(request);
        }, "清床冲销3506", summarizeEncounterCommand(command));
    }

    /**
     * 异步上报退药销售退货 3506；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      退药上报命令
     */
    @Async
    public void uploadAfterReturn(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterReturnCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
                return;
            }
            // Pre：异步无登录态；3511 须用调用线程操作人快照
            command.setOperatorId(operatorId);
            command.setOperatorName(operatorName);
            InvUpload3506Command request = dispenseUploadContextLoader.build3506Request(command);
            applyOperator(request, operatorId, operatorName);
            // Cmd：出站
            uploadOrchestrator.uploadSaleReturn3506(request);
        }, "退药3506", summarizeDispenseCommand(command));
    }

    /**
     * 异步上报入库采购 3503；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      入库上报命令
     */
    @Async
    public void uploadAfterInboundAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterInboundCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3503Command request = inboundUploadContextLoader.build3503Request(command);
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadPurchase3503(request);
        }, "入库3503", summarizeInboundCommand(command));
    }

    /**
     * 异步上报采购退货 3504；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      采购退货上报命令
     */
    @Async
    public void uploadAfterPurchaseReturn(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterPurchaseReturnCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3504Command request = purchaseReturnUploadContextLoader.build3504Request(command);
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadPurchaseReturn3504(request);
        }, "采购退货3504", summarizePurchaseReturnCommand(command));
    }

    /**
     * 异步上报盘点盈亏 3502；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      盘点上报命令
     */
    @Async
    public void uploadAfterStocktakeAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterStocktakeCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            // Pre：盘点差异行组装 3502
            InvUpload3502Command request3502 = stocktakeUploadContextLoader.build3502Request(command);
            // 无盈亏行不报
            if (request3502.getLines() == null || request3502.getLines().isEmpty()) {
                return;
            }
            applyOperator(request3502, operatorId, operatorName);
            uploadOrchestrator.uploadInvChange3502(request3502);
        }, "盘点3502", summarizeStocktakeCommand(command));
    }

    /**
     * 异步上报调拨库存变更 3502；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      调拨上报命令
     */
    @Async
    public void uploadAfterTransferAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterTransferCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3502Command request = transferUploadContextLoader.build3502Request(command);
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadInvChange3502(request);
        }, "调拨3502", summarizeTransferCommand(command));
    }

    /**
     * 异步上报损益库存变更 3502；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      损益上报命令
     */
    @Async
    public void uploadAfterGainLossAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterGainLossCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3502Command request = gainLossUploadContextLoader.build3502Request(command);
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadInvChange3502(request);
        }, "损益3502", summarizeGainLossCommand(command));
    }

    /**
     * 异步上报科室领用其他出库 3502；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      领用上报命令
     */
    @Async
    public void uploadAfterDeptIssueAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterDeptIssueCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3502Command request = deptIssueUploadContextLoader.build3502DeptIssueRequest(command);
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadInvChange3502(request);
        }, "科室领用3502", summarizeDeptIssueCommand(command));
    }

    /**
     * 异步上报科室退领其他入库 3502；失败仅记日志。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param operatorId   调用线程捕获的操作人主键
     * @param operatorName 调用线程捕获的操作人姓名；可空
     * @param command      退领上报命令
     */
    @Async
    public void uploadAfterDeptIssueReturnAgree(
        Long tenantId,
        Long operatorId,
        String operatorName,
        UploadAfterDeptIssueReturnCommand command
    ) {
        runWithTenant(tenantId, () -> {
            if (command == null || command.getSupplyRequestLineIds() == null
                || command.getSupplyRequestLineIds().isEmpty()) {
                return;
            }
            InvUpload3502Command request =
                deptIssueUploadContextLoader.build3502DeptIssueReturnRequest(command);
            if (request.getLines() == null || request.getLines().isEmpty()) {
                return;
            }
            applyOperator(request, operatorId, operatorName);
            uploadOrchestrator.uploadInvChange3502(request);
        }, "科室退领3502", summarizeDeptIssueReturnCommand(command));
    }

    /**
     * 将调用线程操作人写入上报命令（对齐发票快照落 operatorId）。
     *
     * @param request      3505 命令
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private static void applyOperator(InvUpload3505Command request, Long operatorId, String operatorName) {
        if (request == null) {
            return;
        }
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
    }

    /**
     * 将调用线程操作人写入 3506 命令。
     *
     * @param request      3506 命令
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private static void applyOperator(InvUpload3506Command request, Long operatorId, String operatorName) {
        if (request == null) {
            return;
        }
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
    }

    /**
     * 将调用线程操作人写入 3503 命令。
     *
     * @param request      3503 命令
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private static void applyOperator(InvUpload3503Command request, Long operatorId, String operatorName) {
        if (request == null) {
            return;
        }
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
    }

    /**
     * 将调用线程操作人写入 3504 命令。
     *
     * @param request      3504 命令
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private static void applyOperator(InvUpload3504Command request, Long operatorId, String operatorName) {
        if (request == null) {
            return;
        }
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
    }

    /**
     * 将调用线程操作人写入 3502 命令。
     *
     * @param request      3502 命令
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private static void applyOperator(InvUpload3502Command request, Long operatorId, String operatorName) {
        if (request == null) {
            return;
        }
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
    }

    /**
     * 在异步线程还原租户后执行上报；异常仅 warn。
     *
     * @param tenantId     调用线程捕获的租户主键
     * @param uploadAction 实际上报逻辑
     * @param actionLabel  日志标签
     * @param contextHint  日志上下文摘要
     */
    private void runWithTenant(Long tenantId, Runnable uploadAction, String actionLabel, String contextHint) {
        try {
            // Pre：还原租户，保证多租户仓储读到正确库/过滤条件
            if (tenantId != null) {
                tenantContext.setCurrentTenantId(tenantId);
            }
            uploadAction.run();
        } catch (RuntimeException ex) {
            log.warn("医保进销存{}异步上传失败 {}", actionLabel, contextHint, ex);
        } finally {
            tenantContext.clear();
        }
    }

    /**
     * 汇总发药/退药命令上下文供日志使用。
     *
     * @param command 发药或退药命令
     * @return 日志摘要
     */
    private static String summarizeDispenseCommand(Object command) {
        if (command instanceof UploadAfterDispenseCommand dispense) {
            return "encounterId=" + dispense.getEncounterId();
        }
        if (command instanceof UploadAfterReturnCommand ret) {
            return "encounterId=" + ret.getEncounterId();
        }
        return "";
    }

    /**
     * 汇总清床相关命令上下文供日志使用。
     *
     * @param command 清床或冲销命令
     * @return 日志摘要
     */
    private static String summarizeEncounterCommand(Object command) {
        if (command instanceof UploadAfterDischargeSummarySalesCommand summary) {
            return "encounterId=" + summary.getEncounterId();
        }
        if (command instanceof UploadAfterClearBedSummarySalesReverseCommand reverse) {
            return "encounterId=" + reverse.getEncounterId();
        }
        return "";
    }

    /**
     * 汇总入库命令上下文供日志使用。
     *
     * @param command 入库命令
     * @return 日志摘要
     */
    private static String summarizeInboundCommand(UploadAfterInboundCommand command) {
        if (command == null) {
            return "";
        }
        return "inboundBusNo=" + command.getInboundBusNo();
    }

    /**
     * 汇总采购退货命令上下文供日志使用。
     *
     * @param command 采购退货命令
     * @return 日志摘要
     */
    private static String summarizePurchaseReturnCommand(UploadAfterPurchaseReturnCommand command) {
        if (command == null) {
            return "";
        }
        return "returnBusNo=" + command.getReturnBusNo();
    }

    /**
     * 汇总盘点命令上下文供日志使用。
     *
     * @param command 盘点命令
     * @return 日志摘要
     */
    private static String summarizeStocktakeCommand(UploadAfterStocktakeCommand command) {
        if (command == null) {
            return "";
        }
        return "stocktakeBusNo=" + command.getStocktakeBusNo();
    }

    /**
     * 汇总调拨命令上下文供日志使用。
     *
     * @param command 调拨命令
     * @return 日志摘要
     */
    private static String summarizeTransferCommand(UploadAfterTransferCommand command) {
        if (command == null) {
            return "";
        }
        return "transferBusNo=" + command.getTransferBusNo();
    }

    /**
     * 汇总损益命令上下文供日志使用。
     *
     * @param command 损益命令
     * @return 日志摘要
     */
    private static String summarizeGainLossCommand(UploadAfterGainLossCommand command) {
        if (command == null) {
            return "";
        }
        return "gainLossBusNo=" + command.getGainLossBusNo();
    }

    /**
     * 汇总科室领用命令上下文供日志使用。
     *
     * @param command 领用命令
     * @return 日志摘要
     */
    private static String summarizeDeptIssueCommand(UploadAfterDeptIssueCommand command) {
        if (command == null) {
            return "";
        }
        return "deptIssueBusNo=" + command.getDeptIssueBusNo();
    }

    /**
     * 汇总科室退领命令上下文供日志使用。
     *
     * @param command 退领命令
     * @return 日志摘要
     */
    private static String summarizeDeptIssueReturnCommand(UploadAfterDeptIssueReturnCommand command) {
        if (command == null) {
            return "";
        }
        return "deptIssueReturnBusNo=" + command.getDeptIssueReturnBusNo();
    }
}
