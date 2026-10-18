/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.assembler;

import com.zxplat.plugin.chs.domain.share.enums.ValiFlag;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1312Input;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.catalog.command.Catalog1312FilterCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【1312】医保目录信息查询入参组装（平台报文 data 节点） */
@Component
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class Catalog1312InputAssembler {

    /**
     * 组装 1312 平台查询 {@code data} 节点。
     *
     * @param insuAdmdvs 参保地医保区划
     * @param filter 目录筛选（list_type、hilist_code/name、vali_flag 等）
     * @param resolvedUpdtTime 增量游标 updt_time（缺省取常量默认值）
     * @param pageNum 页码（缺省 1）
     * @param pageSize 页大小（缺省 {@link ChsCatalogConstants#DEFAULT_PLATFORM_PAGE_SIZE}）
     * @return 平台 input DTO（{@code {data:{...}}}） */
    public Cat1312Input assemble(String insuAdmdvs, Catalog1312FilterCommand filter,
                                 String resolvedUpdtTime, Integer pageNum, Integer pageSize) {
        Catalog1312FilterCommand criteria = filter;
        if (criteria == null) {
            criteria = new Catalog1312FilterCommand();
        }
        int platformPageNum;
        if (pageNum != null) {
            platformPageNum = pageNum;
        } else {
            platformPageNum = 1;
        }
        int platformPageSize;
        if (pageSize != null) {
            platformPageSize = pageSize;
        } else {
            platformPageSize = ChsCatalogConstants.DEFAULT_PLATFORM_PAGE_SIZE;
        }
        String valiFlag;
        if (isNotEmpty(criteria.getValiFlag())) {
            valiFlag = criteria.getValiFlag().trim();
        } else {
            valiFlag = ValiFlag.VALID.getCode();
        }
        String updtTime;
        if (isNotEmpty(resolvedUpdtTime)) {
            updtTime = resolvedUpdtTime.trim();
        } else {
            updtTime = ChsCatalogConstants.DEFAULT_CATALOG_UPDT_TIME;
        }
        Cat1312Input.Cat1312Data data = new Cat1312Input.Cat1312Data()
            .setInsuAdmdvs(insuAdmdvs)
            .setListType(trimToNull(criteria.getListType()))
            .setHilistCode(trimToNull(criteria.getHilistCode()))
            .setHilistName(resolveHilistName(criteria))
            .setPinyin(resolvePinyin(criteria))
            .setValiFlag(valiFlag)
            .setChrgitmLv(trimToNull(criteria.getChrgitmLv()))
            .setLmtUsedFlag(trimToNull(criteria.getLmtUsedFlag()))
            .setQueryDate(trimToNull(criteria.getQueryDate()))
            .setBegndate(trimToNull(criteria.getBegndate()))
            .setUpdtTime(updtTime)
            .setPageNum(platformPageNum)
            .setPageSize(platformPageSize);
        Cat1312Input input = new Cat1312Input();
        input.setData(data);
        return input;
    }

    private static String resolveHilistName(Catalog1312FilterCommand filter) {
        if (isNotEmpty(filter.getHilistName())) {
            return filter.getHilistName().trim();
        }
        if (isNotEmpty(filter.getKeyword())) {
            return filter.getKeyword().trim();
        }
        return null;
    }

    private static String resolvePinyin(Catalog1312FilterCommand filter) {
        if (isNotEmpty(filter.getPinyin())) {
            return filter.getPinyin().trim();
        }
        if (isNotEmpty(filter.getKeyword())) {
            return filter.getKeyword().trim();
        }
        return null;
    }
}
