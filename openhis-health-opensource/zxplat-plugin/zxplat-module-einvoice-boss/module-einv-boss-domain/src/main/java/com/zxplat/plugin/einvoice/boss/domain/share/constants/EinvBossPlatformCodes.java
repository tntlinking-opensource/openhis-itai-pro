/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.share.constants;

/**
 * 博思平台返回的业务码（报文 {@code result}/{@code message} 前缀，非 HIS 错误码）。
 *
 * <p>与 {@code EinvBossErrorCodes} 区分：后者是 ZXPlat 落库/API 码；本类是博思侧原文码。</p>
 */
public final class EinvBossPlatformCodes {

    /**
     * 单位开票数据已超过审核周期，须先上报/日结后才能继续开票。
     *
     * <p>属平台侧永久业务拒收，自动重试不会成功，应停扫表并进入补偿页人工处理。</p>
     */
    public static final String EYD_COM_0303 = "EYD-COM-0303";

    private EinvBossPlatformCodes() {
    }
}
