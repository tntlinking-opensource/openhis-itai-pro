/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 明细审核主路径门禁结果。
 *
 * <p>{@code skipped=true} 表示未调平台（如待审明细全为自费账户）；有违规时 {@code analyzeResult.violations} 非空。
 * 前端 {@code gateHasViolations} 对 skipped 视为无违规、不弹窗。</p>
 */
@Data
@Accessors(chain = true)
public class DetailAuditGateResult {

    /** 是否跳过审核（未出站；含全自费跳过） */
    private boolean skipped;
    /** 跳过原因（如 {@link com.zxplat.plugin.chs.domain.share.constants.ChsDetailAuditConstants#SKIP_REASON_ALL_SELF_PAY}） */
    private String skipReason;
    /** 平台就诊 ID（解析或注册后） */
    private String mdtrtId;
    /** 触发场景回显 */
    private String trigScen;
    /** 契约 bus_no 回显 */
    private String contractBusNo;
    /** 3101 分析结果；skipped 时可为 null */
    private AuditAnalyzeResult analyzeResult;

    /** 是否存在违规提醒（需弹窗） */
    public boolean hasViolations() {
        if (analyzeResult == null || analyzeResult.getViolations() == null) {
            return false;
        }
        return !analyzeResult.getViolations().isEmpty();
    }
}
