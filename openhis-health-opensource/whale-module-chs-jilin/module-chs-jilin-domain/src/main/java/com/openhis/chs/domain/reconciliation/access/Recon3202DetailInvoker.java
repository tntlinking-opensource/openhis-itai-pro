/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.access;

import com.openhis.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.openhis.chs.domain.reconciliation.result.ReconDetail3202Result;

/**
 * 【3202】明细对账出站端口。
 *
 * <p><b>职责：</b>定义医保平台 9101→3202→9102 明细对账全流程编排 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.reconciliation.workflow.Recon3202Workflow}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3202DetailInvoker {

    /**
     * 执行明细对账全流程并落本地明细账。
     *
     * @param command 明细对账命令
     * @return 对账结果与本地记录 ID
     */
    ReconDetail3202Result invoke(ReconDetail3202Command command);
}
