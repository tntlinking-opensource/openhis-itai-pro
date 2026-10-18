/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.coding.service;

import com.zxplat.application.mrms.caserecord.coding.dto.IcdItemDto;
import com.zxplat.application.mrms.caserecord.coding.request.SearchIcdRequest;
import com.zxplat.application.mrms.caserecord.coding.response.SearchIcdPageResponse;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 ICD 选码应用服务（IF-MR-Coding）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordCoding/*}。为首页诊断/手术 picker 提供
 * ICD-10 疾病与 ICD-9-CM-3 手术编码模糊检索，只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordCodingAppService {

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;

    /**
     * 按关键字检索 ICD-10 疾病编码（首页诊断 picker）。
     *
     * @param request 关键字与条数上限
     * @return 编码项列表；关键字为空时返回空列表
     */
    public List<IcdItemDto> searchIcd10(SearchIcdRequest request) {
        // Gate：无关键字时不查库，避免全表扫描
        String key = trimToNull(request.getKeyword());
        if (key == null) {
            return Collections.emptyList();
        }
        int limit = resolveLimit(request.getLimit());
        // Pre：仅国家临床版 2.0（病案首页用国临码，医保上传另映射 yb_no）
        List<ConditionDefinitionSearchItemQueryModel> items =
                conditionDefinitionQueryService.searchClinicalV2ByKeyword(key);
        // Asm：截断并映射 picker 行
        return toIcd10Dtos(items, limit);
    }

    /**
     * 按关键字分页检索手术编码（首页手术 picker；AS-MRMS-HP-001 IF-MRMS-HP-PR-02）。
     *
     * <p>优先 ACTIVE 国临 PD（clinical_proc_v3）；若国临目录为空则回落已启用全来源术式
     *（与医生站术式下拉同口径，避免院点只导了手术操作目录时下拉空白）。
     * 关键字可空——打开下拉即可分页浏览。回传 id/码/名/ybNo（可空提示未对码）。</p>
     *
     * @param request 关键字与分页；keyword 可空
     * @return 分页候选项
     */
    public SearchIcdPageResponse searchIcd9Cm3(SearchIcdRequest request) {
        int pageNo = resolvePageNo(request.getPageNo());
        int pageSize = resolvePageSize(request);
        String key = trimToNull(request.getKeyword());
        PageRequest pageable = PageRequest.of(pageNo - 1, pageSize);
        // Pre：先查国临手术 3.0（空关键字=浏览）
        Page<ProcedureDefinitionSearchItemQueryModel> page =
                procedureDefinitionQueryService.pageClinicalProcV3ByKeyword(key, pageable);
        // 院点未导国临时回落全来源 ACTIVE，避免下拉「暂无数据」
        if (page.getTotalElements() == 0L) {
            page = procedureDefinitionQueryService.pageActiveByKeyword(key, pageable);
        }
        // Asm：映射 picker 行（含 PD id / ybNo）
        SearchIcdPageResponse response = new SearchIcdPageResponse();
        response.setItems(toIcd9Cm3Dtos(page.getContent(), pageSize));
        response.setTotalCount(page.getTotalElements());
        return response;
    }

    /**
     * 解析页码：默认 1。
     *
     * @param pageNo 请求页码，可为 null
     * @return 合法页码（≥1）
     */
    private int resolvePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1;
        }
        return pageNo;
    }

    /**
     * 解析每页条数：优先 pageSize，其次 limit，默认 20，最大 100。
     *
     * @param request 检索请求
     * @return 合法 pageSize
     */
    private int resolvePageSize(SearchIcdRequest request) {
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            return resolveLimit(request.getPageSize());
        }
        return resolveLimit(request.getLimit());
    }

    /**
     * 解析条数上限：默认 20，最大 100。
     *
     * @param limit 请求条数，可为 null
     * @return 合法上限
     */
    private int resolveLimit(Integer limit) {
        // 默认 20 条
        if (limit == null || limit < 1) {
            return 20;
        }
        // 上限 100，防止 picker 一次拉取过多
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    /**
     * 国临手术检索结果转 picker DTO（含 PD id / ybNo），按 limit 截断。
     *
     * @param items 手术定义检索行
     * @param limit 最大条数
     * @return picker 行列表
     */
    private List<IcdItemDto> toIcd9Cm3Dtos(List<ProcedureDefinitionSearchItemQueryModel> items, int limit) {
        List<IcdItemDto> result = new ArrayList<>();
        // 无检索命中时返回空列表供 picker 展示「无数据」
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (ProcedureDefinitionSearchItemQueryModel item : items) {
            // 达到条数上限后截断，避免 picker 一次过大
            if (result.size() >= limit) {
                break;
            }
            IcdItemDto dto = new IcdItemDto();
            dto.setId(item.getId());
            dto.setCode(item.getProcedureCode());
            dto.setName(item.getName());
            dto.setPyStr(item.getPyStr());
            // 可空：未对码仍可选入首页；上传侧阻断（BR-MRMS-HP-PR-008）
            dto.setYbNo(item.getYbNo());
            result.add(dto);
        }
        return result;
    }

    /**
     * ICD-10 检索结果转 picker DTO，按 limit 截断。
     *
     * @param items 疾病定义检索行
     * @param limit 最大条数
     * @return picker 行列表
     */
    private List<IcdItemDto> toIcd10Dtos(List<ConditionDefinitionSearchItemQueryModel> items, int limit) {
        List<IcdItemDto> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (ConditionDefinitionSearchItemQueryModel item : items) {
            if (result.size() >= limit) {
                break;
            }
            IcdItemDto dto = new IcdItemDto();
            dto.setCode(item.getConditionCode());
            dto.setName(item.getName());
            dto.setPyStr(item.getPyStr());
            result.add(dto);
        }
        return result;
    }
}
