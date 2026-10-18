/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.nationalcatalog.mapper;

import com.zxplat.plugin.chs.application.catalog.nationalcatalog.request.QueryNationalCatalogRequest;
import com.zxplat.plugin.chs.application.catalog.nationalcatalog.response.NationalCatalogItemResponse;
import com.zxplat.plugin.chs.application.catalog.nationalcatalog.response.QueryNationalCatalogResponse;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.zxplat.plugin.chs.domain.catalog.result.NationalCatalogItemResult;
import org.springframework.data.domain.Page;

/**
 * 国家目录管理页：UI 入参与领域查询/结果映射。
 * <p>纯字段搬运与分页参数转换，不含业务判断。</p>
 */
public final class InsuranceNationalCatalogMapper {

    private InsuranceNationalCatalogMapper() {
    }

    /**
     * 将分页查询入参转为领域 {@link NationalCatalogPageQuery}。
     *
     * @param request    UI 分页查询入参
     * @param contractId 已解析的契约主键
     * @param tenantId   当前租户 ID
     * @return 领域分页查询对象
     */
    public static NationalCatalogPageQuery toPageQuery(
            QueryNationalCatalogRequest request,
            Long contractId,
            Long tenantId) {
        NationalCatalogPageQuery query = new NationalCatalogPageQuery();
        query.setTenantId(tenantId);
        query.setContractId(contractId);
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setKeyword(request.getKeyword());
        query.setListType(request.getListType());
        query.setHilistCode(request.getHilistCode());
        query.setSourceInfno(request.getSourceInfno());
        if (Boolean.TRUE.equals(request.getDedupeByHilistCode())) {
            query.setDedupeByHilistCode(true);
        }
        query.setValiFlag(request.getValiFlag());
        query.setAprvno(request.getAprvno());
        query.setSpec(request.getSpec());
        if (Boolean.TRUE.equals(request.getExcludePlatformExpired())) {
            query.setExcludePlatformExpired(true);
        }
        String source = request.getSourceInfno() == null ? null : request.getSourceInfno().trim();
        if ("1301".equals(source) && !Boolean.TRUE.equals(request.getDedupeByHilistCode())) {
            query.setPickerMode1301Detail(true);
        }
        if ("1312".equals(source)) {
            query.setPickerMode1312Only(true);
        }
        return query;
    }

    /**
     * 将分页实体映射为 UI 摘要出参。
     *
     * @param page     领域分页结果
     * @param pageNo   当前页码（从 1 起）
     * @param pageSize 每页条数
     * @return UI 分页出参
     */
    public static QueryNationalCatalogResponse toResponse(Page<YbNationalCatalogItem> page, int pageNo, int pageSize) {
        QueryNationalCatalogResponse response = new QueryNationalCatalogResponse()
            .setTotal(page.getTotalElements())
            .setPageNo(pageNo)
            .setPageSize(pageSize);
        for (YbNationalCatalogItem row : page.getContent()) {
            if (row == null) {
                continue;
            }
            response.getRows().add(new NationalCatalogItemResponse()
                .setListType(row.getListType())
                .setHilistCode(row.getHilistCode())
                .setHilistName(row.getHilistName())
                .setChrgitmLv(row.getChrgitmLv())
                .setValiFlag(row.getValiFlag()));
        }
        return response;
    }

    /**
     * 将镜像实体映射为 API 行结果（完整字段）。
     *
     * @param item 国家目录镜像实体
     * @return 完整行结果，item 为 null 时返回 null
     */
    public static NationalCatalogItemResult toItemResult(YbNationalCatalogItem item) {
        if (item == null) {
            return null;
        }
        return new NationalCatalogItemResult()
            .setId(item.getId())
            .setContractId(item.getContractId())
            .setListType(item.getListType())
            .setHilistCode(item.getHilistCode())
            .setHilistName(item.getHilistName())
            .setChrgitmLv(item.getChrgitmLv())
            .setLmtUsedFlag(item.getLmtUsedFlag())
            .setBegndate(item.getBegndate())
            .setEnddate(item.getEnddate())
            .setValiFlag(item.getValiFlag())
            .setPinyin(item.getPinyin())
            .setSpec(item.getSpec())
            .setDosform(item.getDosform())
            .setAprvno(item.getAprvno())
            .setProdentpName(item.getProdentpName())
            .setSourceInfno(item.getSourceInfno())
            .setUpdtTime(item.getUpdtTime())
            .setMinPacCnt(item.getMinPacCnt())
            .setMinPacunt(item.getMinPacunt())
            .setMinPrepunt(item.getMinPrepunt())
            .setMinPrcunt(item.getMinPrcunt());
    }
}
