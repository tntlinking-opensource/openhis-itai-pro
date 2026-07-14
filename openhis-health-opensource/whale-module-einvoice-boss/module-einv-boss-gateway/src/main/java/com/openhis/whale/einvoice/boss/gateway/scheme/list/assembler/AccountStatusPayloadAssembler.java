/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.list.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 入账状态查询（{@code getEBillAccountStatus}）业务 JSON 组装。
 */
@Component
public class AccountStatusPayloadAssembler {

    /**
     * 按蓝票批次号与票号组装入账状态查询请求体。
     *
     * @param billBatchCode 博思批次号
     * @param billNo 博思票号
     * @return 业务 JSON 明文字符串
     */
    public String assemble(String billBatchCode, String billNo) {
        JSONObject body = new JSONObject();
        body.put("billBatchCode", billBatchCode);
        body.put("billNo", billNo);
        return JSON.toJSONString(body);
    }
}
