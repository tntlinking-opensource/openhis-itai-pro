/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.access;

import com.openhis.chs.domain.catalog.command.RevokeActivityCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeDeviceCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeMedicationCatalog3302Command;
import com.openhis.chs.domain.catalog.command.UploadActivityCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadDeviceCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadMedicationCatalog3301Command;
import com.openhis.chs.domain.catalog.result.Catalog3301UploadResult;
import com.openhis.chs.domain.catalog.result.Catalog3302RevokeResult;

/**
 * 3301 上传 / 3302 撤销编排出站端口。
 *
 * <p><b>职责：</b>药品、诊疗、耗材三品类目录对照上传与撤销编排。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.catalog.workflow.CatalogMappingUploadWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogMappingAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P13</p>
 */
public interface CatalogMappingUploadAccess {

    /**
     * 3301 药品目录对照上传。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3301UploadResult uploadMedication(UploadMedicationCatalog3301Command command);

    /**
     * 3301 诊疗目录对照上传。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3301UploadResult uploadActivity(UploadActivityCatalog3301Command command);

    /**
     * 3301 耗材目录对照上传。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3301UploadResult uploadDevice(UploadDeviceCatalog3301Command command);

    /**
     * 3302 药品目录对照撤销。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3302RevokeResult revokeMedication(RevokeMedicationCatalog3302Command command);

    /**
     * 3302 诊疗目录对照撤销。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3302RevokeResult revokeActivity(RevokeActivityCatalog3302Command command);

    /**
     * 3302 耗材目录对照撤销。
     *
     * @param command 上传/撤销命令
     * @return 平台返回摘要
     */
    Catalog3302RevokeResult revokeDevice(RevokeDeviceCatalog3302Command command);
}
