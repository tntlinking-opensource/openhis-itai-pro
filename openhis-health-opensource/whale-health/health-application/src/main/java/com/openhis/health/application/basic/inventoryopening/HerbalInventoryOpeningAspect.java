/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.HerbalInboundReceiptSupport;
import com.openhis.health.application.basic.inventoryopening.request.AgreeInventoryOpeningRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 产品库存期初 AOP：同意后中草药批号售价 ×1.25（与进货同源规则）。
 *
 * <p>切 health {@code BasicInventoryOpeningAppService#agreeInventoryOpening}；加价实现复用
 * {@link HerbalInboundReceiptSupport}。包路径与 health
 * {@code application.basic.inventoryopening} 同构。</p>
 *
 * <p>核心期初同意只写 COST=PRICE=进价；本切面在 {@code proceed()} 之后同事务改 LOT_NUMBER_PRICE。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class HerbalInventoryOpeningAspect {

    private final HerbalInboundReceiptSupport inboundReceiptSupport;

    /**
     * IF-BAS-INV-OPN-08：同意期初成功后，将中草药批号售价调整为进价 ×1.25 并写审计单。
     *
     * @param joinPoint 切入点；首参为 {@link AgreeInventoryOpeningRequest}；核心方法返回 void
     * @return {@code null}（核心为 void）
     * @throws Throwable 核心或产品加价/审计异常
     */
    @Around("execution(* com.openhis.health.application.basic.inventoryopening.service"
            + ".BasicInventoryOpeningAppService.agreeInventoryOpening(..))")
    public Object aroundAgreeInventoryOpening(ProceedingJoinPoint joinPoint) throws Throwable {
        AgreeInventoryOpeningRequest request = (AgreeInventoryOpeningRequest) joinPoint.getArgs()[0];
        // Cmd：先跑核心同意（含批号 COST=PRICE=进价）
        Object result = joinPoint.proceed();
        // Asm：核心成功后，本切面只改中草药批号售价并落审计（失败则同事务回滚同意）
        if (request != null && request.getBusNo() != null) {
            inboundReceiptSupport.applyHerbalLotSaleMarkupAfterAgreeByBusNo(request.getBusNo());
        }
        return result;
    }
}
