/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.access;

import com.openhis.chs.domain.medicalinfo.command.QuerySetl5203Command;
import com.openhis.chs.domain.medicalinfo.result.Setl5203Result;

/**
 * 【5203】结算信息查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 5203 查询结算详情 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.medicalinfo.invoker.Med5203Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceMedicalInfoQueryAppService}、Extension</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P11</p>
 */
public interface Med5203QueryInvoker {

    /**
     * 调用平台 5203 查询结算详情。
     *
     * @param command 结算信息查询命令
     * @return 结算信息查询结果
     */
    Setl5203Result invoke(QuerySetl5203Command command);
}
