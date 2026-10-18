/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.access;

import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspApproval5302ACommand;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;

/**
 * 【5302A】人员门诊慢特病审批查询出站端口。
 *
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.medicalinfo.invoker.Med5302AInvoker}</p>
 */
public interface Med5302AQueryInvoker {

    /**
     * 调用平台 5302A 查询参保人门诊慢特病审批/备案信息。
     *
     * @param command psnNo 必填；medType 可选
     * @return 审批/备案行列表
     */
    MedicalInfoQueryResult invoke(QueryOpspApproval5302ACommand command);
}
