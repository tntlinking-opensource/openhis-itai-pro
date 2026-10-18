/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.reconcile.parser;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.BossReconcileTotalResult;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 财务日结核对（{@code checkTotalData*}）业务 JSON 解析。
 *
 * <p>汇总全分页总笔数/总金额及当前页笔数，字段名兼容多版博思响应。</p>
 */
@Component
public class ReconcileTotalOutputParser {

    /**
     * 解析日结核对汇总响应。
     *
     * @param invokeResult bridge 解码后的调用结果；{@code null} 返回空统计对象
     * @return 核对汇总 DTO
     */
    public BossReconcileTotalResult parse(BossInvokeResult invokeResult) {
        BossReconcileTotalResult stats = new BossReconcileTotalResult();
        if (invokeResult == null) {
            return stats;
        }
        stats.setSuccess(invokeResult.isSuccess());
        stats.setResultCode(invokeResult.getResultCode());
        stats.setRawMessage(invokeResult.getRawMessage());
        JSONObject business = invokeResult.getBusinessJson();
        if (business != null) {
            stats.setTotalCount(readLong(business, "allPageTotalCount", "totalCount", "totalNum", "allPageTotalNum"));
            stats.setTotalAmount(readDecimal(business, "allPageTotalAmt", "totalAmt", "totalAmount", "allPageTotalAmount"));
            stats.setPageCount(readLong(business, "count", "pageCount", "totalCount"));
        }
        return stats;
    }

    /**
     * 按候选键顺序读取 long；均不存在返回 0。
     *
     * @param json 业务 JSON
     * @param keys 候选键名
     * @return 整型值
     */
    private long readLong(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return json.getLongValue(key);
            }
        }
        return 0L;
    }

    /**
     * 按候选键顺序读取金额；均不存在返回 {@link BigDecimal#ZERO}。
     *
     * @param json 业务 JSON
     * @param keys 候选键名
     * @return 金额
     */
    private BigDecimal readDecimal(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return json.getBigDecimal(key);
            }
        }
        return BigDecimal.ZERO;
    }
}
