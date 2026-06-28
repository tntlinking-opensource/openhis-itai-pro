/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.upload.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.mrms.upload.dto.CaseRecordUploadSummaryDto;
import com.openhis.health.application.mrms.upload.dto.UploadLogDto;
import com.openhis.health.application.mrms.upload.dto.UploadResultDto;
import com.openhis.health.application.mrms.upload.mapper.CaseRecord4401Mapper;
import com.openhis.health.application.mrms.upload.mapper.UploadMapper;
import com.openhis.health.application.mrms.upload.request.ListUploadLogsRequest;
import com.openhis.health.application.mrms.upload.request.QueryUploadPageRequest;
import com.openhis.health.application.mrms.upload.request.RetryUploadRequest;
import com.openhis.health.application.mrms.upload.request.Upload4401Request;
import com.openhis.health.application.mrms.upload.support.MrmsInsuranceEncounterContext;
import com.openhis.health.application.mrms.upload.support.MrmsInsuranceEncounterSupport;
import com.openhis.health.application.mrms.upload.support.Upload4401Validator;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordUploadPageQuery;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordQueryService;
import com.openhis.health.domain.mrms.upload.entity.UploadLog;
import com.openhis.health.domain.mrms.upload.service.UploadLogDomainService;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.ChsUploadStatus;
import com.openhis.health.integration.insurance.medcas.MedcasHomepageUploadExtension;
import com.openhis.health.integration.insurance.medcas.MedcasInfCodes;
import com.openhis.health.integration.insurance.medcas.command.MedcasHomepageUploadCommand;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepagePayload;
import com.openhis.health.integration.insurance.medcas.result.MedcasUploadResult;
import com.openhis.health.module.common.validator.MrmsOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 4401 医保上传应用服务（IF-MR-Upload）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordUpload/*}。已归档且首页冻结的病案组装 4401
 * 载荷并调用医保网关；含待传列表、日志查询与重试。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordUploadAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordQueryService caseRecordQueryService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final UploadLogDomainService uploadLogDomainService;
    private final MrmsInsuranceEncounterSupport insuranceEncounterSupport;
    private final MedcasHomepageUploadExtension medcasHomepageUploadExtension;
    private final ContractQueryService contractQueryService;
    private final CaseRecord4401Mapper caseRecord4401Mapper;
    private final UploadMapper uploadMapper;
    private final MrmsOperatorResolver operatorResolver;
    private final PageWindowGate pageWindowGate;
    private final ObjectMapper objectMapper;

    /**
     * 上传 4401 病案首页至医保平台。
     *
     * @param request 上传请求
     * @return 上传结果
     */
    public UploadResultDto upload(Upload4401Request request) {
        // Gate：病案与费用性质必填
        return doUpload(requireCaseRecordId(request.getCaseRecordId()), requireContractBusNo(request.getContractBusNo()));
    }

    /**
     * 按上传日志重试 4401。
     *
     * @param request 重试请求
     * @return 上传结果
     */
    public UploadResultDto retry(RetryUploadRequest request) {
        // Gate：上传日志主键必填
        if (request.getUploadLogId() == null) {
            throw new ValidationException("上传日志主键不能为空");
        }
        // Pre：从日志还原病案与 contractBusNo
        UploadLog log = uploadLogDomainService.requireById(request.getUploadLogId());
        return doUpload(log.getCaseRecordId(), trimToNull(log.getContractBusNo()));
    }

    /**
     * 查询病案上传日志列表。
     *
     * @param request 查询请求
     * @return 日志 DTO 列表
     */
    public List<UploadLogDto> queryUploadLogs(ListUploadLogsRequest request) {
        // Gate：病案主键必填且须存在
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        caseRecordDomainService.requireById(caseRecordId);
        // Pre：读上传日志列表
        List<UploadLogDto> result = new ArrayList<>();
        for (UploadLog row : uploadLogDomainService.listByCaseRecord(caseRecordId)) {
            result.add(uploadMapper.toLogDto(row));
        }
        // Asm：映射日志 DTO
        return result;
    }

    /**
     * 预览 4401 组装载荷（不调医保网关）。
     *
     * @param request 预览请求
     * @return 4401 载荷
     */
    public MedcasHomepagePayload previewAssembled(Upload4401Request request) {
        // Gate：病案与费用性质必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        assertUploadPreconditions(record);
        // Pre：加载医保就诊上下文
        Optional<MrmsInsuranceEncounterContext> insCtx = insuranceEncounterSupport.findContext(
                record.getEncounterId(), requireContractBusNo(request.getContractBusNo()));
        MrmsInsuranceEncounterContext ctx = null;
        // 自费/无 mdtrt 时仍可预览组装（insCtx 为 null）
        if (insCtx.isPresent()) {
            ctx = insCtx.get();
        }
        // Asm：映射 4401 载荷
        return caseRecord4401Mapper.assemble(record, ctx);
    }

    /**
     * 分页检索已归档病案上传摘要。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public PagedResult<CaseRecordUploadSummaryDto> queryUploadPage(QueryUploadPageRequest request) {
        // Gate：分页窗口合法
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        // 分页上限 200，防止大页拖垮列表
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：组装领域查询
        CaseRecordUploadPageQuery query = new CaseRecordUploadPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setChsUploadStatusCode(trimToNull(request.getStatusCode()));
        query.setKeyword(request.getKeyword());
        Page<CaseRecord> page = caseRecordQueryService.queryUploadPage(
                query,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "archivedAt", "id")));
        // Asm：映射摘要 DTO
        List<CaseRecordUploadSummaryDto> items = new ArrayList<>();
        for (CaseRecord row : page.getContent()) {
            items.add(uploadMapper.toUploadSummaryDto(row));
        }
        return PagedResult.of(items, page.getTotalElements(), pageNo, pageSize);
    }

    private UploadResultDto doUpload(Long caseRecordId, String contractBusNo) {
        // Gate：病案须已归档且首页冻结
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        assertUploadPreconditions(record);
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();

        // 非医保住院直接标记跳过，避免误传自费病案
        if (!insuranceEncounterSupport.isInsuranceInpatient(record.getEncounterId())) {
            return skippedResult(caseRecordId, operatorId);
        }

        // Pre：解析医保就诊上下文（mdtrt_id 等）
        Optional<MrmsInsuranceEncounterContext> insCtxOpt = insuranceEncounterSupport.findContext(
                record.getEncounterId(), contractBusNo);
        if (insCtxOpt.isEmpty()) {
            // 医保住院但缺 mdtrt_id，阻断上传
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "非医保住院或未登记 mdtrt_id");
        }

        // Pre：组装并校验 4401 载荷
        MedcasHomepagePayload payload = caseRecord4401Mapper.assemble(record, insCtxOpt.get());
        Upload4401Validator.validate(payload, true);
        String inputJson = serializePayload(payload);
        String digest = digest(inputJson);

        if (!medcasHomepageUploadExtension.isEnabled()) {
            // CHS starter 未装配，阻断实际上传
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "医保网关未装配，请启用 chs-jilin-starter");
        }
        // Cmd：调用医保网关上传并落上传日志
        MedcasHomepageUploadCommand uploadCommand = new MedcasHomepageUploadCommand();
        uploadCommand.setContractBusNo(contractBusNo);
        uploadCommand.setInputJson(inputJson);
        uploadCommand.setOperatorId(operatorId);
        MedcasUploadResult platform = medcasHomepageUploadExtension.upload(uploadCommand);

        Long contractId = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo)
                .map(contract -> contract.getId())
                .orElse(null);
        String status;
        ChsUploadStatus uploadStatus;
        // 平台成功落 SUCCESS，否则 FAILED 并写错误摘要
        if (platform.isSuccess()) {
            status = ChsUploadStatus.SUCCESS.getCode();
            uploadStatus = ChsUploadStatus.SUCCESS;
        } else {
            status = ChsUploadStatus.FAILED.getCode();
            uploadStatus = ChsUploadStatus.FAILED;
        }
        String snapshot = JSON.toJSONString(platform);
        UploadLog log = uploadLogDomainService.appendLog(
                tenantId,
                record.getId(),
                contractBusNo,
                contractId,
                MedcasInfCodes.MEDCAS_HOMEPAGE,
                status,
                digest,
                platform.getInfcode(),
                platform.getErrMsg(),
                snapshot,
                operatorId);
        uploadLogDomainService.updateCaseRecordUploadStatus(
                record.getId(),
                uploadStatus,
                platform.getErrMsg(),
                operatorId);
        // Asm：映射出参
        UploadResultDto dto = new UploadResultDto();
        dto.setStatusCode(status);
        dto.setUploadLogId(String.valueOf(log.getId()));
        dto.setInfcode(platform.getInfcode());
        dto.setErrMsg(platform.getErrMsg());
        dto.setAssembledAt(LocalDateTime.now());
        return dto;
    }

    /**
     * 非医保住院：仅更新主档上传状态为 skipped，不调网关。
     *
     * @param caseRecordId 病案主键
     * @param operatorId 操作人
     * @return 跳过结果 DTO
     */
    private UploadResultDto skippedResult(Long caseRecordId, Long operatorId) {
        uploadLogDomainService.updateCaseRecordUploadStatus(
                caseRecordId, ChsUploadStatus.SKIPPED, null, operatorId);
        UploadResultDto dto = new UploadResultDto();
        dto.setStatusCode(ChsUploadStatus.SKIPPED.getCode());
        dto.setAssembledAt(LocalDateTime.now());
        return dto;
    }

    /**
     * 4401 上传前置：已归档、首页冻结、流水号与病案号齐全。
     *
     * @param record 病案主档
     */
    private void assertUploadPreconditions(CaseRecord record) {
        // 未归档不可上传
        if (record.getStatusEnum() != CaseRecordStatus.ARCHIVED) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "未归档不可上传");
        }
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.requireExistingByCaseRecordId(record.getId());
        // 首页未冻结不可上传
        if (!Boolean.TRUE.equals(homepage.getFrozen())) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "首页未冻结");
        }
        // 4401 流水号缺失阻断上传
        if (trimToNull(homepage.getIptMedcasHmpgSn()) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "首页流水号缺失");
        }
        // 病案号缺失阻断上传
        if (trimToNull(record.getMedcasno()) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "病案号缺失");
        }
    }

    /**
     * 4401 载荷 JSON 序列化（供网关与 digest 使用）。
     *
     * @param payload 4401 载荷
     * @return JSON 字符串
     */
    private String serializePayload(MedcasHomepagePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4401 载荷序列化失败");
        }
    }

    /**
     * 请求体 SHA-256 摘要（截断 128 字符存日志，用于重试幂等比对）。
     *
     * @param json 4401 JSON
     * @return 十六进制摘要，失败时 null
     */
    private String digest(String json) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String full = sb.toString();
            // 日志列长度有限，截断至 128 字符
            if (full.length() > 128) {
                return full.substring(0, 128);
            }
            return full;
        } catch (Exception ex) {
            // 摘要失败不阻断上传，由日志侧 nullable 处理
            return null;
        }
    }

    /**
     * 校验病案主键非空。
     *
     * @param caseRecordId 病案主档主键
     * @return 非 null 主键
     */
    private Long requireCaseRecordId(Long caseRecordId) {
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        return caseRecordId;
    }

    /**
     * 校验费用性质 contractBusNo 非空。
     *
     * @param contractBusNo 费用性质业务号
     * @return trim 后的业务号
     */
    private String requireContractBusNo(String contractBusNo) {
        if (trimToNull(contractBusNo) == null) {
            throw new ValidationException("费用性质 contractBusNo 不能为空");
        }
        return contractBusNo.trim();
    }
}
