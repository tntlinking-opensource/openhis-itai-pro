/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printbinding.command;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存租户级场景绑定命令（TC-PRT-BND-SAVE-001）。
 */
@Getter
@Setter
public class SaveTenantBindingsCommand {

    /** 租户主键。 */
    private Long tenantId;

    /** bindingCode → 模板 busNo 全量映射。 */
    private Map<String, String> bindingCodeToBusNo;
}
