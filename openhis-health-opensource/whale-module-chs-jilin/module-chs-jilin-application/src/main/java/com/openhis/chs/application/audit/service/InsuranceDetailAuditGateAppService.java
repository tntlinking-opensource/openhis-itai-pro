/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.service;

import com.openhis.chs.domain.audit.access.Audit3103Invoker;
import com.openhis.chs.domain.audit.access.DetailAuditGateInvoker;
import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;
import com.openhis.chs.domain.audit.command.DetailAuditIpGateCommand;
import com.openhis.chs.domain.audit.command.DetailAuditOpGateCommand;
import com.openhis.chs.domain.audit.result.DetailAuditGateResult;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 明细审核主路径门禁应用服务（{@code POST /api/app/insuranceDetailAuditGate/*}）。
 *
 * <p>供收费/医生站嵌入对话框调用：3101 门禁 + 3103 反馈。与试调页 AppService 分离。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceDetailAuditGateAppService {

    private final DetailAuditGateInvoker detailAuditGateInvoker;
    private final Audit3103Invoker audit3103Invoker;

    /**
     * 门诊场景事前分析门禁（trig 2/7 等）。
     *
     * @param request 门诊门禁命令
     * @return 门禁结果
     */
    public DetailAuditGateResult analyzeOpGate(DetailAuditOpGateCommand request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "门诊审核门禁请求不能为空");
        }
        // Pre/Cmd：装载 + mdtrt + 3101 均在 bridge Invoker
        return detailAuditGateInvoker.analyzeOp(request);
    }

    /**
     * 住院场景事前分析门禁（trig 9）。
     *
     * @param request 住院门禁命令
     * @return 门禁结果
     */
    public DetailAuditGateResult analyzeIpGate(DetailAuditIpGateCommand request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "住院审核门禁请求不能为空");
        }
        // Pre/Cmd：装载 + mdtrt + 3101
        return detailAuditGateInvoker.analyzeIp(request);
    }

    /**
     * 事前提醒反馈（主路径对话框确认后）。
     *
     * @param request 3103 命令（warnRsltId=jrdId）
     */
    public void feedback3103(AuditFeedback3103Command request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "3103 请求不能为空");
        }
        // Pre：校验在 Invoker
        // Cmd：出站反馈并落库
        audit3103Invoker.invoke(request);
        // Asm：无业务输出
    }
}
