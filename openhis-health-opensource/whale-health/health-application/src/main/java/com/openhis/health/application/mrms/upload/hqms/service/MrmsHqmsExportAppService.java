/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.hqms.service;

import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepagePageDto;
import com.openhis.health.application.mrms.caserecord.homepage.request.LoadPageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.service.MrmsCaseRecordHomepageAppService;
import com.openhis.health.application.mrms.upload.hqms.HqmsConstants;
import com.openhis.health.application.mrms.upload.hqms.request.ExportHqmsCsvRequest;
import com.openhis.health.application.mrms.upload.hqms.response.ExportHqmsCsvResponse;
import com.openhis.health.application.mrms.upload.hqms.support.HqmsCsvWriter;
import com.openhis.health.application.mrms.upload.hqms.support.HqmsOrgContext;
import com.openhis.health.application.mrms.upload.hqms.support.HqmsRowAssembler;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * HQMS 住院病案首页 CSV 导出（IF：mrmsHqmsExport/exportHqmsCsv）。
 *
 * <p>与卫统 weistat / 医保 medcas 分管道；取数复用 {@link MrmsCaseRecordHomepageAppService#loadPage}。
 * 文档：12-HQMS导出-字段对照.md。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsHqmsExportAppService {

    private final MrmsCaseRecordHomepageAppService mrmsCaseRecordHomepageAppService;

    private final HqmsRowAssembler hqmsRowAssembler;

    private final OrganizationQueryService organizationQueryService;

    /**
     * 按病案主键批量导出 HQMS CSV。
     *
     * <p>非 readOnly：复用 loadPage 可能软刷首页/建空壳首页。</p>
     *
     * @param request 多选病案主键
     * @return 文件名 + CSV 字节
     */
    @Transactional(rollbackFor = Exception.class)
    public ExportHqmsCsvResponse exportHqmsCsv(ExportHqmsCsvRequest request) {
        // Gate：主键列表非空且去重后不超过批上限
        if (request == null || request.getCaseRecordIds() == null || request.getCaseRecordIds().isEmpty()) {
            throw new ValidationException("请先勾选需要导出的病案");
        }
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (Long id : request.getCaseRecordIds()) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            throw new ValidationException("请先勾选需要导出的病案");
        }
        if (idSet.size() > HqmsConstants.MAX_EXPORT_CASE_RECORDS) {
            throw new ValidationException(
                    "单次最多导出 " + HqmsConstants.MAX_EXPORT_CASE_RECORDS + " 条病案");
        }
        // Pre：机构代码/名称（A01/A02 与文件名）
        HqmsOrgContext org = resolveOrgContext();
        // Cmd：逐条 loadPage → 组装 HQMS 行
        List<Map<String, String>> rows = new ArrayList<>();
        for (Long caseRecordId : idSet) {
            LoadPageRequest load = new LoadPageRequest();
            load.setCaseRecordId(caseRecordId);
            HomepagePageDto page = mrmsCaseRecordHomepageAppService.loadPage(load);
            rows.add(hqmsRowAssembler.assemble(page, org));
        }
        // Asm：写 CSV 字节与响应
        byte[] body = HqmsCsvWriter.write(rows);
        ExportHqmsCsvResponse response = new ExportHqmsCsvResponse();
        response.setFileName(HqmsConstants.FILE_PREFIX + sanitizeFileName(org.orgName())
                + HqmsConstants.FILE_SUFFIX);
        response.setContentType(HqmsConstants.CSV_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(rows.size());
        log.info("HQMS 导出完成 rows={} hospital={}", rows.size(), org.orgName());
        return response;
    }

    /**
     * 解析当前租户启用医院 → HQMS 机构上下文。
     *
     * @return 非空上下文
     */
    private HqmsOrgContext resolveOrgContext() {
        List<Organization> hospitals = organizationQueryService.listActiveHospitals();
        if (hospitals == null || hospitals.isEmpty()) {
            return new HqmsOrgContext(HqmsConstants.DASH, "医院");
        }
        Organization first = hospitals.get(0);
        if (first == null) {
            return new HqmsOrgContext(HqmsConstants.DASH, "医院");
        }
        String name = first.getName();
        if (name == null || name.isBlank()) {
            name = "医院";
        } else {
            name = name.trim();
        }
        // A01：优先医保机构标识 medinsId，其次科室/医院 ybNo（对齐国基 orgybCode 习惯）
        String code = StringUtils.trimToNull(first.getMedinsId());
        if (code == null) {
            code = StringUtils.trimToNull(first.getYbNo());
        }
        if (code == null) {
            code = HqmsConstants.DASH;
        }
        return new HqmsOrgContext(code, name);
    }

    /**
     * 去掉文件名非法字符，避免下载失败。
     *
     * @param name 机构名
     * @return 安全片段
     */
    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "医院";
        }
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
