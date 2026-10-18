/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.command.Refresh1317Command;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.share.enums.ValiFlag;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1317Input;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 【1317】医药机构目录匹配信息查询入参组装（平台报文 data 节点）。
 */
@Component
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class Catalog1317InputAssembler {

    /**
     * 组装 1317 平台查询 {@code data} 节点。
     *
     * @param fixmedinsCode 定点医药机构编号
     * @param insuAdmdvs    参保地医保区划；全量同步请传 null（老插件不传此字段，传了会滤掉历史对照）
     * @param command       刷新筛选（list_type、vali_flag、关键字等）
     * @param pageNum       页码（缺省 1）
     * @param pageSize      页大小（缺省 {@link ChsCatalogConstants#DEFAULT_PLATFORM_PAGE_SIZE}）
     * @return 平台 input DTO（{@code {data:{...}}}）
     */
    public Cat1317Input assemble(
            String fixmedinsCode,
            String insuAdmdvs,
            Refresh1317Command command,
            Integer pageNum,
            Integer pageSize) {
        Refresh1317Command criteria = command;
        if (criteria == null) {
            criteria = new Refresh1317Command();
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
        if (isNotEmpty(criteria.getUpdtTime())) {
            updtTime = criteria.getUpdtTime().trim();
        } else {
            // 1317 全量：比 1312 默认更早，避免漏掉早期对照
            updtTime = ChsCatalogConstants.DEFAULT_1317_UPDT_TIME;
        }
        Cat1317Input.Cat1317Data data = new Cat1317Input.Cat1317Data()
            .setQueryDate(trimToNull(criteria.getQueryDate()))
            .setFixmedinsCode(trimToNull(fixmedinsCode))
            .setMedinsListCodg(trimToNull(criteria.getMedinsListCodg()))
            .setMedinsListName(resolveMedinsListName(criteria))
            .setInsuAdmdvs(trimToNull(insuAdmdvs))
            .setListType(trimToNull(criteria.getListType()))
            .setMedListCodg(trimToNull(criteria.getMedListCodg()))
            .setBegndate(trimToNull(criteria.getBegndate()))
            .setValiFlag(valiFlag)
            .setUpdtTime(updtTime)
            .setPageNum(platformPageNum)
            .setPageSize(platformPageSize);
        Cat1317Input input = new Cat1317Input();
        input.setData(data);
        return input;
    }

    /**
     * 解析院内目录名称筛选：显式名称优先，否则用关键字。
     *
     * @param criteria 刷新命令
     * @return 名称或 null
     */
    private static String resolveMedinsListName(Refresh1317Command criteria) {
        if (isNotEmpty(criteria.getMedinsListName())) {
            return criteria.getMedinsListName().trim();
        }
        if (isNotEmpty(criteria.getKeyword())) {
            return criteria.getKeyword().trim();
        }
        return null;
    }
}
