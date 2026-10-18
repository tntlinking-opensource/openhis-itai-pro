/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.config;

/**
 * 医保 OAuth 账号（主账号 + 备用账号轮换签到） */
public record ContractCredential(String username, String password) {
}
