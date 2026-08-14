/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.inventory;

import com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadAsyncRunner;
import com.openhis.chs.domain.inventory.command.UploadAfterClearBedSummarySalesReverseCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDeptIssueReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDischargeSummarySalesCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterDispenseCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterGainLossCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterInboundCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterPurchaseReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterReturnCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterStocktakeCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterTransferCommand;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
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
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林医保进销存第三方上报 SPI 实现。
 *
 * <p><b>3502 搭配（吉林省）</b>：3501 初始化成对 3502；3503/3504 成功后跟 3502（108/109）；
 * <strong>3505 前先试 3502(107) 再调 3505（3502 失败仍继续），3506 成功后再 3502(106，批次=退药单号)</strong>。
 * 详见 whale-doc {@code 09-医保进销存-详细设计.md} §2.6。</p>
 *
 * <p><b>异步</b>：除 {@link #hasUnreversedClearBedSummarySales} 同步查询外，全部 {@code uploadAfter*} 委托
 * {@link InventoryThirdPartyUploadAsyncRunner} 在事务提交后 {@code @Async} 出站；失败不回滚 HIS 主流程。
 * 操作人对齐发票：在<strong>调用线程</strong>（afterCommit 时仍有登录态）解析 {@code operatorId}/{@code operatorName}
 * 传入异步，异步侧写入 {@code InvUpload*} 命令——同 {@code BossIssueSnapshot.operatorId}，不依赖异步 SecurityContext。</p>
 *
 * <p><b>追溯码</b>：策略与上报前校验内聚于 {@code *UploadContextLoader} 与 Orchestrator，Health 仅调用本 SPI。</p>
 *
 * <p><b>SPI 接口</b>：{@link InventoryThirdPartyUploadExtension}</p>
 * <p><b>Health 调用方</b>：门诊发退药、库房进销存、住院清床/无床直结净销售合并、反结/召回冲销（住院发/退药当时不上报）</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(InventoryThirdPartyUploadAsyncRunner.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryThirdPartyUploadExtensionImpl implements InventoryThirdPartyUploadExtension {

    private final InventoryThirdPartyUploadAsyncRunner uploadAsyncRunner;
    /** 清床轻门禁：未冲销汇总销售查询。 */
    private final YbInvUploadQueryService invUploadQueryService;
    /** 调用线程解析操作人主键。 */
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    /** 调用线程解析操作人姓名（供平台 InvokeContext）。 */
    private final ChsSignCredentialResolver signCredentialResolver;

    @Value("${openhis.chs.inventory.upload-enabled:true}")
    private boolean uploadEnabled;

    /**
     * 是否启用进销存上报：与 {@code openhis.chs.inventory.upload-enabled} 对齐，
     * 供 {@code runUploadIfEnabled} / 清床门禁短路，避免开关关闭后仍注册 afterCommit。
     *
     * @return 配置开启时 true
     */
    @Override
    public boolean isEnabled() {
        return uploadEnabled;
    }

    /** 【3505】发药后上报：先试 3502(107) 再 3505（异步；3502 失败仍继续 3505）。 */
    @Override
    public void uploadAfterDispense(InventoryUploadAfterDispenseExtensionIn command) {
        if (!uploadEnabled || command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        // Pre：调用线程捕获操作人（afterCommit 时登录态仍在）
        CapturedOperator operator = captureOperator();
        // Cmd：委托异步 Runner，立即返回
        uploadAsyncRunner.uploadAfterDispense(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toDispenseCommand(command));
    }

    /**
     * 【3505】清床/无床直结后住院净销售合并上报（异步）。
     *
     * @param command 就诊与可选契约
     */
    @Override
    public void uploadAfterDischargeSummarySales(InventoryUploadAfterDischargeSummarySalesExtensionIn command) {
        if (!uploadEnabled || command == null || command.getEncounterId() == null) {
            return;
        }
        CapturedOperator operator = captureOperator();
        // Cmd：委托异步 Runner，立即返回，不阻塞清床/结算 HTTP
        uploadAsyncRunner.uploadAfterDischargeSummarySales(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toDischargeSummaryCommand(command));
    }

    /**
     * 【3506】反结/召回冲销清床或无床直结合并净销售（异步；无成功 3505 时 no-op）。
     *
     * @param command 就诊与可选契约
     */
    @Override
    public void uploadAfterClearBedSummarySalesReverse(
        InventoryUploadAfterClearBedSummarySalesReverseExtensionIn command) {
        if (!uploadEnabled || command == null || command.getEncounterId() == null) {
            return;
        }
        CapturedOperator operator = captureOperator();
        // Cmd：委托异步 Runner；未清床反结因无 SUCCESS 审计而空跑
        uploadAsyncRunner.uploadAfterClearBedSummarySalesReverse(
            currentTenantId(),
            operator.operatorId(),
            operator.operatorName(),
            toClearBedSummaryReverseCommand(command));
    }

    /**
     * 清床轻门禁：存在未冲销的成功清床/无床直结合并 3505 时 true。
     *
     * @param encounterId 就诊主键
     * @return 存在未冲销销售时 true
     */
    @Override
    public boolean hasUnreversedClearBedSummarySales(Long encounterId) {
        if (!uploadEnabled || encounterId == null) {
            return false;
        }
        return invUploadQueryService.hasUnreversedClearBedSummarySales(
            TenantUtils.getCurrentTenantId(), encounterId);
    }

    /** 【3506】退药后上报：3506 成功后再 3502(106，批次=退药单号)（异步；伴生失败不回写 3506）。 */
    @Override
    public void uploadAfterReturn(InventoryUploadAfterReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getDispenseIds() == null || command.getDispenseIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterReturn(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toReturnCommand(command));
    }

    /** 【3503】入库审核通过后上报商品采购（异步；成功后伴生 3502/108）。 */
    @Override
    public void uploadAfterInboundAgree(InventoryUploadAfterInboundExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterInboundAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toInboundCommand(command));
    }

    /** 【3504】采购退货审核通过后上报商品采购退货（异步；成功后伴生 3502/109，批次=退供单号）。 */
    @Override
    public void uploadAfterPurchaseReturn(InventoryUploadAfterPurchaseReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterPurchaseReturn(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toPurchaseReturnCommand(command));
    }

    /** 【3502】盘点审核通过后上报盘盈盘亏（3501 仅首次初始化，日常盘点不报 3501）。 */
    @Override
    public void uploadAfterStocktakeAgree(InventoryUploadAfterStocktakeExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterStocktakeAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toStocktakeCommand(command));
    }

    /** 【3502】调拨审核通过后上报商品库存变更（异步）。 */
    @Override
    public void uploadAfterTransferAgree(InventoryUploadAfterTransferExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterTransferAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toTransferCommand(command));
    }

    /** 【3502】损益审核通过后上报商品库存变更（异步）。 */
    @Override
    public void uploadAfterGainLossAgree(InventoryUploadAfterGainLossExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterGainLossAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toGainLossCommand(command));
    }

    /** 【3502】科室领用审核通过后上报其他出库（异步）。 */
    @Override
    public void uploadAfterDeptIssueAgree(InventoryUploadAfterDeptIssueExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterDeptIssueAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toDeptIssueCommand(command));
    }

    /** 【3502】科室退领审核通过后上报其他入库（异步）。 */
    @Override
    public void uploadAfterDeptIssueReturnAgree(InventoryUploadAfterDeptIssueReturnExtensionIn command) {
        if (!uploadEnabled || command == null || command.getSupplyRequestLineIds() == null
            || command.getSupplyRequestLineIds().isEmpty()) {
            return;
        }
        CapturedOperator operator = captureOperator();
        uploadAsyncRunner.uploadAfterDeptIssueReturnAgree(
            currentTenantId(), operator.operatorId(), operator.operatorName(), toDeptIssueReturnCommand(command));
    }

    /**
     * 捕获当前租户主键，供异步线程还原。
     *
     * @return 当前租户主键
     */
    private static Long currentTenantId() {
        return TenantUtils.getCurrentTenantId();
    }

    /**
     * 在调用线程（仍有登录态）解析操作人，供异步写入 {@code InvUpload*} 命令。
     *
     * <p>对齐发票：Worker 用快照 {@code operatorId}，不在异步线程 {@code requireOperatorId}。</p>
     *
     * @return 操作人主键与姓名
     */
    private CapturedOperator captureOperator() {
        // Pre：主键必须能解析，否则异步必失败且无流水
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InvokeContext partial = InvokeContext.builder()
            .operatorId(String.valueOf(operatorId))
            .build();
        // Pre：姓名可回落 admin；仍在调用线程解析，避免异步无登录名
        String operatorName = signCredentialResolver.resolveOperatorName(partial);
        return new CapturedOperator(operatorId, operatorName);
    }

    /**
     * 调用线程捕获的操作人（异步出站载荷）。
     *
     * @param operatorId   操作人主键
     * @param operatorName 操作人姓名
     */
    private record CapturedOperator(Long operatorId, String operatorName) {
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
     * 将清床/无床直结合并上报 ExtensionIn 映射为 CHS 命令。
     *
     * @param command 清床/无床直结合并扩展入参
     * @return CHS 清床合并上报命令
     */
    private static UploadAfterDischargeSummarySalesCommand toDischargeSummaryCommand(
        InventoryUploadAfterDischargeSummarySalesExtensionIn command) {
        return new UploadAfterDischargeSummarySalesCommand()
            .setEncounterId(command.getEncounterId())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId());
    }

    /**
     * 将清床汇总径冲销 ExtensionIn 映射为 CHS 命令。
     *
     * @param command 冲销扩展入参
     * @return CHS 冲销命令
     */
    private static UploadAfterClearBedSummarySalesReverseCommand toClearBedSummaryReverseCommand(
        InventoryUploadAfterClearBedSummarySalesReverseExtensionIn command) {
        return new UploadAfterClearBedSummarySalesReverseCommand()
            .setEncounterId(command.getEncounterId())
            .setContractBusNo(command.getContractBusNo())
            .setContractId(command.getContractId())
            .setDispenseIds(command.getDispenseIds());
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
