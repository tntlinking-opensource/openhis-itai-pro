/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.outbound;

import com.zxplat.plugin.pacs.bridge.adapter.config.PacsAdapterProperties;
import com.zxplat.plugin.pacs.bridge.adapter.mapping.InMemoryPacsItemMapRepository;
import lombok.RequiredArgsConstructor;

/**
 * 国基 Mock 外发客户端（无真实 HTTP）。
 */
@RequiredArgsConstructor
public class MockPacsOutboundClient implements PacsOutboundPort {

    private final PacsAdapterProperties properties;
    private final InMemoryPacsItemMapRepository itemMapRepository;

    @Override
    public PacsOutboundResult sendApply(PacsSendApplyCommand command) {
        if (command == null || command.getWorkItemId() == null) {
            return PacsOutboundResult.fail("workItemId 不能为空");
        }
        String external = itemMapRepository.findExternalCode(command.getActivityId()).orElse("MOCK-" + command.getActivityId());
        String payload = "mock-sendApply workItemId=" + command.getWorkItemId() + " externalCode=" + external;
        if (!properties.isMockOutboundSuccess()) {
            return PacsOutboundResult.fail("Mock 外发失败（mockOutboundSuccess=false）");
        }
        return PacsOutboundResult.ok(payload);
    }

    @Override
    public PacsOutboundResult notifyReportPath(PacsNotifyReportPathCommand command) {
        if (command == null || command.getWorkItemId() == null) {
            return PacsOutboundResult.fail("workItemId 不能为空");
        }
        if (command.getPdfStorageKey() == null || command.getPdfStorageKey().isBlank()) {
            return PacsOutboundResult.fail("pdfStorageKey 不能为空");
        }
        String payload = "mock-reportPath workItemId=" + command.getWorkItemId()
            + " reportId=" + command.getReportId()
            + " accession=" + command.getAccessionNumber()
            + " pdfKey=" + command.getPdfStorageKey();
        if (!properties.isMockOutboundSuccess()) {
            return PacsOutboundResult.fail("Mock 报告路径回传失败（mockOutboundSuccess=false）");
        }
        return PacsOutboundResult.ok(payload);
    }
}
