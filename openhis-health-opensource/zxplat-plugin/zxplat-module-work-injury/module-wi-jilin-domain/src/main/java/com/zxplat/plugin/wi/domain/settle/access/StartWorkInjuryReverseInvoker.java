/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.access;

import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.zxplat.plugin.wi.domain.settle.command.StartWorkInjuryReverseCommand;

/** 2208 退费 invoke 首步 prepare 端口。 */
public interface StartWorkInjuryReverseInvoker {

    PrepareWorkInjuryInvokeResponse invoke(StartWorkInjuryReverseCommand command);
}
