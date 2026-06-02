/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 供应写模型与库存交互时的数量口径约定（进货、药库出库、药房入库等路径）。
 *
 * <p>{@code wkf_supply_request} 行上 {@code item_quantity} 与库存 {@code wkf_inventory_item.quantity} 一致，均为<strong>拆零（最小）单位</strong>下的数量；
 * {@code unit_code} 仅记录录入时的操作或展示单位，<strong>不参与</strong>与库存数量的再换算。凡从供应行驱动 {@code InventoryItem} 增减的应用编排应直接使用
 * {@code item_quantity} 与账面比对或入账，禁止再按 {@code unit_code} 调用 {@link com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter}
 * 做倍数换算。</p>
 *
 * <p><strong>例外（不在本类覆盖范围）</strong>：① 盘点类单据（{@link com.openhis.health.domain.share.enums.SupplyType#isStocktakeTypeCode(String)}）
 * 行上 {@code item_quantity} 为盈亏数量，可正可负可零，审批入账须直接使用该字段，不得调用本类正数校验；
 * ② 门诊发药等场景若发放行数量仍为业务单位语义，应继续使用
 * {@link com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter} 与
 * {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService#assertSufficientForMedicationDispense} 既有契约。</p>
 */
public final class SupplyPersistedSplitQuantity {

    private SupplyPersistedSplitQuantity() {
    }

    /**
     * 校验供应读模型行物品表白名单且 {@code item_quantity} 为拆零下正数。
     *
     * @param line           供应请求读模型行，不应为 {@code null}
     * @param errorCode      失败时业务错误码
     * @param invalidMessage 数量或表非法时的提示文案
     * @return 拆零数量（与 {@code wkf_supply_request.item_quantity} 持久化一致）
     * @throws BusinessException {@code line} 为 {@code null}，或物品表不在白名单，或数量非法
     */
    public static BigDecimal requireSupplyLineItemQuantityAsPersistedSplitUnit(
        SupplyRequestModel line,
        String errorCode,
        String invalidMessage
    ) {
        if (line == null) {
            throw new BusinessException(errorCode, invalidMessage);
        }
        return requireMedicationOrDeviceTableAndPositiveSplitQuantity(
            line.getItemTable(), line.getItemQuantity(), errorCode, invalidMessage);
    }

    /**
     * 校验物品表为药品定义表或耗材定义表之一，且数量为拆零下正数。
     *
     * <p>用于出库草稿行等非 {@link SupplyRequestModel} 载体、但字段语义与供应行一致的场景。</p>
     *
     * @param itemTableRaw   物品物理表名，可 {@code null}（按空串归一）
     * @param itemQuantity   行数量，须为拆零下正数
     * @param errorCode      失败时业务错误码
     * @param invalidMessage 数量非法时的提示文案
     * @return 通过校验的数量（原样返回 {@code itemQuantity}）
     * @throws BusinessException 表名不在白名单，或 {@code itemQuantity} 为空或非正
     */
    public static BigDecimal requireMedicationOrDeviceTableAndPositiveSplitQuantity(
        String itemTableRaw,
        BigDecimal itemQuantity,
        String errorCode,
        String invalidMessage
    ) {
        String table = "";
        if (itemTableRaw != null) {
            table = itemTableRaw.trim();
        }
        if (!(TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(table))) {
            throw new BusinessException(errorCode, "不支持的物品表类型");
        }
        if (itemQuantity == null || itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(errorCode, invalidMessage);
        }
        return itemQuantity;
    }
}
