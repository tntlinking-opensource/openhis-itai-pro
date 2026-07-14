/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.domain;

/**
 * 标记实体不写入通用审计表（whale_audit_logs）。
 * 适用于已有专用表或无需重复审计的实体，如 SecurityLog（已有 whale_security_logs）。
 */
public interface IExcludeFromAudit {
}
