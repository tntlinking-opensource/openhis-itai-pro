/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.mapping.mapper;

import com.zxplat.plugin.chs.application.catalog.nationalcatalog.mapper.InsuranceNationalCatalogMapper;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogMatchCandidateResult;
import com.zxplat.plugin.chs.domain.catalog.result.NationalCatalogItemResult;

/**
 * 目录对码页：候选行与领域结果映射。
 * <p>复用 {@link InsuranceNationalCatalogMapper#toItemResult} 填充基础字段，
 * 得分与命中说明由 {@link com.zxplat.plugin.chs.application.catalog.mapping.support.CatalogMedicationMatchScorer} 填充。</p>
 */
public final class InsuranceCatalogMappingMapper {

    private InsuranceCatalogMappingMapper() {
    }

    /**
     * 将镜像实体映射为对码推荐候选行（基础字段）。
     *
     * @param item 国家目录镜像实体
     * @return 候选行结果（score/matchReason 待 scorer 填充）
     */
    public static CatalogMatchCandidateResult toMatchCandidate(YbNationalCatalogItem item) {
        CatalogMatchCandidateResult candidate = new CatalogMatchCandidateResult();
        NationalCatalogItemResult base = InsuranceNationalCatalogMapper.toItemResult(item);
        if (base != null) {
            candidate.setId(base.getId());
            candidate.setContractId(base.getContractId());
            candidate.setListType(base.getListType());
            candidate.setHilistCode(base.getHilistCode());
            candidate.setHilistName(base.getHilistName());
            candidate.setChrgitmLv(base.getChrgitmLv());
            candidate.setLmtUsedFlag(base.getLmtUsedFlag());
            candidate.setBegndate(base.getBegndate());
            candidate.setEnddate(base.getEnddate());
            candidate.setValiFlag(base.getValiFlag());
            candidate.setPinyin(base.getPinyin());
            candidate.setSpec(base.getSpec());
            candidate.setDosform(base.getDosform());
            candidate.setAprvno(base.getAprvno());
            candidate.setProdentpName(base.getProdentpName());
            candidate.setSourceInfno(base.getSourceInfno());
            candidate.setUpdtTime(base.getUpdtTime());
            candidate.setMinPacCnt(base.getMinPacCnt());
            candidate.setMinPacunt(base.getMinPacunt());
            candidate.setMinPrepunt(base.getMinPrepunt());
            candidate.setMinPrcunt(base.getMinPrcunt());
        }
        return candidate;
    }
}
