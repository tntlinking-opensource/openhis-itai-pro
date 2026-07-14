/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.volume.support;

import com.openhis.health.application.mrms.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.homepage.dto.PdfGenerateResultDto;
import com.openhis.health.application.mrms.homepage.mapper.HomepageMapper;
import com.openhis.health.application.mrms.homepage.support.HomepageOpenPdfRenderer;
import com.openhis.health.application.mrms.volume.dto.VolumePdfResultDto;
import com.openhis.health.application.mrms.volume.request.PreviewVolumeRequest;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentListExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 卷宗 PDF 编排（volume IF {@code support}）：首页 OpenPDF + EMR 文书导出合并。
 *
 * <p>读病案/首页经 DomainService（B.6.4）；EMR 导出经 {@link EmrDocumentExtension} Extension。</p>
 */
@Component
@RequiredArgsConstructor
public class VolumePdfComposer {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final HomepageMapper homepageMapper;
    private final HomepageOpenPdfRenderer homepageOpenPdfRenderer;
    private final PdfMergeUtil pdfMergeUtil;
    private final EmrDocumentExtension emrDocumentExtension;

    /**
     * 按请求组装卷宗 PDF（可选含首页 + 指定或全部可打印 EMR 文书）。
     *
     * @param request 预览请求（病案主键、是否含首页、文书 ID 列表）
     * @param operatorId 操作人，用于懒创建首页
     * @return Base64 PDF 与页数
     */
    public VolumePdfResultDto compose(PreviewVolumeRequest request, Long operatorId) {
        // Gate：病案主键必填
        Long caseRecordId = request.getCaseRecordId();
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：加载主档；解析是否含首页（默认 true）
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        boolean includeHomepage = request.getIncludeHomepage() == null || Boolean.TRUE.equals(request.getIncludeHomepage());
        List<byte[]> parts = new ArrayList<>();

        // Pre：渲染首页 PDF 段
        if (includeHomepage) {
            CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                    record.getId(), record.getTenantId(), operatorId);
            HomepageDto dto = homepageMapper.toDto(
                    homepage,
                    caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                    caseRecordHomepageDomainService.listProcedures(record.getId()));
            PdfGenerateResultDto homepagePdf = homepageOpenPdfRenderer.render(dto);
            parts.add(pdfMergeUtil.decodeBase64(homepagePdf.getPdfBase64()));
        }

        List<Long> documentIds = resolveDocumentIds(request, record);
        // Pre：导出 EMR 文书 PDF 段
        if (emrDocumentExtension.isEnabled()) {
            for (Long documentId : documentIds) {
                parts.add(emrDocumentExtension.exportPdf(documentId));
            }
        } else if (!documentIds.isEmpty()) {
            // 请求含文书但 EMR 未启用，阻断预览
            throw new ValidationException("EMR 模块未启用，无法导出文书 PDF");
        }

        // Gate：至少有一段可合并内容
        if (parts.isEmpty()) {
            throw new ValidationException("没有可预览的卷宗内容");
        }

        // Asm：合并 PDF 并编码出参
        byte[] merged = pdfMergeUtil.mergePdfBytes(parts);
        VolumePdfResultDto result = new VolumePdfResultDto();
        result.setPdfBase64(Base64.getEncoder().encodeToString(merged));
        result.setPageCount(pdfMergeUtil.countPages(merged));
        result.setRenderedAt(Instant.now());
        return result;
    }

    /**
     * 解析待导出文书 ID：请求显式指定时直接用，否则取就诊下全部可打印文书（去重保序）。
     *
     * @param request 预览请求
     * @param record 病案主档（取 patientId/encounterId）
     * @return 文书主键列表，永不为 null
     */
    private List<Long> resolveDocumentIds(PreviewVolumeRequest request, CaseRecord record) {
        // 请求显式指定文书列表时不再自动展开
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            return request.getDocumentIds();
        }
        if (!emrDocumentExtension.isEnabled()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (EmrDocumentQueryExtensionOut item : emrDocumentExtension.listByEncounter(
                new EmrDocumentListExtensionIn()
                    .setPatientId(record.getPatientId())
                    .setEncounterId(record.getEncounterId()))) {
            // 不可打印或无 id 的文书不纳入默认列表
            if (!item.isPrintable() || item.getDocumentId() == null) {
                continue;
            }
            // 去重保序
            if (seen.add(item.getDocumentId())) {
                ids.add(item.getDocumentId());
            }
        }
        return ids;
    }
}
