/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-PACS-REG-04 本地补登记响应。
 */
@Getter
@Setter
public class CreateLocalRegistrationResponse {

    /** 工单主键。 */
    private Long workItemId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 检查号。 */
    private String accessionNumber;

    /** 执行态枚举 code。 */
    private String statusEnum;
}
