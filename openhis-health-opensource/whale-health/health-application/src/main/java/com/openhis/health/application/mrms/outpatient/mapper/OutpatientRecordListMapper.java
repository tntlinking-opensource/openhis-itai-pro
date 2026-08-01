/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatient.mapper;

import com.openhis.health.application.mrms.outpatient.dto.OutpatientRecordListItemDto;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentItemExtensionOut;
import org.springframework.stereotype.Component;

/**
 * 门诊病历查询列表行映射。
 */
@Component
public class OutpatientRecordListMapper {

    /**
     * Extension 行转页面 DTO（不含 HIS 姓名/科室 enrich）。
     *
     * @param row Extension 摘要行
     * @return 列表行 DTO；{@code row} 为空时返回 null
     */
    public OutpatientRecordListItemDto toListItem(EmrOutpatientDocumentItemExtensionOut row) {
        if (row == null) {
            return null;
        }
        OutpatientRecordListItemDto dto = new OutpatientRecordListItemDto();
        dto.setDocumentId(row.getDocumentId());
        dto.setPatientId(parseLongId(row.getPatientId()));
        dto.setEncounterId(parseLongId(row.getEncounterId()));
        dto.setTitle(row.getTitle());
        dto.setDocumentTypeCode(row.getDocumentTypeCode());
        dto.setDocumentTypeName(row.getDocumentTypeName());
        dto.setStatusCode(row.getStatusCode());
        dto.setUpdatedAt(row.getUpdatedAt());
        dto.setPrintable(row.isPrintable());
        return dto;
    }

    /**
     * 将 EMR 存库字符串主键解析为 Long。
     *
     * @param raw 字符串主键
     * @return Long；空白或非法时 null
     */
    private static Long parseLongId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
