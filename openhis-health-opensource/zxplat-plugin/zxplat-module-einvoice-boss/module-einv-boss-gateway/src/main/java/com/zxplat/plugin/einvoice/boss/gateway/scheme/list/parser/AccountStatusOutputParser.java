/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.list.parser;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.domain.list.result.BossAccountStatusResult;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 入账状态查询（{@code getEBillAccountStatus}）业务 JSON 解析。
 *
 * <p>将 {@link BossInvokeResult} 映射为 {@link BossAccountStatusResult}；
 * 博思多版本响应字段名不一致（accountStatus/accStatus/state 等），按候选键取首个非空。</p>
 */
@Component
public class AccountStatusOutputParser {

    /**
     * 解析入账状态响应。
     *
     * @param invokeResult bridge 解码后的调用结果；{@code null} 返回空结果对象
     * @return 入账状态 DTO（含 success/resultCode/rawMessage）
     */
    public BossAccountStatusResult parse(BossInvokeResult invokeResult) {
        BossAccountStatusResult status = new BossAccountStatusResult();
        if (invokeResult == null) {
            return status;
        }
        status.setSuccess(invokeResult.isSuccess());
        status.setResultCode(invokeResult.getResultCode());
        status.setRawMessage(invokeResult.getRawMessage());
        JSONObject business = invokeResult.getBusinessJson();
        if (business != null) {
            status.setAccountStatus(readText(business, "accountStatus", "accStatus", "state"));
            status.setAccountDate(readText(business, "accountDate", "accDate", "accountTime"));
        }
        return status;
    }

    /**
     * 按候选键顺序读取首个非空字符串（博思多版本字段命名差异）。
     *
     * @param json 业务 JSON 对象
     * @param keys 候选键名（从左到右优先）
     * @return 去空白后的值；均不存在返回 {@code null}
     */
    private String readText(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return StringUtils.trimToNull(json.getString(key));
            }
        }
        return null;
    }
}
