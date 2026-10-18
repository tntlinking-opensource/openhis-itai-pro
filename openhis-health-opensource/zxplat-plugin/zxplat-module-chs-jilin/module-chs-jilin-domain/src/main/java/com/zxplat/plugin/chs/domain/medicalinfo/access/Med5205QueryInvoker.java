/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.access;

import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspDrug5205Command;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;

/**
 * 【5205】慢特病用药记录查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 5205 查询慢特病用药记录 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.medicalinfo.invoker.Med5205Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceMedicalInfoQueryAppService}、Extension</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P11</p>
 */
public interface Med5205QueryInvoker {

    /**
     * 调用平台 5205 查询慢特病用药记录。
     *
     * @param command 慢特病用药记录查询命令
     * @return 用药记录查询结果
     */
    MedicalInfoQueryResult invoke(QueryOpspDrug5205Command command);
}
