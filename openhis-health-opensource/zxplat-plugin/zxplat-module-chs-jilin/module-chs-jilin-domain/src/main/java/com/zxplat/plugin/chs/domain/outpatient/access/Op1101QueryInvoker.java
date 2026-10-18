/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.access;

import com.zxplat.plugin.chs.domain.outpatient.command.YbPersonQueryCommand;
import com.zxplat.plugin.chs.domain.outpatient.result.YbPersonInfoResult;

/** 【1101】人员信息获取出站（bridge {@code Op1101Invoker} 实现；application / Extension 共用） */
public interface Op1101QueryInvoker {

    /**
     * 调用平台 1101 获取人员基本信息与参保信息。
     *
     * @param command 人员查询命令（含凭证类型与凭证号）
     * @return 人员信息查询结果
     */
    YbPersonInfoResult invoke(YbPersonQueryCommand command);
}
