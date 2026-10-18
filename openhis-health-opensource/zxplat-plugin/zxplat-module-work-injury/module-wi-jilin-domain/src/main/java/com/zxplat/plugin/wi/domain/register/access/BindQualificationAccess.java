/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.register.access;

import com.zxplat.plugin.wi.domain.register.command.BindQualificationCommand;

/** 9102 后绑定 qualification 端口。 */
public interface BindQualificationAccess {

    void bind(BindQualificationCommand command);
}
