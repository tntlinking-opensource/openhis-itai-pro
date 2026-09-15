/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.mrms.upload.medcas.dto.CaseRecordUploadSummaryDto;
import com.openhis.health.application.mrms.upload.medcas.dto.OneClickReportResultDto;
import com.openhis.health.application.mrms.upload.medcas.dto.SettleListQuery4103Dto;
import com.openhis.health.application.mrms.upload.medcas.dto.UploadLogDto;
import com.openhis.health.application.mrms.upload.medcas.dto.UploadResultDto;
import com.openhis.health.application.mrms.upload.medcas.mapper.CaseRecord4401Mapper;
import com.openhis.health.application.mrms.upload.medcas.mapper.CaseRecord4101AMapper;
import com.openhis.health.application.mrms.upload.medcas.mapper.UploadMapper;
import com.openhis.health.application.mrms.upload.medcas.request.ListUploadLogsRequest;
import com.openhis.health.application.mrms.upload.medcas.request.MedcasReportRequest;
import com.openhis.health.application.mrms.upload.medcas.request.ModifySettleListStatusRequest;
import com.openhis.health.application.mrms.upload.medcas.request.QuerySettleList4103Request;
import com.openhis.health.application.mrms.upload.medcas.request.QueryUploadPageRequest;
import com.openhis.health.application.mrms.upload.medcas.request.RetryUploadRequest;
import com.openhis.health.application.mrms.upload.medcas.request.Upload4401Request;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterLoader;
import com.openhis.health.application.mrms.upload.medcas.support.SettleListOutputSupport;
import com.openhis.health.application.mrms.upload.medcas.support.SettleListQueryOutputSupport;
import com.openhis.health.application.mrms.upload.medcas.support.Upload4101AValidator;
import com.openhis.health.application.mrms.upload.medcas.support.Upload4102Validator;
import com.openhis.health.application.mrms.upload.medcas.support.Upload4401Validator;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
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
import com.openhis.health.domain.share.constants.MrmsSettleUploadFlags;
import com.openhis.health.domain.share.enums.ChsUploadStatus;
import com.openhis.health.infrastructure.integration.insurance.medcas.MedcasHomepageUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.medcas.MedcasInfCodes;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListStatusExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasHomepagePayload;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
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
 * 病案医保上报应用服务（4401 / 4101A / 4102 / 4103 / 一键编排）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordUpload/*}。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordUploadAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordQueryService caseRecordQueryService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final UploadLogDomainService uploadLogDomainService;
    private final InsuranceEncounterLoader insuranceEncounterLoader;
    private final MedcasHomepageUploadExtension medcasHomepageUploadExtension;
    private final ContractQueryService contractQueryService;
    private final AccountQueryService accountQueryService;
    private final CaseRecord4401Mapper caseRecord4401Mapper;
    private final CaseRecord4101AMapper caseRecord4101AMapper;
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
        // Gate：病案必填；合同可省略（从医保账户解析）
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：装载主档并解析契约
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        String contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        // Cmd：出站 4401 并落日志/回写
        return doUpload4401(caseRecordId, contractBusNo, false);
    }

    /**
     * 预览 4401 组装载荷（不调医保网关）。
     *
     * @param request 预览请求
     * @return 4401 载荷
     */
    public MedcasHomepagePayload previewAssembled(Upload4401Request request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：装载主档并校验基础字段（不归档不冻结）
        CaseRecord prepared = prepareForUpload(caseRecordId);
        String contractBusNo = resolveContractBusNo(prepared.getEncounterId(), request.getContractBusNo());
        Optional<InsuranceEncounterContext> insCtx = insuranceEncounterLoader.findContext(
                prepared.getEncounterId(), contractBusNo);
        InsuranceEncounterContext ctx = null;
        // 有结算上下文才做必填校验；无上下文仅组装预览
        if (insCtx.isPresent()) {
            ctx = insCtx.get();
        }
        // Asm：Mapper 组装载荷
        MedcasHomepagePayload payload = caseRecord4401Mapper.assemble(prepared, ctx);
        if (insCtx.isPresent()) {
            Upload4401Validator.validate(payload, true);
        }
        return payload;
    }

    /**
     * 上传 4101A 结算清单。
     *
     * @param request 病案+合同
     * @return 上传结果（含 setl_list_id）
     */
    public UploadResultDto uploadSettleList(MedcasReportRequest request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：装载主档并解析契约
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        String contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        // Cmd：出站 4101A 并回写 setl_list_id
        return doUpload4101A(caseRecordId, contractBusNo, false);
    }

    /**
     * 预览 4101A 组装载荷。
     *
     * @param request 病案+合同
     * @return 4101A 载荷
     */
    public MedcasSettleListPayload previewSettleList(MedcasReportRequest request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：归档冻结 + 已结算上下文
        CaseRecord record = prepareForUpload(caseRecordId);
        String contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        InsuranceEncounterContext ctx = requireSettledContext(record, contractBusNo);
        Long operatorId = operatorResolver.requireOperatorId();
        // Asm：组装并校验规范必填
        MedcasSettleListPayload payload = caseRecord4101AMapper.assemble(record, ctx, operatorId);
        Upload4101AValidator.validate(payload);
        return payload;
    }

    /**
     * 4102 提交审核 / 撤回审核。
     *
     * @param request 含 stas_type
     * @return 调用结果
     */
    public UploadResultDto modifySettleListStatus(ModifySettleListStatusRequest request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：装载主档、契约与 stas_type
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        String contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        String stasType = trimToNull(request.getStasType());
        // Cmd：出站 4102 并回写 settle_upload_status_code
        return do4102(caseRecordId, contractBusNo, stasType, false);
    }

    /**
     * 一键上报：4401 → 4101A → 4102(stas=1)；已成功侧跳过。
     *
     * @param request 病案+合同
     * @return 分步结果
     */
    public OneClickReportResultDto reportOneClick(MedcasReportRequest request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：装载主档与契约
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        String contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        OneClickReportResultDto result = new OneClickReportResultDto();
        // Cmd：分步出站；任一步失败即短路返回分步结果
        UploadResultDto step4401 = doUpload4401(caseRecordId, contractBusNo, true);
        result.setHomepage4401(step4401);
        // 4401 失败则不再打结算清单
        if (!isStepOk(step4401)) {
            result.setAllSuccess(false);
            return result;
        }
        UploadResultDto step4101A = doUpload4101A(caseRecordId, contractBusNo, true);
        result.setSettle4101A(step4101A);
        // 4101A 失败则不提交审核
        if (!isStepOk(step4101A)) {
            result.setAllSuccess(false);
            return result;
        }
        UploadResultDto step4102 = do4102(caseRecordId, contractBusNo, "1", true);
        result.setCommit4102(step4102);
        // Asm：汇总三步是否均成功（含跳过）
        result.setAllSuccess(isStepOk(step4102));
        return result;
    }

    /**
     * 按上传日志重试（按日志 infno 分发）。
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
        String infno = trimToNull(log.getInfno());
        // Cmd：按交易号分发到对应出站（4103 为只读查询，不支持重试上传）
        if (MedcasInfCodes.SETTLE_LIST_UPLOAD.equals(infno)) {
            return doUpload4101A(log.getCaseRecordId(), trimToNull(log.getContractBusNo()), false);
        }
        if (MedcasInfCodes.SETTLE_LIST_STATUS.equals(infno)) {
            return do4102(log.getCaseRecordId(), trimToNull(log.getContractBusNo()), "1", false);
        }
        return doUpload4401(log.getCaseRecordId(), trimToNull(log.getContractBusNo()), false);
    }

    /**
     * 查询病案上传日志列表。
     *
     * @param request 查询请求
     * @return 日志 DTO 列表
     */
    public List<UploadLogDto> queryUploadLogs(ListUploadLogsRequest request) {
        // Gate：病案主键必填且主档存在
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        caseRecordDomainService.requireById(caseRecordId);
        // Asm：实体转 DTO（含 infno，便于运维区分 4401/4101A/4102/4103）
        List<UploadLogDto> result = new ArrayList<>();
        for (UploadLog row : uploadLogDomainService.listByCaseRecord(caseRecordId)) {
            result.add(uploadMapper.toLogDto(row));
        }
        return result;
    }

    /**
     * 【4103】查询平台结算清单信息（只读，不改院内首页）。
     *
     * <p>可直接传 psn_no+setl_id；或传 caseRecordId/medcasno 由院内结算上下文解析。
     * 能定位病案时写入上传流水，便于运维「看上传记录」。</p>
     *
     * @param request 查询入参
     * @return 平台 setlinfo 摘要
     */
    public SettleListQuery4103Dto querySettleList(QuerySettleList4103Request request) {
        // Gate：Extension 已装配；操作员/租户可解析
        requireExtensionEnabled();
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();
        String psnNo = trimToNull(request.getPsnNo());
        String setlId = trimToNull(request.getSetlId());
        CaseRecord record = null;
        String contractBusNo;
        // Pre：缺平台键时按病案主键或病案号解析结算上下文
        if (psnNo == null || setlId == null) {
            Long caseRecordId = request.getCaseRecordId();
            // 无主键则要求病案号精确命中
            if (caseRecordId == null) {
                String medcasno = trimToNull(request.getMedcasno());
                if (medcasno == null) {
                    throw new ValidationException("请填写 psn_no+setl_id，或病案主键/病案号");
                }
                record = caseRecordQueryService.findOptionalByMedcasno(tenantId, medcasno)
                        .orElseThrow(() -> new ValidationException("未找到病案号：" + medcasno));
                caseRecordId = record.getId();
            } else {
                record = caseRecordDomainService.requireById(caseRecordId);
            }
            contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
            InsuranceEncounterContext ctx = requireSettledContext(record, contractBusNo);
            psnNo = ctx.getPsnNo();
            setlId = trimToNull(ctx.getSetlId());
            // 结算未落库则无法打平台 4103
            if (setlId == null) {
                throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "医保 setl_id 缺失，无法查询 4103");
            }
        } else if (request.getCaseRecordId() != null) {
            // 已有平台键：可选挂病案以便写查询流水
            record = caseRecordDomainService.requireById(request.getCaseRecordId());
            contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
        } else {
            String medcasno = trimToNull(request.getMedcasno());
            // 可选病案号：命中则挂流水；未命中则要求显式契约
            if (medcasno != null) {
                Optional<CaseRecord> byNo = caseRecordQueryService.findOptionalByMedcasno(tenantId, medcasno);
                if (byNo.isPresent()) {
                    record = byNo.get();
                    contractBusNo = resolveContractBusNo(record.getEncounterId(), request.getContractBusNo());
                } else {
                    contractBusNo = requireExplicitContractBusNo(request.getContractBusNo());
                }
            } else {
                contractBusNo = requireExplicitContractBusNo(request.getContractBusNo());
            }
        }
        // Cmd：出站 4103（只读）
        MedcasSettleListQueryExtensionIn command = new MedcasSettleListQueryExtensionIn();
        command.setContractBusNo(contractBusNo);
        command.setPsnNo(psnNo);
        command.setSetlId(setlId);
        command.setOperatorId(operatorId);
        MedcasUploadExtensionOut platform = medcasHomepageUploadExtension.querySettleList(command);
        // 能定位病案时落查询流水（不回写首页上传标志）
        if (record != null) {
            String inputJson = JSON.toJSONString(command);
            persistPlatformResult(
                    tenantId,
                    record.getId(),
                    contractBusNo,
                    MedcasInfCodes.SETTLE_LIST_QUERY,
                    inputJson,
                    platform,
                    operatorId,
                    false,
                    record.getSetlListId());
        }
        // Asm：解析平台全量 output + setlinfo 摘要
        SettleListQuery4103Dto dto = new SettleListQuery4103Dto();
        dto.setSuccess(platform.isSuccess());
        dto.setErrMsg(platform.getErrMsg());
        dto.setInfcode(platform.getInfcode());
        dto.setPsnNo(psnNo);
        dto.setSetlId(setlId);
        if (record != null) {
            dto.setMedcasno(trimToNull(record.getMedcasno()));
            dto.setPsnName(trimToNull(record.getPsnName()));
            dto.setSetlListId(trimToNull(record.getSetlListId()));
        }
        // 有 output 即解析（成功展示全量；失败也可能带回包片段）
        if (trimToNull(platform.getOutput()) != null) {
            SettleListQueryOutputSupport.fillFromOutput(dto, platform.getOutput());
        }
        if (!platform.isSuccess()) {
            dto.setPlatformMsg(platform.getErrMsg());
        }
        return dto;
    }

    /**
     * 分页检索病案上传摘要。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public PagedResult<CaseRecordUploadSummaryDto> queryUploadPage(QueryUploadPageRequest request) {
        // Gate：分页窗口合法且每页不超过 200
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：租户 + 交易号/出院窗/状态条件下推
        CaseRecordUploadPageQuery query = new CaseRecordUploadPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setInfno(trimToNull(request.getInfno()));
        query.setUploadStatusCode(trimToNull(request.getStatusCode()));
        query.setKeyword(request.getKeyword());
        query.setDscgTimeFrom(request.getDscgTimeFrom());
        query.setDscgTimeTo(request.getDscgTimeTo());
        String infno = trimToNull(request.getInfno());
        Sort sort;
        // 4401 按归档时间；结算清单按最近结算上传/出院时间
        if ("4401".equals(infno)) {
            sort = Sort.by(Sort.Direction.DESC, "archivedAt", "id");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "settleUploadAt", "dscgTime", "id");
        }
        Page<CaseRecord> page = caseRecordQueryService.queryUploadPage(
                query,
                PageRequest.of(pageNo - 1, pageSize, sort));
        // Asm：主档 → 摘要 DTO，并补齐医保 psn_no/setl_id 供运维可见可点查
        List<CaseRecordUploadSummaryDto> items = new ArrayList<>();
        for (CaseRecord row : page.getContent()) {
            CaseRecordUploadSummaryDto dto = uploadMapper.toUploadSummaryDto(row);
            this.fillInsuranceKeys(dto, row);
            items.add(dto);
        }
        return PagedResultBuilder.build(new PagedResult<>(), items, page);
    }

    /**
     * 列表行补齐医保人员编号/结算 ID（失败不阻断列表）。
     *
     * @param dto 摘要行
     * @param row 病案主档
     */
    private void fillInsuranceKeys(CaseRecordUploadSummaryDto dto, CaseRecord row) {
        if (row == null || row.getEncounterId() == null) {
            return;
        }
        try {
            String busNo = resolveContractBusNo(row.getEncounterId(), null);
            Optional<InsuranceEncounterContext> ctxOpt =
                    insuranceEncounterLoader.findContext(row.getEncounterId(), busNo);
            if (ctxOpt.isEmpty()) {
                return;
            }
            InsuranceEncounterContext ctx = ctxOpt.get();
            dto.setPsnNo(trimToNull(ctx.getPsnNo()));
            dto.setSetlId(trimToNull(ctx.getSetlId()));
        } catch (Exception ex) {
            // 未结算/缺契约时跳过，列表仍可展示上传态
        }
    }

    /**
     * 4401 实际上传。
     *
     * @param caseRecordId 病案
     * @param contractBusNo 合同
     * @param skipIfSuccess 已成功则跳过
     * @return 结果
     */
    private UploadResultDto doUpload4401(Long caseRecordId, String contractBusNo, boolean skipIfSuccess) {
        CaseRecord record = prepareForUpload(caseRecordId);
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();
        // 已成功侧跳过：仍补齐自动归档冻结（上传成功后才锁死编辑）
        if (skipIfSuccess && ChsUploadStatus.SUCCESS.getCode().equals(trimToNull(record.getChsUploadStatusCode()))) {
            ensureArchivedAfterHomepageUpload(caseRecordId);
            return skippedStepResult();
        }
        // 非医保住院直接标记跳过（不上报、不归档）
        if (!insuranceEncounterLoader.isInsuranceInpatient(record.getEncounterId())) {
            return skippedResult(caseRecordId, operatorId);
        }
        InsuranceEncounterContext ctx = requireSettledContext(record, contractBusNo);
        MedcasHomepagePayload payload = caseRecord4401Mapper.assemble(record, ctx);
        Upload4401Validator.validate(payload, true);
        String inputJson = serialize(payload, "4401");
        requireExtensionEnabled();
        MedcasHomepageUploadExtensionIn uploadCommand = new MedcasHomepageUploadExtensionIn();
        uploadCommand.setContractBusNo(contractBusNo);
        uploadCommand.setInputJson(inputJson);
        uploadCommand.setOperatorId(operatorId);
        MedcasUploadExtensionOut platform = medcasHomepageUploadExtension.upload(uploadCommand);
        UploadResultDto dto = persistPlatformResult(
                tenantId,
                record.getId(),
                contractBusNo,
                MedcasInfCodes.MEDCAS_HOMEPAGE,
                inputJson,
                platform,
                operatorId,
                true,
                null);
        // 4401 成功后自动归档冻结，之后不可再改首页
        if (ChsUploadStatus.SUCCESS.getCode().equals(trimToNull(dto.getStatusCode()))) {
            ensureArchivedAfterHomepageUpload(caseRecordId);
        }
        return dto;
    }

    /**
     * 4101A 实际上传。
     *
     * @param caseRecordId 病案
     * @param contractBusNo 合同
     * @param skipIfSuccess 已上传(≥1)则跳过
     * @return 结果
     */
    private UploadResultDto doUpload4101A(Long caseRecordId, String contractBusNo, boolean skipIfSuccess) {
        CaseRecord record = prepareForUpload(caseRecordId);
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();
        String settleFlag = trimToNull(record.getSettleUploadStatusCode());
        // 已上传未提交或已提交：一键时跳过
        if (skipIfSuccess
                && (MrmsSettleUploadFlags.UPLOADED.equals(settleFlag)
                        || MrmsSettleUploadFlags.COMMITTED.equals(settleFlag))) {
            UploadResultDto skipped = skippedStepResult();
            skipped.setSetlListId(record.getSetlListId());
            return skipped;
        }
        if (!insuranceEncounterLoader.isInsuranceInpatient(record.getEncounterId())) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "非医保住院不可上传结算清单");
        }
        InsuranceEncounterContext ctx = requireSettledContext(record, contractBusNo);
        MedcasSettleListPayload payload = caseRecord4101AMapper.assemble(record, ctx, operatorId);
        Upload4101AValidator.validate(payload);
        String inputJson = serialize(payload, "4101A");
        requireExtensionEnabled();
        MedcasSettleListUploadExtensionIn command = new MedcasSettleListUploadExtensionIn();
        command.setContractBusNo(contractBusNo);
        command.setInputJson(inputJson);
        command.setOperatorId(operatorId);
        MedcasUploadExtensionOut platform = medcasHomepageUploadExtension.uploadSettleList(command);
        String setlListId = SettleListOutputSupport.parseSetlListId(platform.getOutput());
        UploadResultDto dto = persistPlatformResult(
                tenantId,
                record.getId(),
                contractBusNo,
                MedcasInfCodes.SETTLE_LIST_UPLOAD,
                inputJson,
                platform,
                operatorId,
                false,
                setlListId);
        // 成功回写 settle 标志=1
        if (platform.isSuccess()) {
            uploadLogDomainService.updateCaseRecordSettleUploadStatus(
                    record.getId(),
                    MrmsSettleUploadFlags.UPLOADED,
                    setlListId,
                    false,
                    null,
                    operatorId);
            dto.setSetlListId(setlListId);
        } else {
            uploadLogDomainService.updateCaseRecordSettleUploadStatus(
                    record.getId(),
                    settleFlag,
                    null,
                    false,
                    platform.getErrMsg(),
                    operatorId);
        }
        return dto;
    }

    /**
     * 4102 状态修改。
     *
     * @param caseRecordId 病案
     * @param contractBusNo 合同
     * @param stasType 0/1
     * @param skipIfCommitted 一键时已提交则跳过
     * @return 结果
     */
    private UploadResultDto do4102(
            Long caseRecordId, String contractBusNo, String stasType, boolean skipIfCommitted) {
        CaseRecord record = prepareForUpload(caseRecordId);
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();
        if (skipIfCommitted
                && "1".equals(stasType)
                && MrmsSettleUploadFlags.COMMITTED.equals(trimToNull(record.getSettleUploadStatusCode()))) {
            return skippedStepResult();
        }
        // 正式提交须先已上传
        if ("1".equals(stasType)
                && !MrmsSettleUploadFlags.UPLOADED.equals(trimToNull(record.getSettleUploadStatusCode()))
                && !MrmsSettleUploadFlags.COMMITTED.equals(trimToNull(record.getSettleUploadStatusCode()))) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "结算已上传状态才能提交结算清单");
        }
        InsuranceEncounterContext ctx = requireSettledContext(record, contractBusNo);
        Upload4102Validator.validate(ctx.getPsnNo(), ctx.getSetlId(), stasType);
        requireExtensionEnabled();
        MedcasSettleListStatusExtensionIn command = new MedcasSettleListStatusExtensionIn();
        command.setContractBusNo(contractBusNo);
        command.setPsnNo(ctx.getPsnNo());
        command.setSetlId(ctx.getSetlId());
        command.setStasType(stasType);
        command.setOperatorId(operatorId);
        MedcasUploadExtensionOut platform = medcasHomepageUploadExtension.modifySettleListStatus(command);
        String inputJson = JSON.toJSONString(command);
        UploadResultDto dto = persistPlatformResult(
                tenantId,
                record.getId(),
                contractBusNo,
                MedcasInfCodes.SETTLE_LIST_STATUS,
                inputJson,
                platform,
                operatorId,
                false,
                record.getSetlListId());
        if (platform.isSuccess()) {
            if ("1".equals(stasType)) {
                uploadLogDomainService.updateCaseRecordSettleUploadStatus(
                        record.getId(),
                        MrmsSettleUploadFlags.COMMITTED,
                        record.getSetlListId(),
                        false,
                        null,
                        operatorId);
            } else {
                // 撤回为待提交：保留 setl_list_id，状态回 1
                uploadLogDomainService.updateCaseRecordSettleUploadStatus(
                        record.getId(),
                        MrmsSettleUploadFlags.UPLOADED,
                        record.getSetlListId(),
                        false,
                        null,
                        operatorId);
            }
        }
        return dto;
    }

    /**
     * 上报前准备：基础字段齐全即可；上传前不归档不冻结，保证可反复编辑保存。
     *
     * @param caseRecordId 病案主键
     * @return 主档
     */
    private CaseRecord prepareForUpload(Long caseRecordId) {
        // Pre：装载主档与首页，校验上报必备标识
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.requireExistingByCaseRecordId(record.getId());
        if (trimToNull(homepage.getIptMedcasHmpgSn()) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "首页流水号缺失");
        }
        if (trimToNull(record.getMedcasno()) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "病案号缺失");
        }
        return record;
    }

    /**
     * 4401 成功后自动归档冻结（自动管理病案：上传后不可再改）。
     *
     * @param caseRecordId 病案主键
     */
    private void ensureArchivedAfterHomepageUpload(Long caseRecordId) {
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：隐性推进 SUBMIT→RECEIVE→START_QC→ARCHIVE 并冻结首页
        caseRecordDomainService.ensureArchivedAndFrozenForUpload(caseRecordId, operatorId);
    }

    /**
     * 要求已医保结算上下文。
     *
     * @param record        主档
     * @param contractBusNo 合同业务号
     * @return 上下文
     */
    private InsuranceEncounterContext requireSettledContext(CaseRecord record, String contractBusNo) {
        Optional<InsuranceEncounterContext> insCtxOpt = insuranceEncounterLoader.findContext(
                record.getEncounterId(), contractBusNo);
        if (insCtxOpt.isEmpty()) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_PRECONDITION, "病人确认结算后才能上报（缺 mdtrt_id/setl_id）");
        }
        InsuranceEncounterContext ctx = insCtxOpt.get();
        if (trimToNull(ctx.getPsnNo()) == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_PRECONDITION, "医保 psn_no 缺失");
        }
        // 定点编码须来自 chs-contract；fin_contract 列常为空，缺配置时提前阻断
        if (trimToNull(ctx.getFixmedinsCode()) == null) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_PRECONDITION,
                    "定点医药机构编号缺失（请检查 chs-contract.properties 中 contract."
                            + contractBusNo
                            + ".fixmedins_code）");
        }
        return ctx;
    }

    /**
     * 要求 CHS Extension 已启用。
     */
    private void requireExtensionEnabled() {
        if (!medcasHomepageUploadExtension.isEnabled()) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_PRECONDITION, "医保网关未装配，请启用 module-chs-jilin-starter");
        }
    }

    /**
     * 落上传日志；可选回写 4401 主档状态。
     *
     * @param tenantId 租户
     * @param caseRecordId 病案
     * @param contractBusNo 合同
     * @param infno 交易码
     * @param inputJson 请求体
     * @param platform 平台出参
     * @param operatorId 操作人
     * @param updateChsHomepageFlag 是否回写 4401 主档标志
     * @param setlListId 清单号（可选）
     * @return 结果 DTO
     */
    private UploadResultDto persistPlatformResult(
            Long tenantId,
            Long caseRecordId,
            String contractBusNo,
            String infno,
            String inputJson,
            MedcasUploadExtensionOut platform,
            Long operatorId,
            boolean updateChsHomepageFlag,
            String setlListId) {
        Long contractId = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo)
                .map(contract -> contract.getId())
                .orElse(null);
        String status;
        ChsUploadStatus uploadStatus;
        if (platform.isSuccess()) {
            status = ChsUploadStatus.SUCCESS.getCode();
            uploadStatus = ChsUploadStatus.SUCCESS;
        } else {
            status = ChsUploadStatus.FAILED.getCode();
            uploadStatus = ChsUploadStatus.FAILED;
        }
        UploadLog log = uploadLogDomainService.appendLog(
                tenantId,
                caseRecordId,
                contractBusNo,
                contractId,
                infno,
                status,
                digest(inputJson),
                platform.getInfcode(),
                platform.getErrMsg(),
                JSON.toJSONString(platform),
                operatorId);
        if (updateChsHomepageFlag) {
            uploadLogDomainService.updateCaseRecordUploadStatus(
                    caseRecordId, uploadStatus, platform.getErrMsg(), operatorId);
        }
        UploadResultDto dto = new UploadResultDto();
        dto.setStatusCode(status);
        dto.setUploadLogId(String.valueOf(log.getId()));
        dto.setInfcode(platform.getInfcode());
        dto.setErrMsg(platform.getErrMsg());
        dto.setAssembledAt(LocalDateTime.now());
        dto.setSetlListId(setlListId);
        dto.setSkipped(false);
        return dto;
    }

    /**
     * 非医保住院：仅更新主档 4401 状态为 skipped。
     *
     * @param caseRecordId 病案
     * @param operatorId 操作人
     * @return 跳过结果
     */
    private UploadResultDto skippedResult(Long caseRecordId, Long operatorId) {
        uploadLogDomainService.updateCaseRecordUploadStatus(
                caseRecordId, ChsUploadStatus.SKIPPED, null, operatorId);
        UploadResultDto dto = new UploadResultDto();
        dto.setStatusCode(ChsUploadStatus.SKIPPED.getCode());
        dto.setAssembledAt(LocalDateTime.now());
        dto.setSkipped(true);
        return dto;
    }

    /**
     * 一键编排中「已成功侧跳过」结果。
     *
     * @return 跳过结果
     */
    private UploadResultDto skippedStepResult() {
        UploadResultDto dto = new UploadResultDto();
        dto.setStatusCode(ChsUploadStatus.SUCCESS.getCode());
        dto.setAssembledAt(LocalDateTime.now());
        dto.setSkipped(true);
        return dto;
    }

    /**
     * 分步是否视为成功（success 或 skipped）。
     *
     * @param step 分步结果
     * @return true 可继续下一步
     */
    private boolean isStepOk(UploadResultDto step) {
        if (step == null) {
            return false;
        }
        if (Boolean.TRUE.equals(step.getSkipped())) {
            return true;
        }
        return ChsUploadStatus.SUCCESS.getCode().equals(trimToNull(step.getStatusCode()));
    }

    /**
     * 载荷 JSON 序列化。
     *
     * @param payload 对象
     * @param label 标签（错误提示）
     * @return JSON
     */
    private String serialize(Object payload, String label) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, label + " 载荷序列化失败");
        }
    }

    /**
     * 请求体 SHA-256 摘要（截断 128 字符）。
     *
     * @param json JSON
     * @return 摘要
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
            if (full.length() > 128) {
                return full.substring(0, 128);
            }
            return full;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 校验病案主键。
     *
     * @param caseRecordId 主键
     * @return 主键
     */
    private Long requireCaseRecordId(Long caseRecordId) {
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        return caseRecordId;
    }

    /**
     * 显式合同号必填（无病案可解析时）。
     *
     * @param contractBusNo 请求合同号
     * @return 非空合同号
     */
    private String requireExplicitContractBusNo(String contractBusNo) {
        String explicit = trimToNull(contractBusNo);
        if (explicit == null) {
            throw new ValidationException("费用性质 contractBusNo 不能为空");
        }
        return explicit;
    }

    /**
     * 解析费用性质业务号：请求显式传入优先，否则取就诊医保账户合同号。
     *
     * @param encounterId 就诊主键
     * @param contractBusNo 请求中的合同号，可空
     * @return 非空合同业务号
     */
    private String resolveContractBusNo(long encounterId, String contractBusNo) {
        String explicit = trimToNull(contractBusNo);
        if (explicit != null) {
            return explicit;
        }
        Account ybAccount = accountQueryService.getYbAccount(encounterId);
        if (ybAccount == null || trimToNull(ybAccount.getContractNo()) == null) {
            throw new ValidationException("费用性质 contractBusNo 不能为空（就诊无医保账户合同）");
        }
        return ybAccount.getContractNo().trim();
    }
}
