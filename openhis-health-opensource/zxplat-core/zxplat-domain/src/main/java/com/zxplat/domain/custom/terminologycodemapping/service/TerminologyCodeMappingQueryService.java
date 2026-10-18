/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.terminologycodemapping.service;

import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.custom.terminologycodemapping.entity.TerminologyCodeMapping;
import com.zxplat.domain.custom.terminologycodemapping.queries.model.TerminologyMappingQueryModel;
import com.zxplat.domain.custom.terminologycodemapping.repository.TerminologyCodeMappingRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 院内术语与外部编码体系对照关系的读侧查询服务。
 *
 * <p>职责：支持分页维护界面与双向 lookup（源码→目标码、目标码→源码）；仅查询不写库。</p>
 *
 * <p>聚合边界：仅本聚合 {@link TerminologyCodeMappingRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class TerminologyCodeMappingQueryService {

    private final TerminologyCodeMappingRepository repository;

    /**
     * 按来源表与来源项编号模糊分页查询映射记录，创建时间降序。
     *
     * @param sourceItemTable 来源业务表名
     * @param sourceItemNo 来源项编号片段，null 当作空串
     * @param pageNo 页码（0 基），null 为首页
     * @param pageSize 页大小，null 时用批量上限
     * @return 分页读模型
     */
    public Page<TerminologyMappingQueryModel> getTerminologyCodeMappingPage(
        String sourceItemTable,
        String sourceItemNo,
        Integer pageNo,
        Integer pageSize
    ) {
        int normalizedPageNo;
        if (pageNo == null) {
            normalizedPageNo = 0;
        } else {
            normalizedPageNo = pageNo;
        }
        int normalizedPageSize;
        if (pageSize == null) {
            normalizedPageSize = GeneralSharedConstants.BATCH_LIMIT;
        } else {
            normalizedPageSize = pageSize;
        }
        // 排序与 LIMIT/OFFSET 下推到数据库
        Pageable pageable = PageRequest.of(normalizedPageNo, normalizedPageSize, Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        String normalizedSourceItemNo;
        // 编号条件 null 视为「不按编号片段过滤」
        if (sourceItemNo == null) {
            normalizedSourceItemNo = "";
        } else {
            normalizedSourceItemNo = sourceItemNo;
        }
        Page<TerminologyCodeMapping> page = repository.findAllBySourceItemTableAndSourceItemNoContaining(
            sourceItemTable,
            normalizedSourceItemNo,
            pageable
        );
        return page.map(this::toQueryModel);
    }

    /**
     * 按来源表与来源项精确查找启用中的映射，得到对应目标编码列表（对接上报/对码）。
     *
     * @param sourceItemTable 来源业务表名
     * @param sourceItemNo 来源项业务编号
     * @return 命中映射集合，可能多条
     */
    public List<TerminologyMappingQueryModel> lookupTargetCode(String sourceItemTable, String sourceItemNo) {
        List<TerminologyCodeMapping> rows = repository.findAllBySourceItemTableAndSourceItemNoAndActiveFlagTrue(
            sourceItemTable,
            sourceItemNo
        );
        List<TerminologyMappingQueryModel> result = new ArrayList<>();
        for (TerminologyCodeMapping row : rows) {
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 按目标项编号反查来源项（例如医保码反查院内码），仅返回仍启用映射。
     *
     * @param sourceItemTable 来源业务表名（限定映射域）
     * @param targetItemNo 目标体系中的项编号
     * @return 命中映射集合
     */
    public List<TerminologyMappingQueryModel> lookupSourceCode(String sourceItemTable, String targetItemNo) {
        List<TerminologyCodeMapping> rows = repository.findAllBySourceItemTableAndTargetItemNoAndActiveFlagTrue(
            sourceItemTable,
            targetItemNo
        );
        List<TerminologyMappingQueryModel> result = new ArrayList<>();
        for (TerminologyCodeMapping row : rows) {
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 按主键加载单条映射维护记录。
     *
     * @param id 主键
     * @return 读模型
     * @throws BusinessException 记录不存在
     */
    public TerminologyMappingQueryModel getById(Long id) {
        TerminologyCodeMapping row = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_NOT_FOUND, "术语映射不存在"));
        return this.toQueryModel(row);
    }

    /**
     * 实体到读模型拷贝。
     *
     * @param row 映射实体
     * @return 查询模型
     */
    private TerminologyMappingQueryModel toQueryModel(TerminologyCodeMapping row) {
        TerminologyMappingQueryModel model = new TerminologyMappingQueryModel();
        model.setId(row.getId());
        model.setSourceItemTable(row.getSourceItemTable());
        model.setSourceItemNo(row.getSourceItemNo());
        model.setTargetItemNo(row.getTargetItemNo());
        model.setTargetName(row.getTargetName());
        model.setActiveFlag(row.getActiveFlag());
        return model;
    }
}
