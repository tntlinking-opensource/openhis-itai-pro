/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.support;

import com.openhis.health.domain.administration.location.command.CloseInventoryOpeningCommand;
import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 库存期初业务封锁闸门（AS-BAS-INV-001 · BR-BAS-INV-OPN-003/011/012）。
 *
 * <p>封锁 = 关闭标志 OR 非 OPENING 供应业务 OR 窄口径发放类。命中时必须同写关闭行（U3）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryOpeningBusinessLockSupport {

    private final LocationQueryService locationQueryService;
    private final LocationDomainService locationDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 写路径闸门：未关闭且无业务封锁才允许继续；命中封锁时先写关闭再抛 001。
     *
     * @param locationId 目标库房（purpose）
     * @param operatorId 操作人（自动关闭用）
     * @throws BusinessException 已关闭或已封锁（{@link ErrorCodeConstants#BAS_INV_OPN_001}）
     */
    public void assertWritableOrCloseAndReject(Long locationId, Long operatorId) {
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        // 已显式关闭：直接拒绝
        if (locationQueryService.isInventoryOpeningClosed(locationId)) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_001,
                "该库房已关闭期初或已发生业务，不可再做期初");
        }
        boolean supplyBlocked = supplyRequestQueryService.existsNonOpeningBusinessForLocation(locationId);
        boolean dispenseBlocked = medicationDispenseQueryService.existsDispenseBusinessForLocation(locationId);
        // 业务封锁命中：关前若有审核中期初则拒绝自动关闭（与显式关闭 U8 同口径），否则写关闭后拒绝写操作
        if (supplyBlocked || dispenseBlocked) {
            if (supplyRequestQueryService.existsOpeningInApprovalForLocation(locationId)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_018,
                    "请先撤回或驳回审核中的期初单再关闭");
            }
            this.closeOpeningQuietly(locationId, operatorId);
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_001,
                "该库房已关闭期初或已发生业务，不可再做期初");
        }
    }

    /**
     * 仅探测是否封锁（含已关闭），不抛错、不写关闭。
     *
     * @param locationId 库房
     * @return 已关闭或命中业务封锁时 true
     */
    public boolean isLocked(Long locationId) {
        if (locationId == null) {
            return true;
        }
        if (locationQueryService.isInventoryOpeningClosed(locationId)) {
            return true;
        }
        if (supplyRequestQueryService.existsNonOpeningBusinessForLocation(locationId)) {
            return true;
        }
        if (medicationDispenseQueryService.existsDispenseBusinessForLocation(locationId)) {
            return true;
        }
        return false;
    }

    /**
     * 幂等写关闭行（业务封锁自动关闭 / IF-14 显式关闭共用）。
     *
     * @param locationId 库房
     * @param operatorId 操作人
     */
    public void closeOpeningQuietly(Long locationId, Long operatorId) {
        CloseInventoryOpeningCommand cmd = new CloseInventoryOpeningCommand();
        cmd.setOperatorId(operatorId);
        CloseInventoryOpeningCommand.CloseInventoryOpeningItemCommand item =
            new CloseInventoryOpeningCommand.CloseInventoryOpeningItemCommand();
        item.setLocationId(locationId);
        cmd.setItem(item);
        locationDomainService.closeInventoryOpening(cmd);
    }
}
