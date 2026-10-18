/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.invoke.result;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博思 HTTP 调用解析结果。
 */
@Data
public class BossInvokeResult {

    /** 博思 result 码（S0000 成功）。 */
    private String resultCode;

    /** 原始 message 节点（可能为 base64）。 */
    private String rawMessage;

    /** 解码后的业务 JSON。 */
    private JSONObject businessJson;

    /** 电子票据号码。 */
    private String billNo;

    /** 电子票据代码。 */
    private String billBatchCode;

    /** 校验码。 */
    private String random;

    /** 二维码。 */
    private String billQrCode;

    /** 图片 URL。 */
    private String pictureUrl;

    /** 外网图片 URL。 */
    private String pictureNetUrl;

    /** 红票号码（冲红响应 eScarletBillNo）。 */
    private String eScarletBillNo;

    /** 红票批次号（冲红响应 eScarletBillBatchCode）。 */
    private String eScarletBillBatchCode;

    /** 红票校验码（冲红响应 eScarletRandom）。 */
    private String eScarletRandom;

    /** 开具时间。 */
    private LocalDateTime billCreateTime;

    /**
     * 是否博思成功响应。
     *
     * @return true 表示 S0000
     */
    public boolean isSuccess() {
        return "S0000".equals(resultCode);
    }
}
