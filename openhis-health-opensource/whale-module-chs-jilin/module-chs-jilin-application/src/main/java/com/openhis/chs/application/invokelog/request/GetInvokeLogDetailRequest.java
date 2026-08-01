/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.invokelog.request;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 医保调用日志详情请求（POST /api/app/insuranceInvokeLog/getInvokeLogDetail）。 */
@Getter
@Setter
@Accessors(chain = true)
public class GetInvokeLogDetailRequest {

    /** 日志主键 */
    private Long id;
}
