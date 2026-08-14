/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.volume.mapper;

import com.openhis.health.application.mrms.caserecord.volume.dto.VolumeCatalogDto;
import com.openhis.health.application.mrms.caserecord.volume.dto.VolumeItemDto;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 卷宗目录 DTO 映射（volume IF）。
 */
@Component
public class VolumeMapper {

    /**
     * 组装卷宗目录：首页虚拟项 + EMR 文书项。
     *
     * @param record        病案主档
     * @param emrDocuments  EMR 文书快照列表
     * @return 卷宗目录 DTO
     */
    public VolumeCatalogDto toCatalog(CaseRecord record, List<EmrDocumentQueryExtensionOut> emrDocuments) {
        VolumeCatalogDto catalog = new VolumeCatalogDto();
        catalog.setCaseRecordId(record.getId());
        catalog.setMedcasno(record.getMedcasno());
        catalog.setHomepageItem(buildHomepageItem(record));
        catalog.setDocuments(toEmrItems(emrDocuments));
        return catalog;
    }

    /**
     * 构建首页虚拟卷宗项（固定标题，可打印）。
     *
     * @param record 病案主档
     * @return 首页卷宗项
     */
    public VolumeItemDto buildHomepageItem(CaseRecord record) {
        VolumeItemDto item = new VolumeItemDto();
        item.setItemType("homepage");
        item.setRefId(record.getId());
        item.setTitle("住院病案首页");
        item.setPrintable(true);
        return item;
    }

    /**
     * EMR 文书快照转卷宗项列表。
     *
     * @param emrDocuments 文书快照，可为 null
     * @return 卷宗项列表
     */
    public List<VolumeItemDto> toEmrItems(List<EmrDocumentQueryExtensionOut> emrDocuments) {
        List<VolumeItemDto> items = new ArrayList<>();
        if (emrDocuments == null) {
            return items;
        }
        // 逐份 EMR 文书转卷宗目录项
        for (EmrDocumentQueryExtensionOut row : emrDocuments) {
            VolumeItemDto item = new VolumeItemDto();
            item.setItemType("emr_document");
            item.setRefId(row.getDocumentId());
            item.setTitle(row.getTitle());
            item.setDocumentTypeCode(row.getDocumentTypeCode());
            item.setDocumentTypeName(row.getDocumentTypeName());
            item.setCreatedAt(row.getCreatedAt());
            item.setPrintable(row.isPrintable());
            items.add(item);
        }
        return items;
    }
}
