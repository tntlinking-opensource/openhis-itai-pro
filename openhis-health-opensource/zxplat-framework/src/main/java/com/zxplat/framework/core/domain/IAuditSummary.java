/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain;

import com.zxplat.framework.core.infrastructure.auditing.OperationType;

/**
 * 实体可提供审计摘要，替代完整序列化以收窄审计内容。
 * 若返回非 null，将写入审计的 newState；返回 null 时仍使用完整实体序列化。
 */
public interface IAuditSummary {

    /**
     * @param operationType 操作类型
     * @return 摘要 JSON 或简短描述；null 表示使用默认完整快照
     */
    String getAuditSummary(OperationType operationType);
}
