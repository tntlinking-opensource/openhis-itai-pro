/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossPayloadMetadataAccess;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 博思报文元数据解析（issueKind / busType / serviceId），供 application 单据初始化使用。
 */
@Component
@RequiredArgsConstructor
public class BossPayloadMetadataSupport implements BossPayloadMetadataAccess {

    private final BossPayloadBuilder payloadBuilder;

    @Override
    public String resolveIssueKind(String invoiceTypeCode) {
        return payloadBuilder.resolveIssueKind(invoiceTypeCode);
    }

    @Override
    public String resolveBusType(String issueKind) {
        return payloadBuilder.resolveBusType(issueKind);
    }

    @Override
    public String resolveServiceId(String issueKind) {
        return payloadBuilder.resolveServiceId(issueKind);
    }
}
