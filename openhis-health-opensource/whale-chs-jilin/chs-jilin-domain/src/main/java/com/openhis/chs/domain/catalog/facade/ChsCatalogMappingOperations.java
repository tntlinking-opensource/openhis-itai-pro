package com.openhis.chs.domain.catalog.facade;

import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeResultDto;

/**
 * 医保目录对照（3301/3302）应用能力。
 */
public interface ChsCatalogMappingOperations {

    /**
     * 【3301】目录对照上传；失败行写入审计且不回写院内 yb_match_flag（由 bridge 编排）。
     */
    Catalog3301UploadResultDto uploadCatalog3301(UploadCatalog3301Request request);

    /**
     * 【3302】目录对照撤销；失败行写入审计且不回写院内 yb_match_flag（由 bridge 编排）。
     */
    Catalog3302RevokeResultDto revokeCatalog3302(RevokeCatalog3302Request request);
}
