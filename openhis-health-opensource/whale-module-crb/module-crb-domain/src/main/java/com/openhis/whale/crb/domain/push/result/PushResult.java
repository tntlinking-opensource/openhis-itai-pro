/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push.result;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 国家前置 receive API 调用结果摘要。
 */
@Getter
@Setter
@Accessors(chain = true)
public class PushResult {

    /** 前置是否受理成功。 */
    private boolean success;

    /** 前置返回的业务 id。 */
    private String remoteId;

    /** 错误码（失败时）。 */
    private String errorCode;

    /** 错误名称（失败时）。 */
    private String errorName;

    /** 错误描述（失败时）。 */
    private String description;
}
