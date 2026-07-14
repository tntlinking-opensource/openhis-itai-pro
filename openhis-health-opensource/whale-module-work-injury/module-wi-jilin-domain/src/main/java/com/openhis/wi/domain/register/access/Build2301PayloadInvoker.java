/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.register.access;

import com.openhis.wi.domain.register.command.Build2301PayloadCommand;

import java.util.Map;

/** 【2301】审批上传 input 组装出站（bridge 实现，Extension / UI 预览共用）。 */
public interface Build2301PayloadInvoker {

    /**
     * 按就诊与表单上下文组装 2301 Win input Map（不发 Win）。
     *
     * @param command encounterId 必填；其余为 2301 表单字段
     * @return Win 业务体 input
     */
    Map<String, Object> invoke(Build2301PayloadCommand command);
}
