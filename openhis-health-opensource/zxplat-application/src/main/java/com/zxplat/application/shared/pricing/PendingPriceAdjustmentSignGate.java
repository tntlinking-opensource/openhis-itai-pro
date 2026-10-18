/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pricing;

import com.zxplat.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * 签发前 pending 调价互斥门禁（BR-PRC-LOCK-007）。
 *
 * <p>调价单处于 {@code pending} 时禁止对该品规签发药品/耗材医嘱，避免审批窗口内夹心价。</p>
 */
@Component
@RequiredArgsConstructor
public class PendingPriceAdjustmentSignGate {

    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /**
     * 药品定义集合签发前断言：任一条存在 pending 调价则整批拒绝。
     *
     * @param medicationDefinitionIds 药品定义主键集合；空集合直接通过
     * @throws BusinessException {@link ErrorCodeConstants#PRICE_ADJUSTMENT_ITEM_PENDING}
     */
    public void assertNoPendingForMedications(Collection<Long> medicationDefinitionIds) {
        this.assertNoPending(TableNameConstants.MED_MEDICATION_DEFINITION, medicationDefinitionIds, "药品");
    }

    /**
     * 耗材定义集合签发前断言：任一条存在 pending 调价则整批拒绝。
     *
     * @param deviceDefinitionIds 耗材定义主键集合；空集合直接通过
     * @throws BusinessException {@link ErrorCodeConstants#PRICE_ADJUSTMENT_ITEM_PENDING}
     */
    public void assertNoPendingForDevices(Collection<Long> deviceDefinitionIds) {
        this.assertNoPending(TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefinitionIds, "耗材");
    }

    /**
     * 单品规签发前断言。
     *
     * @param itemTable 项目物理表
     * @param itemId    项目定义主键
     * @param itemKind  提示用语（药品/耗材）
     * @throws BusinessException pending 存在时
     */
    public void assertNoPendingForItem(String itemTable, Long itemId, String itemKind) {
        if (itemId == null) {
            return;
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.add(itemId);
        this.assertNoPending(itemTable, ids, itemKind);
    }

    /**
     * 按项目表批量断言无 pending 调价。
     *
     * @param itemTable 项目物理表
     * @param itemIds   项目定义主键
     * @param itemKind  业务提示用语
     * @throws BusinessException 存在 pending 时
     */
    private void assertNoPending(String itemTable, Collection<Long> itemIds, String itemKind) {
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return;
        }
        // Gate：pending 调价窗口内禁止新签发，价格以审批通过后 ACTIVE 行为准
        if (this.priceAdjustmentQueryService.existsPendingByAnyItem(itemTable, itemIds)) {
            String kindLabel = itemKind;
            if (StringUtils.isEmpty(kindLabel)) {
                kindLabel = "项目";
            }
            throw new BusinessException(
                ErrorCodeConstants.PRICE_ADJUSTMENT_ITEM_PENDING,
                "该" + kindLabel + "存在待审批调价单，暂不可签发");
        }
    }
}
