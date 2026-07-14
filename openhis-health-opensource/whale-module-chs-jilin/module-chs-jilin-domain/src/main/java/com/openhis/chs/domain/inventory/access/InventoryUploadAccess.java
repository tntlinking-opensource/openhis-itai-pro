/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.access;

import com.openhis.chs.domain.inventory.command.InvUpload3501Command;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.chs.domain.inventory.command.InvUpload3503Command;
import com.openhis.chs.domain.inventory.command.InvUpload3504Command;
import com.openhis.chs.domain.inventory.command.InvUpload3505Command;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.inventory.result.InvUpload3501Result;
import com.openhis.chs.domain.inventory.result.InvUpload3502Result;
import com.openhis.chs.domain.inventory.result.InvUpload3503Result;
import com.openhis.chs.domain.inventory.result.InvUpload3504Result;
import com.openhis.chs.domain.inventory.result.InvUpload3505Result;
import com.openhis.chs.domain.inventory.result.InvUpload3506Result;

import java.util.List;

/**
 * 【3501–3506】进销存上报编排出站端口。
 *
 * <p><b>职责：</b>3501–3506 进销存上报、落库与追溯编排。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.inventory.workflow.InventoryUploadWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface InventoryUploadAccess {

    /**
     * 3505 销售出库上报。
     *
     * @param command 销售出库上报命令
     * @return 各行上报结果
     */
    List<InvUpload3505Result> uploadSale3505(InvUpload3505Command command);

    /**
     * 3506 销售退货上报。
     *
     * @param command 销售退货上报命令
     * @return 各行上报结果
     */
    List<InvUpload3506Result> uploadSaleReturn3506(InvUpload3506Command command);

    /**
     * 3503 采购入库上报。
     *
     * @param command 采购入库上报命令
     * @return 各行上报结果
     */
    List<InvUpload3503Result> uploadPurchase3503(InvUpload3503Command command);

    /**
     * 3504 采购退货上报。
     *
     * @param command 采购退货上报命令
     * @return 各行上报结果
     */
    List<InvUpload3504Result> uploadPurchaseReturn3504(InvUpload3504Command command);

    /**
     * 3501 盘存上报。
     *
     * @param command 盘存上报命令
     * @return 各行上报结果
     */
    List<InvUpload3501Result> uploadStocktake3501(InvUpload3501Command command);

    /**
     * 3502 库存变更上报。
     *
     * @param command 库存变更上报命令
     * @return 各行上报结果
     */
    List<InvUpload3502Result> uploadInvChange3502(InvUpload3502Command command);
}
