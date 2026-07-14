/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.support;

import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 供应请求写侧应用门面：库存单据「新建/修订草稿」统一入口（**BR-CHG-008**）。
 *
 * <p>行级金额权威落库由领域 {@link com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestWritePricingSupport}
 * 在 {@link SupplyRequestDomainService} 持久化前强制执行；本类仅作应用层约定入口，同意/驳回/撤回仍可直接调领域服务。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyRequestPricedWriteOrchestrator {

    private final SupplyRequestDomainService supplyRequestDomainService;

    /**
     * 创建或修订订货单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createPurchaseOrder(CreatePurchaseOrderCommand command) {
        return supplyRequestDomainService.createPurchaseOrder(command);
    }

    /**
     * 创建或修订进货单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createInboundReceipt(CreateInboundReceiptCommand command) {
        return supplyRequestDomainService.createInboundReceipt(command);
    }

    /**
     * 创建或修订出库单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createStockOut(CreateStockOutCommand command) {
        return supplyRequestDomainService.createStockOut(command);
    }

    /**
     * 创建或修订调拨单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createTransfer(CreateTransferCommand command) {
        return supplyRequestDomainService.createTransfer(command);
    }

    /**
     * 创建或修订请领单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createRequisition(CreateRequisitionCommand command) {
        return supplyRequestDomainService.createRequisition(command);
    }

    /**
     * 创建或修订盘点单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createStocktake(CreateStocktakeCommand command) {
        return supplyRequestDomainService.createStocktake(command);
    }

    /**
     * 创建或修订损益单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createGainLoss(CreateGainLossCommand command) {
        return supplyRequestDomainService.createGainLoss(command);
    }

    /**
     * 创建或修订科室发药单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createDeptIssue(CreateDeptIssueCommand command) {
        return supplyRequestDomainService.createDeptIssue(command);
    }

    /**
     * 创建或修订科室退药单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createDeptIssueReturn(CreateDeptIssueReturnCommand command) {
        return supplyRequestDomainService.createDeptIssueReturn(command);
    }

    /**
     * 创建或修订退库单。
     *
     * @param command 领域命令
     * @return 业务单号
     */
    public String createReturn(CreateReturnCommand command) {
        return supplyRequestDomainService.createReturn(command);
    }
}
