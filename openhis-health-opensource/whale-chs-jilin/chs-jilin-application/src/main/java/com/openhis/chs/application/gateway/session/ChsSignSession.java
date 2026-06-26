package com.openhis.chs.application.gateway.session;

/**
 * 当日有效签到会话（sign_no + 9001 签到操作员编号）。
 */
public record ChsSignSession(String signNo, String signOpterNo) {
}
