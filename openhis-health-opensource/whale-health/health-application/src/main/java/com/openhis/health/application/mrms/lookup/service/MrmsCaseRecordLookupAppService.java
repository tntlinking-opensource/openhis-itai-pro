/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.lookup.service;

import com.openhis.health.application.mrms.homepage.dto.CaseRecordDetailDto;
import com.openhis.health.application.mrms.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.homepage.dto.PdfGenerateResultDto;
import com.openhis.health.application.mrms.homepage.mapper.CaseRecordDetailMapper;
import com.openhis.health.application.mrms.homepage.mapper.HomepageMapper;
import com.openhis.health.application.mrms.homepage.request.GenerateHomepagePdfRequest;
import com.openhis.health.application.mrms.homepage.support.HomepageOpenPdfRenderer;
import com.openhis.health.application.mrms.lookup.request.GetByEncounterIdRequest;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.module.common.validator.MrmsOperatorResolver;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 住院医生站查档/打印应用服务（IF-MR-Lookup）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordLookup/*}。按就诊查病案主档、
 * 生成首页 PDF；PDF 复用 homepage 模块 Mapper 与渲染器，不跨 IF 调 AppService。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordLookupAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final CaseRecordDetailMapper caseRecordDetailMapper;
    private final HomepageMapper homepageMapper;
    private final HomepageOpenPdfRenderer homepageOpenPdfRenderer;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 按就诊查病案主档。
     *
     * @param request 含就诊主键
     * @return 主档详情；不存在时 null
     */
    public CaseRecordDetailDto getByEncounterId(GetByEncounterIdRequest request) {
        // Gate：就诊主键必填
        if (request.getEncounterId() == null) {
            throw new ValidationException("就诊主键不能为空");
        }
        // Pre：按租户+就诊查主档
        Long tenantId = operatorResolver.requireTenantId();
        CaseRecord record = caseRecordDomainService.findByEncounterId(tenantId, request.getEncounterId()).orElse(null);
        // 就诊尚未建档，查档返回空而非抛错
        if (record == null) {
            return null;
        }
        // Asm：映射详情 DTO
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 生成病案首页 PDF 预览（与 homepage IF 同渲染路径）。
     *
     * @param request 含病案主键
     * @return PDF Base64 与页数
     */
    public PdfGenerateResultDto generateHomepagePdf(GenerateHomepagePdfRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：加载主档与首页子表
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        HomepageDto dto = homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId()));
        // Asm：OpenPDF 渲染
        return homepageOpenPdfRenderer.render(dto);
    }
}
