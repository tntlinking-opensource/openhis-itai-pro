/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment;

import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 产品调价读侧 AOP：凡列表 Query 默认排除「中药自动调价」审计单。
 *
 * <p>切 {@code PriceAdjustmentQueryService} 单据分页与已生效明细分页读方法；
 * 规则在 {@link HerbalPriceAdjustmentSupport}。覆盖调价工作站、财务审批并集调价侧、
 * 调价明细等所有经该 QS 的列表页。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class HerbalPriceAdjustmentAspect {

    /** 产品排除原因注入。 */
    private final HerbalPriceAdjustmentSupport priceAdjustmentSupport;

    /**
     * 单据列表三件套：count / 分页单号 / 按单号加载主行（与财务审批 Loader 共用）。
     *
     * @param joinPoint 切入点
     * @return 核心返回值
     * @throws Throwable 核心异常
     */
    @Around("execution(* com.zxplat.domain.custom.priceadjustment.service"
            + ".PriceAdjustmentQueryService.countDistinctBusNo(..))"
            + " || execution(* com.zxplat.domain.custom.priceadjustment.service"
            + ".PriceAdjustmentQueryService.listDistinctBusNoPage(..))"
            + " || execution(* com.zxplat.domain.custom.priceadjustment.service"
            + ".PriceAdjustmentQueryService.listLinesByBusNosWithFilter(..))")
    public Object aroundDocumentListReads(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        // Pre：首参为 DocumentListQuery 时注入排除「中药自动调价」
        if (args != null && args.length > 0 && args[0] instanceof PriceAdjustmentDocumentListQuery) {
            this.priceAdjustmentSupport.applyAutoMarkupExclude(
                (PriceAdjustmentDocumentListQuery) args[0]);
        }
        return joinPoint.proceed(args);
    }

    /**
     * 调价明细已生效行：count / 分页（IF-PRC-D-02）。
     *
     * @param joinPoint 切入点
     * @return 核心返回值
     * @throws Throwable 核心异常
     */
    @Around("execution(* com.zxplat.domain.custom.priceadjustment.service"
            + ".PriceAdjustmentQueryService.countApprovedLines(..))"
            + " || execution(* com.zxplat.domain.custom.priceadjustment.service"
            + ".PriceAdjustmentQueryService.pageApprovedLines(..))")
    public Object aroundApprovedLineListReads(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        // Pre：首参为 ApprovedLineListQuery 时注入排除「中药自动调价」
        if (args != null && args.length > 0 && args[0] instanceof PriceAdjustmentApprovedLineListQuery) {
            this.priceAdjustmentSupport.applyAutoMarkupExclude(
                (PriceAdjustmentApprovedLineListQuery) args[0]);
        }
        return joinPoint.proceed(args);
    }
}
