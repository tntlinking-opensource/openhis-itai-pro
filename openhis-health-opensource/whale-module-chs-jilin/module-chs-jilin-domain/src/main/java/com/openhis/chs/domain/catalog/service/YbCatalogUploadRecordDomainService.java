/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.command.RevokeCatalog3302RowCommand;
import com.openhis.chs.domain.catalog.command.UploadCatalog3301RowCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogUploadRecord;
import com.openhis.chs.domain.catalog.repository.YbCatalogUploadRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code yb_catalog_upload_record} 3301/3302 审计落库 */
@Service
@RequiredArgsConstructor
public class YbCatalogUploadRecordDomainService {

    private final YbCatalogUploadRecordRepository uploadRecordRepository;

    /**
     * 3301 上传完成后落审计记录。
     *
     * @param success          平台是否成功
     * @param msgid            报文 msgid
     * @param request          请求体快照
     * @param responseSnapshot 响应快照
     * @param errorMsg         失败原因
     */
    @Transactional
    public void saveUpload3301Audit(
            Long contractId,
            Long tenantId,
            Long operatorId,
            UploadCatalog3301RowCommand row,
            boolean success,
            String msgid,
            JSONObject request,
            String responseSnapshot,
            String errorMsg) {
        YbCatalogUploadRecord record = new YbCatalogUploadRecord();
        record.setContractId(contractId);
        record.setTenantId(tenantId);
        record.setOperation(ChsCatalogConstants.OPERATION_UPLOAD_3301);
        record.setProductTable(row.getProductTable());
        record.setProductId(row.getProductId());
        record.setListType(row.getListType());
        record.setFixmedinsHilistId(row.getFixmedinsHilistId());
        record.setMedListCodg(row.getMedListCodg());
        record.setPlatformSuccess(success);
        record.setMsgid(msgid);
        record.setRequestSnapshot(JSON.toJSONString(request));
        record.setResponseSnapshot(responseSnapshot);
        record.setErrorMsg(errorMsg);
        record.setOperatorId(operatorId);
        record.setIsDeleted(false);
        record.setCreatedBy(operatorId);
        uploadRecordRepository.save(record);
    }

    /**
     * 3302 撤销完成后落审计记录。
     *
     * @param success          平台是否成功
     * @param request          请求体快照
     * @param responseSnapshot 响应快照
     * @param errorMsg         失败原因
     */
    @Transactional
    public void saveRevoke3302Audit(
            Long contractId,
            Long tenantId,
            Long operatorId,
            RevokeCatalog3302RowCommand row,
            boolean success,
            JSONObject request,
            String responseSnapshot,
            String errorMsg) {
        YbCatalogUploadRecord record = new YbCatalogUploadRecord();
        record.setContractId(contractId);
        record.setTenantId(tenantId);
        record.setOperation(ChsCatalogConstants.OPERATION_REVOKE_3302);
        record.setProductTable(row.getProductTable());
        record.setProductId(row.getProductId());
        record.setListType(row.getListType());
        record.setFixmedinsHilistId(row.getFixmedinsHilistId());
        record.setMedListCodg(row.getMedListCodg());
        record.setPlatformSuccess(success);
        record.setRequestSnapshot(JSON.toJSONString(request));
        record.setResponseSnapshot(responseSnapshot);
        record.setErrorMsg(errorMsg);
        record.setOperatorId(operatorId);
        record.setIsDeleted(false);
        record.setCreatedBy(operatorId);
        uploadRecordRepository.save(record);
    }
}
