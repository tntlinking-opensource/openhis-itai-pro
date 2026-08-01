/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.access;

import com.openhis.chs.domain.audit.command.DetailAuditIpGateCommand;
import com.openhis.chs.domain.audit.command.DetailAuditOpGateCommand;
import com.openhis.chs.domain.audit.result.DetailAuditGateResult;

/**
 * 明细审核主路径门禁出站端口（W2：预结算/签发前 3101）。
 *
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.audit.invoker.DetailAuditGateInvokerImpl}</p>
 * <p><b>调用方：</b>{@code InsuranceDetailAuditGateAppService}</p>
 */
public interface DetailAuditGateInvoker {

    /**
     * 门诊场景：装载上下文 → 解析/注册 mdtrt → 3101。
     *
     * @param command 门诊门禁命令
     * @return 门禁结果（含违规列表）
     */
    DetailAuditGateResult analyzeOp(DetailAuditOpGateCommand command);

    /**
     * 住院场景：装载上下文 → 解析/注册 mdtrt → 3101。
     *
     * @param command 住院门禁命令
     * @return 门禁结果
     */
    DetailAuditGateResult analyzeIp(DetailAuditIpGateCommand command);
}
