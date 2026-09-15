/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.AgreeInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.CreateInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.UpdateInboundReceiptRequest;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.AgreeInboundReceiptResponse;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 产品药库进货 AOP：中草药同批号进价门禁与同意后批号售价 ×1.25。
 *
 * <p>切 health {@code InventoryWarehouseInboundReceiptAppService} 公开方法；规则在
 * {@link HerbalInboundReceiptSupport}。包路径与 health
 * {@code application.inventory.warehouse.inboundreceipt} 同构。</p>
 *
 * <p>挂接方式参考院点 MedSync 类 AOP；本类已落在 health 产品包。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class HerbalInboundReceiptAspect {

    private final HerbalInboundReceiptSupport inboundReceiptSupport;

    /**
     * IF-WHS-IN-04：新建进货保存前拦截中草药同批号进价变更。
     *
     * @param joinPoint 切入点；首参为 {@link CreateInboundReceiptRequest}
     * @return 核心新建响应
     * @throws Throwable 门禁拒绝（{@code WHS_IN_001}）或核心异常
     */
    @Around("execution(* com.openhis.health.application.inventory.warehouse.inboundreceipt.service"
            + ".InventoryWarehouseInboundReceiptAppService.createInboundReceipt(..))")
    public Object aroundCreateInboundReceipt(ProceedingJoinPoint joinPoint) throws Throwable {
        CreateInboundReceiptRequest request = (CreateInboundReceiptRequest) joinPoint.getArgs()[0];
        // Gate：请求为空时交由核心抛错；有明细则先跑中草药同批号进价门禁
        if (request != null) {
            inboundReceiptSupport.assertHerbalLotPurchasePriceUnchanged(request.getLines());
        }
        return joinPoint.proceed();
    }

    /**
     * IF-WHS-IN-05：更新进货保存前拦截中草药同批号进价变更。
     *
     * @param joinPoint 切入点；首参为 {@link UpdateInboundReceiptRequest}
     * @return 核心更新响应
     * @throws Throwable 门禁拒绝（{@code WHS_IN_001}）或核心异常
     */
    @Around("execution(* com.openhis.health.application.inventory.warehouse.inboundreceipt.service"
            + ".InventoryWarehouseInboundReceiptAppService.updateInboundReceipt(..))")
    public Object aroundUpdateInboundReceipt(ProceedingJoinPoint joinPoint) throws Throwable {
        UpdateInboundReceiptRequest request = (UpdateInboundReceiptRequest) joinPoint.getArgs()[0];
        // Gate：请求为空时交由核心抛错；有明细则先跑中草药同批号进价门禁
        if (request != null) {
            inboundReceiptSupport.assertHerbalLotPurchasePriceUnchanged(request.getLines());
        }
        return joinPoint.proceed();
    }

    /**
     * IF-WHS-IN-08：同意入库成功后，将中草药批号售价调整为进价 ×1.25 并写审计单。
     *
     * <p>加价与审计在 {@code proceed()} 之后、同请求线程内执行，与核心同意同事务；失败则整次同意回滚。</p>
     *
     * @param joinPoint 切入点；首参为 {@link AgreeInboundReceiptRequest}
     * @return 核心同意响应
     * @throws Throwable 核心或产品加价/审计异常
     */
    @Around("execution(* com.openhis.health.application.inventory.warehouse.inboundreceipt.service"
            + ".InventoryWarehouseInboundReceiptAppService.agreeInboundReceipt(..))")
    public Object aroundAgreeInboundReceipt(ProceedingJoinPoint joinPoint) throws Throwable {
        AgreeInboundReceiptRequest request = (AgreeInboundReceiptRequest) joinPoint.getArgs()[0];
        AgreeInboundReceiptResponse response = (AgreeInboundReceiptResponse) joinPoint.proceed();
        // Asm：核心已写 LOT_NUMBER_COST=LOT_NUMBER_PRICE=进价后，本切面只改批号售价并落审计
        inboundReceiptSupport.applyHerbalLotSaleMarkupAfterAgree(request);
        return response;
    }
}
