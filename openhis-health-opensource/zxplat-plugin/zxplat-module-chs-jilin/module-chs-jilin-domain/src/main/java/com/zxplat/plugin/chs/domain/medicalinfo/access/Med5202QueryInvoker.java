/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.access;

import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryDise5202Command;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;

/**
 * 【5202】诊断信息查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 5202 查询就诊诊断信息 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.medicalinfo.invoker.Med5202Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceMedicalInfoQueryAppService}、Extension</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P11</p>
 */
public interface Med5202QueryInvoker {

    /**
     * 调用平台 5202 查询就诊诊断信息。
     *
     * @param command 诊断信息查询命令
     * @return 诊断信息查询结果
     */
    MedicalInfoQueryResult invoke(QueryDise5202Command command);
}
