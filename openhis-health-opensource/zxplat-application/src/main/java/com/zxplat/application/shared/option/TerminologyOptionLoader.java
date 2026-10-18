/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.option;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.utils.OptionDtoLegacyRowUtil;
import com.zxplat.domain.terminology.entity.CodeSystem;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.zxplat.framework.core.application.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术语下拉与列表行展示映射的统一入口（B.6.1 {@code common.assembler.option}）。
 *
 * <p><b>选用指引</b>（禁止在 AppService 内再私有实现同构 {@code getOptionListByCodeSystemName → Map}）：</p>
 * <ul>
 *   <li>{@link #loadOptions(String)} / {@link #loadLegacyRows(String)}：页面初始化、筛选条件下拉，需要完整
 *       {@link OptionDto}（value/label）列表时；</li>
 *   <li>{@link #buildLabelMap(String)}：列表、发药单、处置单等<b>批量</b>把持久化 {@code code} 转成展示名时——
 *       先对本编码体系预热「码 → 文案」映射，再配合
 *       {@link com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler#resolveCategoryDisplay}
 *       逐行解析。</li>
 * </ul>
 *
 * <p>业务枚举展示名仍走 {@code EnumUtil}（B.10.8.1）；本类仅负责术语编码体系（字典）侧。</p>
 *
 * <p>进程内按 codeSystemName 短窗缓存选项列表，避免发药 slip / 选品连点反复全量打术语库。</p>
 */
@Component
@RequiredArgsConstructor
public class TerminologyOptionLoader {

    /** 术语编码体系读侧。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /** codeSystemName → 选项列表软缓存。 */
    private final ConcurrentHashMap<String, CachedOptions> optionsCache = new ConcurrentHashMap<>();

    /**
     * 按 codeSystemName 获取标准 {@link OptionDto} 列表。
     *
     * <p>用于下拉框、页面 init 等需要完整选项结构的场景；列表行批量翻译请用 {@link #buildLabelMap(String)}。</p>
     *
     * @param codeSystemName 术语编码体系名称（见 {@code TerminologyCodeSystemNameConstants}）
     * @return 下拉选项
     */
    public List<OptionDto> loadOptions(String codeSystemName) {
        return this.loadOptionsCached(codeSystemName);
    }

    /**
     * 按 codeSystemName 获取旧前端兼容的 label/value 行。
     *
     * @param codeSystemName 术语编码体系名称
     * @return 旧版行结构
     */
    public List<Map<String, Object>> loadLegacyRows(String codeSystemName) {
        return OptionDtoLegacyRowUtil.toLabelValueRows(loadOptions(codeSystemName));
    }

    /**
     * 将编码体系下全部概念转为「码 → 展示名」映射，供列表行批量解析类目/单位/频次等展示列。
     *
     * <p>持久化列存的是术语 {@code value}（字符串 code）；展示列通过
     * {@link com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler#resolveCategoryDisplay}
     * 查表，未命中时为 {@code null}（由调用方决定是否回落展示 code 本身）。</p>
     *
     * @param codeSystemName 编码体系名称；空白时返回空映射
     * @return 码到展示名
     */
    public Map<String, String> buildLabelMap(String codeSystemName) {
        Map<String, String> labelByCode = new HashMap<>();
        // Gate：空体系名无法映射
        if (StringUtils.isEmpty(codeSystemName)) {
            return labelByCode;
        }
        List<OptionDto> options = this.loadOptionsCached(codeSystemName.trim());
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                labelByCode.put(option.getValue(), option.getLabel());
            }
        }
        return labelByCode;
    }

    /**
     * 带短窗缓存地加载术语选项（命中则不再打 {@code CodeSystemQueryService}）。
     *
     * @param codeSystemName 编码体系名称
     * @return 选项列表；名称为空时返回空列表
     */
    private List<OptionDto> loadOptionsCached(String codeSystemName) {
        // Gate：空白名称无字典可查
        if (StringUtils.isEmpty(codeSystemName)) {
            return List.of();
        }
        String cacheKey = codeSystemName.trim();
        long nowMs = System.currentTimeMillis();
        CachedOptions cached = this.optionsCache.get(cacheKey);
        // 短窗命中：字典变更不频繁，复用避免发药点患者连打 7～8 张码表
        if (cached != null
            && nowMs - cached.loadedAtMs()
                < GeneralSharedConstants.PRODUCT_DEFINITION_SELECTION_TERMINOLOGY_CACHE_TTL_MS) {
            return cached.options();
        }
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(cacheKey);
        List<OptionDto> snapshot;
        if (options == null || options.isEmpty()) {
            snapshot = List.of();
        } else {
            snapshot = List.copyOf(options);
        }
        this.optionsCache.put(cacheKey, new CachedOptions(snapshot, System.currentTimeMillis()));
        return snapshot;
    }

    /**
     * 按编码体系名失效选项软缓存（概念增删改后须调用，否则导入补字典后仍读到旧列表）。
     *
     * @param codeSystemName 编码体系名称
     */
    public void evict(String codeSystemName) {
        if (StringUtils.isEmpty(codeSystemName)) {
            return;
        }
        this.optionsCache.remove(codeSystemName.trim());
    }

    /**
     * 按编码体系主键失效选项软缓存。
     *
     * @param codeSystemId 编码体系主键
     */
    public void evictByCodeSystemId(Long codeSystemId) {
        if (codeSystemId == null) {
            return;
        }
        codeSystemQueryService.findById(codeSystemId)
            .map(CodeSystem::getName)
            .ifPresent(this::evict);
    }

    /**
     * 清空全部选项软缓存。
     */
    public void evictAll() {
        this.optionsCache.clear();
    }

    /**
     * 术语选项软缓存条目。
     *
     * @param options    不可变选项快照
     * @param loadedAtMs 加载时刻（epoch ms）
     */
    private record CachedOptions(List<OptionDto> options, long loadedAtMs) {
    }
}
