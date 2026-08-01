/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 对照行软删命令（科室/检验/药品共用）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class SoftDeleteMapCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 对照主键。 */
    private Long id;
}
