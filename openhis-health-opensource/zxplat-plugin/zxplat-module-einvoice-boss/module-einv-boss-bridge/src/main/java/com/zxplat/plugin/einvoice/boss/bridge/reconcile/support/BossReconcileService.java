/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.reconcile.support;

import com.zxplat.plugin.einvoice.boss.domain.reconcile.access.BossReconcileQueryAccess;
import com.zxplat.plugin.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.zxplat.plugin.einvoice.boss.bridge.reconcile.invoker.EinvReconcileInvoker;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.BossReconcileTotalResult;
import com.zxplat.plugin.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.reconcile.assembler.ReconcilePayloadAssembler;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.reconcile.parser.ReconcileTotalOutputParser;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 博思财务日结核对出站服务（{@code checkTotalData*}）。
 *
 * <p>编排：{@link ReconcilePayloadAssembler} 组装 → {@link EinvReconcileInvoker} HTTP
 * → {@link ReconcileTotalOutputParser} 解析；每次调用写入 {@link BossInvokeLogAccess}。</p>
 */
@Service
@RequiredArgsConstructor
public class BossReconcileService implements BossReconcileQueryAccess {

    private static final DateTimeFormatter BOSS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EinvReconcileInvoker reconcileInvoker;
    private final ReconcilePayloadAssembler reconcilePayloadAssembler;
    private final ReconcileTotalOutputParser reconcileTotalOutputParser;
    private final EinvoiceBossGatewayProperties properties;
    private final BossInvokeLogAccess invokeLogWriter;

    /**
     * 向博思查询指定日期的分页核对汇总（总笔数/总金额）。
     *
     * @param tenantId 租户主键（写 invoke 日志）
     * @param checkDate 核对日期
     * @param dateMode 按业务日或开票日（{@link com.zxplat.plugin.einvoice.boss.domain.enums.ReconcileDateMode}）
     * @param issueKind 可选票种过滤（REG/AMB/IMP/COM）
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 博思汇总结果（含 success/resultCode/rawMessage）
     */
    @Override
    public BossReconcileTotalResult queryBossTotal(
            Long tenantId,
            LocalDate checkDate,
            String dateMode,
            String issueKind,
            int pageNo,
            int pageSize) {
        String serviceId = reconcilePayloadAssembler.resolveServiceId(dateMode);
        String requestJson = reconcilePayloadAssembler.assemble(
                checkDate,
                dateMode,
                issueKind,
                pageNo,
                pageSize,
                properties.getPlatform().getPlaceCode());
        String dateText = BOSS_DATE.format(checkDate);
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = reconcileInvoker.invoke(serviceId, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        invokeLogWriter.writeWithoutTask(
                tenantId,
                serviceId,
                dateText,
                invokeResult,
                durationMs,
                requestJson,
                invokeResult.getRawMessage());
        return reconcileTotalOutputParser.parse(invokeResult);
    }
}
