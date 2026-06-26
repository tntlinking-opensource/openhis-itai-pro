/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.lookup.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.application.homepage.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.homepage.dto.PdfGenerateResultDto;
import com.openhis.whale.mrms.application.homepage.request.GenerateHomepagePdfRequest;
import com.openhis.whale.mrms.application.homepage.service.MrmsCaseRecordHomepageAppService;
import com.openhis.whale.mrms.application.lookup.request.GetByEncounterIdRequest;
import com.openhis.whale.mrms.application.shared.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.support.MrmsOperatorResolver;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.service.CaseRecordDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 住院医生站查档/打印 AppService → {@code POST /api/app/mrmsCaseRecordLookup/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordLookupAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordAssembler caseRecordAssembler;
    private final MrmsOperatorResolver operatorResolver;
    private final MrmsCaseRecordHomepageAppService homepageAppService;

    /**
     * 按就诊查病案主档。
     *
     * @param request 请求
     * @return 主档详情；不存在时 null
     */
    public CaseRecordDetailDto getByEncounterId(GetByEncounterIdRequest request) {
        if (request.getEncounterId() == null) {
            throw new ValidationException("就诊主键不能为空");
        }
        Long tenantId = operatorResolver.requireTenantId();
        CaseRecord record = caseRecordDomainService.findByEncounterId(tenantId, request.getEncounterId()).orElse(null);
        if (record == null) {
            return null;
        }
        return caseRecordAssembler.toDetail(record);
    }

    /**
     * 生成首页 PDF（复用首页页渲染器）。
     *
     * @param request 请求
     * @return PDF 结果
     */
    public PdfGenerateResultDto generateHomepagePdf(GenerateHomepagePdfRequest request) {
        return homepageAppService.generateHomepagePdf(request);
    }
}
