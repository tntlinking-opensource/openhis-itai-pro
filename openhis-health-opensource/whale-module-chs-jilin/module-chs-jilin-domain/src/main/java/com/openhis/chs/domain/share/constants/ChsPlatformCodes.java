/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.constants;

/**
 * 国家医保平台 HTTP 响应头/报文级的通用结果码。
 *
 * <p>与 {@link ChsInfNo}（交易编号 infno）不同：本类描述<b>单次调用是否成功</b>，
 * 出现在几乎所有交易的 {@code infcode} 字段。</p>
 */
public final class ChsPlatformCodes {

    /**
     * 平台调用成功（{@code infcode}）。
     *
     * <p>仅表示网关层成功；业务数据是否落库、结算是否完成需结合各交易 output 再判。</p>
     */
    public static final String INFCODE_SUCCESS = "0";

    private ChsPlatformCodes() {
    }
}
