/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.catalog.assembler.Catalog3301InputAssembler;
import com.openhis.chs.application.catalog.assembler.Catalog3302InputAssembler;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302RowRequest;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301RowRequest;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadRowResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeRowResultDto;
import com.openhis.chs.domain.catalog.entity.YbCatalogUploadRecord;
import com.openhis.chs.domain.catalog.facade.ChsCatalogMappingOperations;
import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.domain.catalog.repository.YbCatalogUploadRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 医保目录对照上传/撤销（3301/3302）；平台 invoke 与审计落库，不回写院内 yb_match_flag（由 bridge 编排）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsCatalogMappingAppService implements ChsCatalogMappingOperations {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog3301InputAssembler catalog3301InputAssembler;
    private final Catalog3302InputAssembler catalog3302InputAssembler;
    private final ChsInsuranceContractPort contractPort;
    private final ChsContractPropertiesPort contractPropertiesPort;
    private final YbCatalogUploadRecordRepository uploadRecordRepository;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /**
     * 【3301】目录对照上传；按批次 invoke 平台，每行写入 yb_catalog_upload_record。
     *
     * @param request 契约 bus_no 与对照行（fixmedins_hilist_id、med_list_codg 等）
     * @return 逐行成功/失败汇总；allSuccess=false 时 bridge 不应回写 yb_match_flag
     * @throws BusinessException 请求为空或未登录时
     */
    @Override
    public Catalog3301UploadResultDto uploadCatalog3301(UploadCatalog3301Request request) {
        // Gate：至少一行对照，否则无意义调用平台
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "3301 上传行不能为空");
        }
        // Pre：契约、租户、操作人与报文头经办人
        ChsContractSnapshot contract = contractPort.requireContract(request.getContractBusNo(), null);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再上传医保目录对照");
        InvokeContext ctx = buildInvokeContext(operatorId);

        List<Catalog3301UploadRowResultDto> rowResults = new ArrayList<>();
        int success = 0;
        int fail = 0;
        List<UploadCatalog3301RowRequest> rows = request.getRows();
        // Cmd：按平台批次上限分批 invoke 3301
        for (int offset = 0; offset < rows.size(); offset += ChsCatalogConstants.CATALOG_3301_BATCH_SIZE) {
            int end = Math.min(offset + ChsCatalogConstants.CATALOG_3301_BATCH_SIZE, rows.size());
            List<UploadCatalog3301RowRequest> batch = rows.subList(offset, end);
            JSONObject input = catalog3301InputAssembler.assemble(batch);
            ResultBody body = gatewayClient.invoke(
                request.getContractBusNo(),
                ChsInfNo.CATALOG_MAPPING_UPLOAD,
                input,
                ctx);
            boolean batchOk = body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
            String batchErr;
            if (batchOk) {
                batchErr = null;
            } else {
                batchErr = resolveErrMsg(body);
            }
            String msgid = null;
            for (UploadCatalog3301RowRequest row : batch) {
                Catalog3301UploadRowResultDto rowResult = new Catalog3301UploadRowResultDto()
                    .setProductId(row.getProductId())
                    .setProductTable(row.getProductTable())
                    .setFixmedinsHilistId(row.getFixmedinsHilistId())
                    .setSuccess(batchOk)
                    .setMsgid(msgid)
                    .setErrorMsg(batchErr);
                rowResults.add(rowResult);
                saveAudit(contractId, tenantId, operatorId, row, batchOk, msgid, input, body, batchErr);
                if (batchOk) {
                    success++;
                } else {
                    fail++;
                }
            }
        }
        // Asm：汇总批次结果供 bridge 决定是否回写 matchFlag
        Catalog3301UploadResultDto result = new Catalog3301UploadResultDto()
            .setAllSuccess(fail == 0)
            .setSuccessCount(success)
            .setFailCount(fail)
            .setRows(rowResults);
        if (fail == 0) {
            result.setSummary("3301 上传成功，共 " + success + " 条");
        } else if (success == 0) {
            result.setSummary("3301 上传失败：" + firstError(rowResults));
        } else {
            result.setSummary("3301 部分失败：成功 " + success + " 条，失败 " + fail + " 条");
        }
        return result;
    }

    /**
     * 【3302】目录对照撤销；逐行 invoke，成功行写入审计记录。
     *
     * @param request 契约 bus_no 与待撤销行
     * @return 逐行成功/失败汇总
     * @throws BusinessException 请求为空、未登录或 fixmedins_code 未配置时
     */
    @Override
    public Catalog3302RevokeResultDto revokeCatalog3302(RevokeCatalog3302Request request) {
        // Gate：至少一行待撤销
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "3302 撤销行不能为空");
        }
        // Pre：契约、fixmedins_code（3302 报文必填）、操作人
        ChsContractSnapshot contract = contractPort.requireContract(request.getContractBusNo(), null);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再撤销医保目录对照");
        String fixmedinsCode = contractPropertiesPort.findFixmedinsCode(request.getContractBusNo())
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID,
                "chs-contract.properties 未配置 contract." + request.getContractBusNo() + ".fixmedins_code"));
        InvokeContext ctx = buildInvokeContext(operatorId);

        List<Catalog3302RevokeRowResultDto> rowResults = new ArrayList<>();
        int success = 0;
        int fail = 0;
        // Cmd：3302 平台按行受理，失败行不影响同批其他行的审计
        for (RevokeCatalog3302RowRequest row : request.getRows()) {
            catalog3302InputAssembler.validateRow(row);
            JSONObject input = catalog3302InputAssembler.assemble(fixmedinsCode, row);
            ResultBody body = gatewayClient.invoke(
                request.getContractBusNo(),
                ChsInfNo.CATALOG_MAPPING_REVOKE,
                input,
                ctx);
            boolean ok = body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
            String err;
            if (ok) {
                err = null;
            } else {
                err = resolveErrMsg(body);
            }
            rowResults.add(new Catalog3302RevokeRowResultDto()
                .setProductId(row.getProductId())
                .setProductTable(row.getProductTable())
                .setFixmedinsHilistId(row.getFixmedinsHilistId())
                .setSuccess(ok)
                .setErrorMsg(err));
            saveRevokeAudit(contractId, tenantId, operatorId, row, ok, input, body, err);
            if (ok) {
                success++;
            } else {
                fail++;
            }
        }
        // Asm：汇总撤销结果
        Catalog3302RevokeResultDto result = new Catalog3302RevokeResultDto()
            .setAllSuccess(fail == 0)
            .setSuccessCount(success)
            .setFailCount(fail)
            .setRows(rowResults);
        if (fail == 0) {
            result.setSummary("3302 撤销成功，共 " + success + " 条");
        } else if (success == 0) {
            result.setSummary("3302 撤销失败：" + firstRevokeError(rowResults));
        } else {
            result.setSummary("3302 部分失败：成功 " + success + " 条，失败 " + fail + " 条");
        }
        return result;
    }

    private void saveAudit(
            Long contractId,
            Long tenantId,
            Long operatorId,
            UploadCatalog3301RowRequest row,
            boolean success,
            String msgid,
            JSONObject request,
            ResultBody response,
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
        String responseSnapshot;
        if (response != null) {
            responseSnapshot = JSON.toJSONString(response);
        } else {
            responseSnapshot = null;
        }
        record.setResponseSnapshot(responseSnapshot);
        record.setErrorMsg(errorMsg);
        record.setOperatorId(operatorId);
        record.setIsDeleted(false);
        record.setCreatedBy(operatorId);
        uploadRecordRepository.save(record);
    }

    private void saveRevokeAudit(
            Long contractId,
            Long tenantId,
            Long operatorId,
            RevokeCatalog3302RowRequest row,
            boolean success,
            JSONObject request,
            ResultBody response,
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
        String responseSnapshot;
        if (response != null) {
            responseSnapshot = JSON.toJSONString(response);
        } else {
            responseSnapshot = null;
        }
        record.setResponseSnapshot(responseSnapshot);
        record.setErrorMsg(errorMsg);
        record.setOperatorId(operatorId);
        record.setIsDeleted(false);
        record.setCreatedBy(operatorId);
        uploadRecordRepository.save(record);
    }

    private static String resolveErrMsg(ResultBody body) {
        if (body == null) {
            return "平台无应答";
        }
        if (StringUtils.isNotBlank(body.getErrMsg())) {
            return body.getErrMsg().trim();
        }
        return "infcode=" + body.getInfcode();
    }

    private static String firstError(List<Catalog3301UploadRowResultDto> rows) {
        for (Catalog3301UploadRowResultDto row : rows) {
            if (!row.isSuccess() && StringUtils.isNotBlank(row.getErrorMsg())) {
                return row.getErrorMsg();
            }
        }
        return "未知错误";
    }

    private static String firstRevokeError(List<Catalog3302RevokeRowResultDto> rows) {
        for (Catalog3302RevokeRowResultDto row : rows) {
            if (!row.isSuccess() && StringUtils.isNotBlank(row.getErrorMsg())) {
                return row.getErrorMsg();
            }
        }
        return "未知错误";
    }

    private InvokeContext buildInvokeContext(Long operatorId) {
        String operatorIdText;
        if (operatorId != null) {
            operatorIdText = String.valueOf(operatorId);
        } else {
            operatorIdText = null;
        }
        InvokeContext partial = InvokeContext.builder()
            .operatorId(operatorIdText)
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }
}
