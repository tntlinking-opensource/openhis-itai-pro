package com.openhis.chs.application.config;

/**
 * 医保 OAuth 账号（主账号 + 备用账号轮换签到）。
 */
public record ContractCredential(String username, String password) {
}
