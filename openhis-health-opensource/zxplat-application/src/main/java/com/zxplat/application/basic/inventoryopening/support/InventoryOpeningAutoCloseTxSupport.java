/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.support;

import com.zxplat.domain.administration.location.command.CloseInventoryOpeningCommand;
import com.zxplat.domain.administration.location.service.LocationDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存期初业务封锁自动关闭（独立事务）。
 *
 * <p>写路径闸门命中封锁后须先落关闭行再抛错；若与外层写事务同提交，外层回滚会抹掉关闭态，违反
 * AS-BAS-INV-001 U3「业务封锁必须同写关闭行」。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryOpeningAutoCloseTxSupport {

    private final LocationDomainService locationDomainService;

    /**
     * 幂等写关闭行（新事务立即提交）。
     *
     * @param locationId 库房
     * @param operatorId 操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void closeOpeningInNewTransaction(Long locationId, Long operatorId) {
        CloseInventoryOpeningCommand cmd = new CloseInventoryOpeningCommand();
        cmd.setOperatorId(operatorId);
        CloseInventoryOpeningCommand.CloseInventoryOpeningItemCommand item =
            new CloseInventoryOpeningCommand.CloseInventoryOpeningItemCommand();
        item.setLocationId(locationId);
        cmd.setItem(item);
        locationDomainService.closeInventoryOpening(cmd);
    }
}
