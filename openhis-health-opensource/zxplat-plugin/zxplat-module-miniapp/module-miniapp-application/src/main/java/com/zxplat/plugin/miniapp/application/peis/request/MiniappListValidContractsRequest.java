/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.peis.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序查询有效合同请求。
 */
@Getter
@Setter
public class MiniappListValidContractsRequest {

    /** 会话患者（可空，由 BFF 回填）。 */
    private Long patientId;
}
