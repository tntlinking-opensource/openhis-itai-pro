/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.coding.service;

import com.openhis.health.application.mrms.caserecord.coding.dto.IcdItemDto;
import com.openhis.health.application.mrms.caserecord.coding.request.SearchIcdRequest;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

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
     * 按关键字检索 ICD-9-CM-3 手术编码（首页手术 picker；AS-MRMS-HP-001 IF-MRMS-HP-PR-02）。
     *
     * <p>仅 ACTIVE 国临 PD；回传 id/国临码/名/ybNo（可空提示未对码）。</p>
     *
     * @param request 关键字与条数上限
     * @return 编码项列表；关键字为空时返回空列表
     */
    public List<IcdItemDto> searchIcd9Cm3(SearchIcdRequest request) {
        // Gate：无关键字时不查库
        String key = trimToNull(request.getKeyword());
        if (key == null) {
            return Collections.emptyList();
        }
        int limit = resolveLimit(request.getLimit());
        // Pre：仅国家临床版手术 3.0（病案首页用国临码，医保上传另映射 yb_no）
        List<ProcedureDefinitionSearchItemQueryModel> items =
                procedureDefinitionQueryService.searchClinicalProcV3ByKeyword(key);
        // Asm：截断并映射 picker 行（含 PD id / ybNo）
        return toIcd9Cm3Dtos(items, limit);
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
