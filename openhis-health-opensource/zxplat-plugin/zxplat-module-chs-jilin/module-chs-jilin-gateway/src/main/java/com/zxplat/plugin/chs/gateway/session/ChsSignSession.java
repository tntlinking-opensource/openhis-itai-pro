/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.session;

/**
 * 当日有效签到会话（sign_no + 9001 签到操作员编号） */
public record ChsSignSession(String signNo, String signOpterNo) {
}
