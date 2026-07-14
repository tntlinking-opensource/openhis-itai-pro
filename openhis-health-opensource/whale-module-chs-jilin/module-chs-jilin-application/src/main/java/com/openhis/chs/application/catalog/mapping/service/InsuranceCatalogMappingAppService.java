/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.mapping.service;

import com.openhis.chs.domain.catalog.access.CatalogMappingUploadAccess;
import com.openhis.chs.application.catalog.mapping.support.MedicationCatalogMappingSuggestWorkflow;
import com.openhis.chs.domain.catalog.command.RevokeActivityCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeDeviceCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeMedicationCatalog3302Command;
import com.openhis.chs.domain.catalog.command.SuggestMedicationCatalogMappingCommand;
import com.openhis.chs.domain.catalog.command.UploadActivityCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadDeviceCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadMedicationCatalog3301Command;
import com.openhis.chs.domain.catalog.result.Catalog3301UploadResult;
import com.openhis.chs.domain.catalog.result.Catalog3302RevokeResult;
import com.openhis.chs.domain.catalog.result.MedicationCatalogMappingSuggestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 院内对码上传页 UI 应用服务（{@code POST /api/app/insuranceCatalogMapping/*}）。
 * <p>职责：药品/诊疗/耗材 3301 上传与 3302 撤销、药品国家目录编码智能推荐。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceCatalogMappingAppService {

    private final MedicationCatalogMappingSuggestWorkflow medicationCatalogMappingSuggestWorkflow;
    private final CatalogMappingUploadAccess catalogMappingUploadAccess;

    /** 按院内药品信息推荐国家医保目录编码候选（可触发 1312 补库）。 */
    public MedicationCatalogMappingSuggestResult suggestMedicationCatalogMapping(
            SuggestMedicationCatalogMappingCommand request) {
        return medicationCatalogMappingSuggestWorkflow.suggest(request);
    }

    /** 单药品 3301 上传，成功后回写 health {@code yb_match_flag}。 */
    public Catalog3301UploadResult uploadMedicationCatalog3301(UploadMedicationCatalog3301Command command) {
        return catalogMappingUploadAccess.uploadMedication(command);
    }

    /** 单诊疗项目 3301 上传，成功后回写 health {@code yb_match_flag}。 */
    public Catalog3301UploadResult uploadActivityCatalog3301(UploadActivityCatalog3301Command command) {
        return catalogMappingUploadAccess.uploadActivity(command);
    }

    /** 单耗材 3301 上传，成功后回写 health {@code yb_match_flag}。 */
    public Catalog3301UploadResult uploadDeviceCatalog3301(UploadDeviceCatalog3301Command command) {
        return catalogMappingUploadAccess.uploadDevice(command);
    }

    /** 单药品 3302 撤销，成功后清除 health {@code yb_match_flag}。 */
    public Catalog3302RevokeResult revokeMedicationCatalog3302(RevokeMedicationCatalog3302Command command) {
        return catalogMappingUploadAccess.revokeMedication(command);
    }

    /** 单诊疗项目 3302 撤销，成功后清除 health {@code yb_match_flag}。 */
    public Catalog3302RevokeResult revokeActivityCatalog3302(RevokeActivityCatalog3302Command command) {
        return catalogMappingUploadAccess.revokeActivity(command);
    }

    /** 单耗材 3302 撤销，成功后清除 health {@code yb_match_flag}。 */
    public Catalog3302RevokeResult revokeDeviceCatalog3302(RevokeDeviceCatalog3302Command command) {
        return catalogMappingUploadAccess.revokeDevice(command);
    }
}
