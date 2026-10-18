/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方撤销结果。
 */
@Data
@Accessors(chain = true)
public class ElepRevokeExtensionOut {

    /** 撤销是否成功。 */
    private boolean success;

    /** 失败或提示信息。 */
    private String message;
}
