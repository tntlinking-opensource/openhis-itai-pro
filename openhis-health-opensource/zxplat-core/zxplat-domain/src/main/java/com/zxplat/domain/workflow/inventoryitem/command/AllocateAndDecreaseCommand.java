/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 跨批次（或同批多供应商行）先进先出分配扣减命令。
 *
 * <p>批号为空：在库房下按效期 FEFO 跨不同批号占用；批号非空：仅在同批号多账面行（含不同供应商）间 FEFO 拆分扣减。</p>
 */
@Getter
@Setter
public class AllocateAndDecreaseCommand extends BatchCommand<AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand> {

    /** 操作人标识（审计预留；本命令写路径当前不落操作人列）。 */
    private Long operatorId;

    /**
     * 分配并扣减项（一次命令仅一条）。
     */
    @Getter
    @Setter
    public static class AllocateAndDecreaseItemCommand {
        /** 物品主表名（如 {@code med_medication_definition}）。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 需求拆零数量（须 &gt; 0，与账面 {@code quantity} 同口径）。 */
        private BigDecimal requiredQty;
        /** 发放库房库位主键；可空表示不收窄库位（发药路径须非空）。 */
        private Long locationId;
        /**
         * 批号；非空时仅在该批号的可用账面行间 FEFO 拆分扣减（同批多供应商）。
         * <p>空或空白：不按批号收窄，跨批号 FEFO（无批号发药路径）。</p>
         */
        private String lotNumber;
        /**
         * 供应商；非空时只在该供应商的账面行间拆分。
         * <p>空：同库同品（及批号）下不区分供应商，与发药同批多行口径一致。批号价不含供应商，同批同价。</p>
         */
        private Long supplierId;
        /**
         * 为 true 时不过滤效期（库房调拨、出库、退货仍可动已过期批）。
         * <p>空或 false：排除已过期行（发药路径）。</p>
         */
        private Boolean includeExpired;
        /**
         * 为 true 时批号按字面等值（含空白批号只扣空白批号行）。
         * <p>空或 false：批号空白表示不按批号收窄、跨批 FEFO（发药无批号路径）。</p>
         */
        private Boolean literalLot;
    }
}
