/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.dto.catalog.Catalog1312QueryInput;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.dto.request.Catalog1312FilterRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【1312】医保目录信息查询入参组装（平台报文 data 节点）。 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class Catalog1312InputAssembler {

    /**
     * 组装 1312 平台查询 {@code data} 节点 JSON。
     *
     * @param insuAdmdvs 参保地医保区划
     * @param filter 目录筛选（list_type、hilist_code/name、vali_flag 等）
     * @param resolvedUpdtTime 增量游标 updt_time（缺省取常量默认值）
     * @param pageNum 页码（缺省 1）
     * @param pageSize 页大小（缺省 {@link ChsCatalogConstants#DEFAULT_PLATFORM_PAGE_SIZE}）
     * @return 平台 input JSON（{@code {data:{...}}}）
     */
    public JSONObject assemble(String insuAdmdvs, Catalog1312FilterRequest filter,
                               String resolvedUpdtTime, Integer pageNum, Integer pageSize) {
        Catalog1312FilterRequest criteria = filter;
        if (criteria == null) {
            criteria = new Catalog1312FilterRequest();
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
        if (StringUtils.isNotBlank(criteria.getValiFlag())) {
            valiFlag = criteria.getValiFlag().trim();
        } else {
            valiFlag = ChsCatalogConstants.DEFAULT_VALI_FLAG;
        }
        String updtTime;
        if (StringUtils.isNotBlank(resolvedUpdtTime)) {
            updtTime = resolvedUpdtTime.trim();
        } else {
            updtTime = ChsCatalogConstants.DEFAULT_CATALOG_UPDT_TIME;
        }
        Catalog1312QueryInput data = Catalog1312QueryInput.builder()
            .insuAdmdvs(insuAdmdvs)
            .listType(trimToNull(criteria.getListType()))
            .hilistCode(trimToNull(criteria.getHilistCode()))
            .hilistName(resolveHilistName(criteria))
            .pinyin(resolvePinyin(criteria))
            .valiFlag(valiFlag)
            .chrgitmLv(trimToNull(criteria.getChrgitmLv()))
            .lmtUsedFlag(trimToNull(criteria.getLmtUsedFlag()))
            .queryDate(trimToNull(criteria.getQueryDate()))
            .begndate(trimToNull(criteria.getBegndate()))
            .updtTime(updtTime)
            .pageNum(platformPageNum)
            .pageSize(platformPageSize)
            .build();
        JSONObject root = new JSONObject();
        root.put("data", JSON.toJSON(data));
        return root;
    }

    private static String resolveHilistName(Catalog1312FilterRequest filter) {
        if (StringUtils.isNotBlank(filter.getHilistName())) {
            return filter.getHilistName().trim();
        }
        if (StringUtils.isNotBlank(filter.getKeyword())) {
            return filter.getKeyword().trim();
        }
        return null;
    }

    private static String resolvePinyin(Catalog1312FilterRequest filter) {
        if (StringUtils.isNotBlank(filter.getPinyin())) {
            return filter.getPinyin().trim();
        }
        if (StringUtils.isNotBlank(filter.getKeyword())) {
            return filter.getKeyword().trim();
        }
        return null;
    }
}