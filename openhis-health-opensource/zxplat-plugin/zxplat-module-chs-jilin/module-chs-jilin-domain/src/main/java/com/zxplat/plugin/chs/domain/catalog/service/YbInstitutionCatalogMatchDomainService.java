/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.domain.catalog.command.UpsertInstitutionCatalogMatchCommand;
import com.zxplat.plugin.chs.domain.catalog.entity.YbInstitutionCatalogMatch;
import com.zxplat.plugin.chs.domain.catalog.repository.YbInstitutionCatalogMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 1317 医药机构目录匹配镜像写侧（按业务唯一键 upsert）。
 */
@Service
@RequiredArgsConstructor
public class YbInstitutionCatalogMatchDomainService {

    private final YbInstitutionCatalogMatchRepository repository;

    /**
     * 批量 upsert 1317 行；跳过院内编码或医保码为空的命令。
     *
     * @param contractId 契约主键
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param rows       待写入行
     * @return 实际处理行数
     */
    public int upsertRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            List<UpsertInstitutionCatalogMatchCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (UpsertInstitutionCatalogMatchCommand row : rows) {
            // 缺关键键无法落唯一索引，直接跳过
            if (row == null
                || isEmpty(row.getMedinsListCodg())
                || isEmpty(row.getMedListCodg())
                || isEmpty(row.getListType())) {
                continue;
            }
            String medins = row.getMedinsListCodg().trim();
            String medList = row.getMedListCodg().trim();
            String listType = row.getListType().trim();
            YbInstitutionCatalogMatch entity = repository
                .findByContractIdAndTenantIdAndMedinsListCodgAndMedListCodgAndListTypeAndIsDeletedFalse(
                    contractId, tenantId, medins, medList, listType)
                .orElseGet(() -> newEntity(contractId, tenantId, operatorId, now));
            applyRow(entity, row, medins, medList, listType);
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(operatorId);
            repository.save(entity);
            count++;
        }
        return count;
    }

    /**
     * 新建未落库实体并填审计字段。
     *
     * @param contractId 契约
     * @param tenantId   租户
     * @param operatorId 操作人
     * @param now        当前时间
     * @return 新实体
     */
    private YbInstitutionCatalogMatch newEntity(
            Long contractId, Long tenantId, Long operatorId, LocalDateTime now) {
        YbInstitutionCatalogMatch entity = new YbInstitutionCatalogMatch();
        // 勿手动 setId：AuditedEntityBase 靠 @SnowflakeId + Persistable.isNew 走 persist
        entity.setContractId(contractId);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setIsDeleted(false);
        return entity;
    }

    /**
     * 将命令字段覆盖到实体（保留主键与审计创建信息）。
     *
     * @param entity   目标实体
     * @param row      命令
     * @param medins   已 trim 院内码
     * @param medList  已 trim 医保码
     * @param listType 已 trim 类别
     */
    private static void applyRow(
            YbInstitutionCatalogMatch entity,
            UpsertInstitutionCatalogMatchCommand row,
            String medins,
            String medList,
            String listType) {
        entity.setFixmedinsCode(row.getFixmedinsCode());
        entity.setMedinsListCodg(medins);
        entity.setMedinsListName(row.getMedinsListName());
        entity.setInsuAdmdvs(row.getInsuAdmdvs());
        entity.setListType(listType);
        entity.setMedListCodg(medList);
        entity.setBegndate(row.getBegndate());
        entity.setEnddate(row.getEnddate());
        entity.setAprvno(row.getAprvno());
        entity.setDosform(row.getDosform());
        entity.setExctCont(row.getExctCont());
        entity.setItemCont(row.getItemCont());
        entity.setPrcunt(row.getPrcunt());
        entity.setSpec(row.getSpec());
        entity.setPacspec(row.getPacspec());
        entity.setMemo(row.getMemo());
        entity.setValiFlag(row.getValiFlag());
        entity.setRid(row.getRid());
        entity.setUpdtTime(row.getUpdtTime());
        entity.setPoolareaNo(row.getPoolareaNo());
    }
}
