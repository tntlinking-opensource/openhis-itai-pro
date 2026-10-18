/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain;

/**
 * 标记实体不写入通用审计表（zxplat_audit_logs）。
 * 适用于已有专用表或无需重复审计的实体，如 SecurityLog（已有 zxplat_security_logs）。
 */
public interface IExcludeFromAudit {
}
