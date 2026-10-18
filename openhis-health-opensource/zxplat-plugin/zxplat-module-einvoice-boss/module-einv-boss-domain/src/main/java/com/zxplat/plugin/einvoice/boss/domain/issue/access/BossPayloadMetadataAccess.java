/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.access;

/** 博思报文元数据解析端口（issueKind / busType / serviceId）。 */
public interface BossPayloadMetadataAccess {

    String resolveIssueKind(String invoiceTypeCode);

    String resolveBusType(String issueKind);

    String resolveServiceId(String issueKind);
}
