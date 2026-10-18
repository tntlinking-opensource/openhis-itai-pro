/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.auditing;

/**
 * 审计存储接口
 * 由 zxplat-framework audit 等模块实现，负责将审计信息持久化（如写入 zxplat_audit_logs 表）
 */
public interface IAuditingStore {

    /**
     * 保存一条审计日志
     * 实现方应使用独立事务（如 REQUIRES_NEW）或异步写入，避免影响主流程
     *
     * @param info 审计信息
     */
    void save(AuditLogInfo info);
}
