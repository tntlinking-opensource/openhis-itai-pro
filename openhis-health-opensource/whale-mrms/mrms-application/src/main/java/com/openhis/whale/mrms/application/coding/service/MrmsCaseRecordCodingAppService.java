/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.coding.service;

import com.openhis.whale.mrms.application.coding.dto.IcdItemDto;
import com.openhis.whale.mrms.application.coding.request.SearchIcdRequest;
import com.openhis.whale.mrms.contracts.reader.HealthIcdDictionaryReader;
import com.openhis.whale.mrms.contracts.snapshot.IcdItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 首页 ICD Picker AppService → {@code POST /api/app/mrmsCaseRecordCoding/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordCodingAppService {

    private final HealthIcdDictionaryReader healthIcdDictionaryReader;

    /**
     * ICD-10 诊断编码检索。
     *
     * @param request 检索请求
     * @return 候选项列表
     */
    public List<IcdItemDto> searchIcd10(SearchIcdRequest request) {
        int limit = resolveLimit(request.getLimit());
        List<IcdItemSnapshot> snaps = healthIcdDictionaryReader.searchIcd10(trimToNull(request.getKeyword()), limit);
        return toDtos(snaps);
    }

    /**
     * ICD-9-CM-3 手术编码检索。
     *
     * @param request 检索请求
     * @return 候选项列表
     */
    public List<IcdItemDto> searchIcd9Cm3(SearchIcdRequest request) {
        int limit = resolveLimit(request.getLimit());
        List<IcdItemSnapshot> snaps = healthIcdDictionaryReader.searchIcd9Cm3(trimToNull(request.getKeyword()), limit);
        return toDtos(snaps);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    private List<IcdItemDto> toDtos(List<IcdItemSnapshot> snaps) {
        List<IcdItemDto> result = new ArrayList<>();
        if (snaps == null) {
            return result;
        }
        for (IcdItemSnapshot snap : snaps) {
            IcdItemDto dto = new IcdItemDto();
            dto.setCode(snap.getCode());
            dto.setName(snap.getName());
            dto.setPyStr(snap.getSearchCode());
            result.add(dto);
        }
        return result;
    }
}
