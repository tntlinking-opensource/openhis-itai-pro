/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.weistat.service;

import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepagePageDto;
import com.openhis.health.application.mrms.caserecord.homepage.request.LoadPageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.service.MrmsCaseRecordHomepageAppService;
import com.openhis.health.application.mrms.upload.weistat.WeistatN041Constants;
import com.openhis.health.application.mrms.upload.weistat.request.ExportN041CsvRequest;
import com.openhis.health.application.mrms.upload.weistat.response.ExportN041CsvResponse;
import com.openhis.health.application.mrms.upload.weistat.support.WeistatN041CsvWriter;
import com.openhis.health.application.mrms.upload.weistat.support.WeistatN041RowAssembler;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 卫统 N041 住院病案首页 CSV 导出（IF：mrmsWeistatExport/exportN041Csv）。
 *
 * <p>与 HQMS / 医保 medcas 分管道；取数复用 {@link MrmsCaseRecordHomepageAppService#loadPage}，
 * 保证与病案编辑页、结算清单同源。文档：11-卫统N041导出-字段对照.md。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsWeistatExportAppService {

    private final MrmsCaseRecordHomepageAppService mrmsCaseRecordHomepageAppService;

    private final WeistatN041RowAssembler weistatN041RowAssembler;

    private final OrganizationQueryService organizationQueryService;

    /**
     * 按病案主键批量导出卫统 N041 CSV。
     *
     * @param request 多选病案主键
     * @return 文件名 + CSV 字节
     */
    /**
     * 非 readOnly：复用 loadPage 可能软刷首页/建空壳首页。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExportN041CsvResponse exportN041Csv(ExportN041CsvRequest request) {
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
        if (idSet.size() > WeistatN041Constants.MAX_EXPORT_CASE_RECORDS) {
            throw new ValidationException(
                    "单次最多导出 " + WeistatN041Constants.MAX_EXPORT_CASE_RECORDS + " 条病案");
        }
        // Pre：机构名作文件名与 USERNAME
        String hospitalName = resolveHospitalName();
        // Cmd：逐条 loadPage → 组装 N041 行
        List<Map<String, String>> rows = new ArrayList<>();
        for (Long caseRecordId : idSet) {
            LoadPageRequest load = new LoadPageRequest();
            load.setCaseRecordId(caseRecordId);
            HomepagePageDto page = mrmsCaseRecordHomepageAppService.loadPage(load);
            rows.add(weistatN041RowAssembler.assemble(page, hospitalName));
        }
        // Asm：写 CSV 字节与响应
        byte[] body = WeistatN041CsvWriter.write(rows);
        ExportN041CsvResponse response = new ExportN041CsvResponse();
        response.setFileName(WeistatN041Constants.FILE_PREFIX + sanitizeFileName(hospitalName)
                + WeistatN041Constants.FILE_SUFFIX);
        response.setContentType(WeistatN041Constants.CSV_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(rows.size());
        log.info("卫统 N041 导出完成 rows={} hospital={}", rows.size(), hospitalName);
        return response;
    }

    /**
     * 解析当前租户启用医院名称；无则占位「医院」。
     *
     * @return 非空机构名
     */
    private String resolveHospitalName() {
        List<Organization> hospitals = organizationQueryService.listActiveHospitals();
        if (hospitals == null || hospitals.isEmpty()) {
            return "医院";
        }
        Organization first = hospitals.get(0);
        if (first == null || first.getName() == null || first.getName().isBlank()) {
            return "医院";
        }
        return first.getName().trim();
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
