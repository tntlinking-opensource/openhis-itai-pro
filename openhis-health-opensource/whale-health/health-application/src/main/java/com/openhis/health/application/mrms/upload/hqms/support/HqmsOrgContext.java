/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.hqms.support;

/**
 * HQMS 导出行机构上下文（A01 组织机构代码 / A02 机构名称）。
 *
 * @param orgCode 组织机构代码（优先 medinsId，其次医院 ybNo）
 * @param orgName 医疗机构名称
 */
public record HqmsOrgContext(String orgCode, String orgName) {
}
