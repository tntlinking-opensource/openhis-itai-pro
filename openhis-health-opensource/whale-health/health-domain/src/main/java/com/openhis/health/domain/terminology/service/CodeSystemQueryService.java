/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.terminology.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.repository.CodeSystemRepository;
import com.openhis.health.domain.terminology.repository.ConceptRepository;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.infrastructure.cache.IndexedCacheService;
import com.openhis.whale.core.infrastructure.util.SearchUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 编码系统（CodeSystem）与概念（Concept）读侧服务（含缓存）。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>CodeSystem 列表分页查询不做强缓存（筛选/排序多、易过期）；Concept 字典/Option 走缓存。</li>
 *   <li>写侧 {@link CodeSystemDomainService} 完成变更后应调用本类的 evict 方法做写后失效，保证一致性。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSystemQueryService {

    private static final String CACHE_PREFIX_DICT = "DICT:";
    private static final String CACHE_PREFIX_ENUM = "ENUM:";

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;

    @Autowired
    private final IndexedCacheService<Concept> conceptCache;

    @Value("${whale.cache.concept.init-on-startup:true}")
    private boolean initCacheOnStartup;

    private final ConcurrentHashMap<String, List<OptionDto>> enumOptionCache = new ConcurrentHashMap<>();

    // ---- CodeSystem 查询（不强缓存） ----

    /**
     * 按主键读取编码系统；入参为空时直接空 Optional，避免无意义访问数据库。
     *
     * @param id 主键
     * @return 可能为空的编码系统
     */
    public Optional<CodeSystem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return codeSystemRepository.findById(id);
    }

    /**
     * 按唯一名称解析启用态编码系统（名称 trim 后匹配）。
     *
     * @param name 编码系统名称
     * @return 命中则非空
     */
    public Optional<CodeSystem> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        return findActiveCodeSystemByName(name.trim());
    }

    /**
     * 通用分页检索：由 {@link SearchUtils} 将前端 JSON 条件转为 JPA 分页查询。
     *
     * @param requestJson 查询请求体
     * @param probe 探针实体（承载默认字段）
     * @return 分页结果
     */
    public Page<CodeSystem> pageQueryCodeSystem(JSONObject requestJson, CodeSystem probe) {
        SearchUtils<CodeSystem> searchUtils = new SearchUtils<>();
        return searchUtils.generateJpaPageQuery(requestJson, probe, codeSystemRepository);
    }

    /**
     * CodeSystem 全量列表（专用于左侧树等“全量加载”场景，不受 BaseQueryRequest pageSize=1000 上限影响）。
     *
     * @return 全部未删编码系统，按创建时间倒序
     */
    public List<CodeSystem> listAllCodeSystems() {
        return codeSystemRepository.findAll(Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
    }

    // ---- Concept 查询（codeSystemId 维度，读库）----

    /**
     * 按主键读取概念项。
     *
     * @param id 概念主键
     * @return 可能为空
     */
    public Optional<Concept> findConceptById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return conceptRepository.findById(id);
    }

    /**
     * 列出某编码系统下全部启用概念，按排序号升序（无分页，规模受字典体量约束）。
     *
     * @param codeSystemId 编码系统主键
     * @return 概念列表；id 为空时返回空列表
     */
    public List<Concept> findConceptsByCodeSystem(Long codeSystemId) {
        if (codeSystemId == null) {
            return List.of();
        }
        return conceptRepository.findByCodeSystemIdAndIsActiveTrueOrderBySortOrderAsc(codeSystemId);
    }

    // ---- 字典缓存（codeSystemName 维度）----

    /**
     * 按「编码系统名称 + 概念码」解析单条概念：先读二级缓存，未命中则回源并回填。
     *
     * @param codeSystemName 编码系统名称
     * @param code 概念业务码
     * @return 概念实体；入参非法或未命中时为 null
     */
    public Concept findConceptByCodeSystemNameAndCode(String codeSystemName, String code) {
        if (codeSystemName == null || codeSystemName.trim().isEmpty()) {
            return null;
        }
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String name = codeSystemName.trim();
        String c = code.trim();

        String cacheKey = CACHE_PREFIX_DICT + name;
        Concept cached = conceptCache.get(cacheKey, c);
        if (cached != null) {
            return cached;
        }

        return findActiveCodeSystemByName(name)
            .flatMap(cs -> conceptRepository.findByCodeSystemIdAndCode(cs.getId(), c))
            .map(concept -> {
                conceptCache.put(cacheKey, concept.getCode(), concept);
                return concept;
            })
            .orElse(null);
    }

    /**
     * 按编码系统名称取该编码系统下所有 Concept（优先缓存；缓存为空时回源并回填）。
     *
     * @param codeSystemName 编码系统名称
     * @return 概念列表；名称非法或编码系统不存在时返回空列表
     */
    public List<Concept> findConceptsByCodeSystemName(String codeSystemName) {
        if (codeSystemName == null || codeSystemName.trim().isEmpty()) {
            return List.of();
        }
        String name = codeSystemName.trim();
        String cacheKey = CACHE_PREFIX_DICT + name;

        List<Concept> cached = conceptCache.getAll(cacheKey);
        if (!cached.isEmpty()) {
            return sortConceptsBySortOrder(cached);
        }
        return loadAndCacheConcepts(name);
    }

    /**
     * 将指定编码系统名称下全部概念转为下拉选项（走缓存与 {@link #findConceptsByCodeSystemName}）。
     *
     * @param codeSystemName 编码系统名称
     * @return 选项 DTO 列表
     */
    public List<OptionDto> getOptionListByCodeSystemName(String codeSystemName) {
        List<OptionDto> options = conceptToOptionList(findConceptsByCodeSystemName(codeSystemName));
        // Asm：按 sort_order 升序稳定下拉次序，供 IF-DOR-18 init 与前端展示一致
        options.sort(
            Comparator.comparing(OptionDto::getSort, Comparator.nullsLast(Long::compareTo))
                .thenComparing(o -> StringUtils.defaultString(o.getValue())));
        return options;
    }

    /**
     * 将实现了 {@link EnumInterface} 的枚举常量懒加载为下拉选项，进程内复用。
     *
     * @param enumClass 枚举类
     * @param <E> 枚举类型
     * @return 选项列表
     */
    public <E extends Enum<E> & EnumInterface> List<OptionDto> getOptionListByEnum(Class<E> enumClass) {
        String cacheKey = CACHE_PREFIX_ENUM + enumClass.getName();
        return enumOptionCache.computeIfAbsent(cacheKey, k -> {
            // Loop：遍历枚举常量，转换为OptionDto
            List<OptionDto> list = Stream.of(enumClass.getEnumConstants())
                    .map(e -> new OptionDto(e.getSortOrder(), String.valueOf(e.getCode()), e.getDisplay()))
                    .collect(Collectors.toList());
            log.debug("懒加载枚举选项: {} -> {} 条", enumClass.getSimpleName(), list.size());
            return list;
        });
    }

    /**
     * 按码取枚举展示名；码为空或未匹配时返回 null。
     *
     * @param enumClass 枚举类
     * @param code 枚举 code
     * @param <E> 枚举类型
     * @return 展示文案
     */
    public <E extends Enum<E> & EnumInterface> String getDisplayByCode(Class<E> enumClass, String code) {
        if (code == null) {
            return null;
        }
        return getOptionListByEnum(enumClass).stream()
            .filter(o -> code.equals(o.getValue()))
            .findFirst()
            .map(OptionDto::getLabel)
            .orElse(null);
    }

    // ---- 写后失效（由写侧调用）----

    /**
     * 按编码系统名称整包失效字典缓存，保证写后读一致性。
     *
     * @param codeSystemName 编码系统名称
     */
    public void evictDictByCodeSystemName(String codeSystemName) {
        if (codeSystemName == null || codeSystemName.trim().isEmpty()) {
            return;
        }
        conceptCache.evictAll(CACHE_PREFIX_DICT + codeSystemName.trim());
    }

    /**
     * 按编码系统主键失效缓存：先解析名称再调用 {@link #evictDictByCodeSystemName}。
     *
     * @param codeSystemId 编码系统主键
     */
    public void evictDictByCodeSystemId(Long codeSystemId) {
        if (codeSystemId == null) {
            return;
        }
        codeSystemRepository.findById(codeSystemId)
            .map(CodeSystem::getName)
            .ifPresent(this::evictDictByCodeSystemName);
    }

    /**
     * 清空全部概念字典缓存（运维或重建索引场景慎用）。
     */
    public void clearAllDictCache() {
        conceptCache.clear();
    }

    /**
     * 从数据库加载某名称下全部启用概念并一次性写入缓存分片。
     *
     * @param codeSystemName 已 trim 的名称
     * @return 概念列表；编码系统不存在时为空列表
     */
    private List<Concept> loadAndCacheConcepts(String codeSystemName) {
        String cacheKey = CACHE_PREFIX_DICT + codeSystemName;
        return findActiveCodeSystemByName(codeSystemName)
            .map(cs -> {
                List<Concept> list = conceptRepository.findByCodeSystemIdAndIsActiveTrueOrderBySortOrderAsc(cs.getId());
                if (!list.isEmpty()) {
                    conceptCache.putAll(cacheKey, list, Concept::getCode);
                    log.debug("回源加载编码系统概念并回填缓存: {} -> {} 条", codeSystemName, list.size());
                }
                return list;
            })
            .orElse(List.of());
    }

    /**
     * 按名称解析「启用」编码系统。
     * <p>兼容两类存量数据：{@link PublicationStatus#ACTIVE} 的字符码（{@code ACTIVE}），以及历史 Flyway 种子写入的数值 status（{@code 2}，
     * 与 ACTIVE 的排序序号一致），持久化为整型时 JPA 侧常映射为字符串 {@code "2"}。</p>
     *
     * @param trimmedName 已 trim 的编码系统名称
     * @return 命中则非空
     */
    private Optional<CodeSystem> findActiveCodeSystemByName(String trimmedName) {
        return codeSystemRepository.findByNameAndStatus(trimmedName, PublicationStatus.ACTIVE.getCode())
            .or(() -> {
                return codeSystemRepository.findByNameAndStatus(
                    trimmedName,
                    String.valueOf(PublicationStatus.ACTIVE.getSortOrder()));
            });
    }

    /**
     * 概念列表转统一下拉 DTO（排序值、码、显示名）。
     *
     * @param list 概念集合
     * @return 选项列表
     */
    private static List<OptionDto> conceptToOptionList(List<Concept> list) {
        // Loop：遍历概念列表，转换为OptionDto
        return list.stream()
                .map(c -> new OptionDto(c.getSortOrder(), String.valueOf(c.getCode()), c.getDisplay()))
                .collect(Collectors.toList());
    }

    /**
     * 术语概念按库表 sort_order 升序（缓存回源后须重排，避免 HashSet 索引乱序）。
     *
     * @param list 概念列表；{@code null} 时返回空列表
     * @return 排序后的新列表
     */
    private static List<Concept> sortConceptsBySortOrder(List<Concept> list) {
        if (list == null || list.isEmpty()) {
            if (list == null) {
                return List.of();
            }
            return list;
        }
        return list.stream()
            .sorted(Comparator.comparing(Concept::getSortOrder, Comparator.nullsLast(Long::compareTo))
                .thenComparing(c -> String.valueOf(c.getCode())))
            .collect(Collectors.toList());
    }

    /**
     * 加载全部未删除编码系统并预取概念集合（B.2：仓储禁 {@code @Query}）。
     *
     * @return 含 LEFT FETCH concepts 的编码系统列表
     */
    private List<CodeSystem> findAllWithConceptsLoaded() {
        Specification<CodeSystem> specification = (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("concepts", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.isFalse(root.get(FieldNameConstants.IS_DELETED));
        };
        return codeSystemRepository.findAll(specification);
    }

    /**
     * 启动时可选预热：将全部编码系统及其概念装入字典缓存，降低首次术语访问延迟。
     *
     * <p>受配置项 {@code whale.cache.concept.init-on-startup} 控制；关闭时跳过预热，后续走懒加载。</p>
     */
    @PostConstruct
    public void initCodeSystemCache() {
        if (!initCacheOnStartup) {
            return;
        }
        try {
            log.info(">>> 术语字典缓存预热开始...");
            List<CodeSystem> codeSystems = findAllWithConceptsLoaded();
            // Loop：遍历编码系统，逐个写入缓存分片
            for (CodeSystem codeSystem : codeSystems) {
                if (codeSystem.getConcepts() != null && !codeSystem.getConcepts().isEmpty()) {
                    conceptCache.putAll(CACHE_PREFIX_DICT + codeSystem.getName(), codeSystem.getConcepts(), Concept::getCode);
                }
            }
            log.info(">>> 术语字典缓存预热完成: {} 个编码系统", codeSystems.size());
        } catch (Exception e) {
            log.error("编码系统缓存初始化失败", e);
        }
    }
}
