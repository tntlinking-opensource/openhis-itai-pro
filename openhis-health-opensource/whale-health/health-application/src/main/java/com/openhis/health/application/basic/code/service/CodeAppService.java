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

package com.openhis.health.application.basic.code.service;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TerminologyEnumScanConstants;
import com.openhis.health.application.basic.code.dto.CodeSystemDto;
import com.openhis.health.application.basic.code.dto.ConceptDto;
import com.openhis.health.application.basic.code.mapper.CodesystemMapper;
import com.openhis.health.application.basic.code.request.CreateCodeSystemRequest;
import com.openhis.health.application.basic.code.request.CreateConceptRequest;
import com.openhis.health.application.basic.code.request.EnumDictRequest;
import com.openhis.health.application.basic.code.request.QueryCodeSystemRequest;
import com.openhis.health.application.basic.code.request.UpdateCodeSystemRequest;
import com.openhis.health.application.basic.code.request.UpdateConceptRequest;
import com.openhis.health.application.basic.code.dto.OrganizationDictDto;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import com.openhis.health.domain.terminology.service.CodeSystemDomainService;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 编码系统（术语）维护应用服务：编码体系与概念 CRUD、枚举字典扫描与展示。
 *
 * <p>枚举目录通过 classpath 扫描 {@link TerminologyEnumScanConstants#ENUM_BASE_PACKAGE} 自动发现，无需手工维护白名单。</p>
 *
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAppService {

    /** 术语读侧：编码体系与概念列表查询、枚举转 Option。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 术语写侧：体系与概念的创建、更新、删除。 */
    private final CodeSystemDomainService codeSystemDomainService;
    /** DTO/请求 ↔ 领域命令与实体展示映射。 */
    private final CodesystemMapper codesystemMapper;

    /**
     * 枚举简单类名 → 枚举 {@code Class}，进程内懒加载双检锁缓存。
     *
     * <p>用途：左侧「枚举字典」页避免每次请求扫描 classpath。</p>
     */
    private static volatile Map<String, Class<? extends Enum<?>>> ENUM_DICT_CACHE;

    /**
     * 获取（或构建）枚举字典缓存映射。
     *
     * @return 枚举名到枚举类型的映射（稳定排序的 {@link java.util.LinkedHashMap}）
     */
    private static Map<String, Class<? extends Enum<?>>> getEnumDictMap() {
        Map<String, Class<? extends Enum<?>>> cached = ENUM_DICT_CACHE;
        // 快路径：已缓存则直接返回，避免同步开销。
        if (cached != null) {
            return cached;
        }
        synchronized (CodeAppService.class) {
            cached = ENUM_DICT_CACHE;
            if (cached != null) {
                return cached;
            }
            ENUM_DICT_CACHE = cached = scanEnumDicts();
            return cached;
        }
    }

    /**
     * 扫描 classpath 下术语枚举包内的全部枚举类型。
     *
     * @return 简单类名 → 枚举 Class；扫描失败返回空映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Class<? extends Enum<?>>> scanEnumDicts() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String enumBasePath = ClassUtils.convertClassNameToResourcePath(
                TerminologyEnumScanConstants.ENUM_BASE_PACKAGE);
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + enumBasePath
                + "/**/*.class";

            Resource[] resources = resolver.getResources(pattern);
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            Map<String, Class<? extends Enum<?>>> map = new java.util.HashMap<>();
            // 遍历 classpath 资源：仅保留指定包下「顶层枚举」类，排除匿名与内部枚举。
            for (Resource r : resources) {
                if (r == null || !r.isReadable()) continue;
                String url = Objects.toString(r.getURL(), "");
                int idx = url.indexOf(enumBasePath);
                if (idx < 0) continue;

                String classPath = url.substring(idx).replace('/', '.');
                if (!classPath.endsWith(".class")) continue;
                String className = classPath.substring(0, classPath.length() - ".class".length());
                // 跳过匿名/内部类
                if (className.contains("$")) continue;

                Class<?> clazz = Class.forName(className, false, cl);
                if (!clazz.isEnum()) continue;
                if (clazz.isInterface()) continue;
                map.put(clazz.getSimpleName(), (Class<? extends Enum<?>>) clazz);
            }
            // 稳定排序：按枚举名
            return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ));
        } catch (Exception e) {
            log.error("扫描枚举字典失败: basePackage={}",
                TerminologyEnumScanConstants.ENUM_BASE_PACKAGE, e);
            return Map.of();
        }
    }

    /**
     * 查询全部编码体系列表（不分页）。
     *
     * <p>对应前端：{@code POST /code/getCodeSystemList}。当前实现未按 {@code request} 过滤，预留扩展。</p>
     *
     * @param request 预留筛选条件（可为 {@code null}）
     * @return 编码体系 DTO 列表
     */
    public List<CodeSystemDto> getCodeSystemList(QueryCodeSystemRequest request) {
        // 读侧全量列表：术语数据量可控；若后续按租户/关键字过滤，应在 QueryService 层下推条件。
        return codeSystemQueryService.listAllCodeSystems()
            .stream()
            .map(codesystemMapper::toDto)
            .collect(Collectors.toList());
    }

    /**
     * 新建编码体系并返回持久化结果。
     *
     * <p>对应前端：{@code POST /code/createCodeSystem}。</p>
     *
     * @param request 创建请求体
     * @return 新建后的编码体系 DTO
     * @throws ValidationException 请求体为空
     */
    public CodeSystemDto createCodeSystem(CreateCodeSystemRequest request) {
        // 网关直达应用：空体立即失败，避免匿名命令污染术语聚合。
        if (request == null) {
            throw new ValidationException("创建编码系统失败：请求体不能为空");
        }
        var command = codesystemMapper.toCreateCommand(request);
        // 租户隔离：写入当前线程租户，避免跨租户误建。
        command.setTenantId(TenantUtils.getCurrentTenantId());
        CodeSystem saved = codeSystemDomainService.create(command);
        codeSystemQueryService.clearAllDictCache();
        return codesystemMapper.toDto(saved);
    }

    /**
     * 更新编码体系。
     *
     * <p>对应前端：{@code POST /code/updateCodeSystem}。</p>
     *
     * @param request 更新请求（须含主键）
     * @return 更新后的编码体系 DTO
     * @throws ValidationException 请求或主键为空
     */
    public CodeSystemDto updateCodeSystem(UpdateCodeSystemRequest request) {
        // 更新语义必须定位主键：缺失 id 时领域无法加载聚合。
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新编码系统失败：编码系统ID不能为空");
        }
        var command = codesystemMapper.toUpdateCommand(request);
        CodeSystem saved = codeSystemDomainService.update(command);
        codeSystemQueryService.clearAllDictCache();
        return codesystemMapper.toDto(saved);
    }

    /**
     * 删除编码体系。
     *
     * <p>对应前端：{@code POST /code/deleteCodeSystem}。</p>
     *
     * @param id 编码体系主键
     * @throws ValidationException {@code id} 为空
     */
    public void deleteCodeSystem(Long id) {
        // 删除须指定目标体系，避免误删全租户术语根。
        if (id == null) {
            throw new ValidationException("删除编码系统失败：编码系统ID不能为空");
        }
        codeSystemDomainService.delete(id);
        codeSystemQueryService.clearAllDictCache();
    }

    /**
     * 按编码体系查询其下全部概念（编码项）列表。
     *
     * <p>对应前端：{@code POST /code/getConceptList}。</p>
     *
     * @param codeSystemId 编码体系主键
     * @return 概念 DTO 列表
     * @throws ValidationException {@code codeSystemId} 为空
     */
    public List<ConceptDto> getConceptList(Long codeSystemId) {
        // 概念列表挂载在编码体系之下，缺少父 id 无法界定命名空间。
        if (codeSystemId == null) {
            throw new ValidationException("获取编码列表失败：编码系统ID不能为空");
        }
        return codeSystemQueryService.findConceptsByCodeSystem(codeSystemId)
            .stream()
            .map(codesystemMapper::toDto)
            .collect(Collectors.toList());
    }

    /**
     * 在指定编码体系下新增概念。
     *
     * <p>对应前端：{@code POST /code/addConcept}。</p>
     *
     * @param request 新增请求（须含 {@code codeSystemId}）
     * @throws ValidationException 请求或编码体系 id 为空
     */
    public void addConcept(CreateConceptRequest request) {
        // 新建概念必须归属于某一编码体系，否则违反术语不变量。
        if (request == null || request.getCodeSystemId() == null) {
            throw new ValidationException("新增编码失败：编码系统ID不能为空");
        }
        var command = codesystemMapper.toCreateConceptCommand(request);
        com.openhis.health.domain.terminology.entity.Concept saved = codeSystemDomainService.createConcept(command);
        codeSystemQueryService.evictDictByCodeSystemId(saved.getCodeSystemId());
    }

    /**
     * 更新已有概念（方法名为历史路由兼容拼写 {@code updatyeConcept}）。
     *
     * <p>对应前端：{@code POST /code/updatyeConcept}。</p>
     *
     * @param request 更新请求（须含概念主键）
     * @throws ValidationException 请求或概念 id 为空
     */
    public void updatyeConcept(UpdateConceptRequest request) {
        // 方法名为历史路由兼容拼写；更新仍需概念主键以加载术语行。
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新编码失败：编码ID不能为空");
        }
        var command = codesystemMapper.toUpdateConceptCommand(request);
        com.openhis.health.domain.terminology.entity.Concept saved = codeSystemDomainService.updateConcept(command);
        codeSystemQueryService.evictDictByCodeSystemId(saved.getCodeSystemId());
    }

    /**
     * 删除概念。
     *
     * <p>对应前端：{@code POST /code/deleteConcept}。</p>
     *
     * @param id 概念主键
     * @throws ValidationException {@code id} 为空
     */
    public void deleteConcept(Long id) {
        // 逻辑删除概念前须唯一定位行主键。
        if (id == null) {
            throw new ValidationException("删除编码失败：编码ID不能为空");
        }
        codeSystemQueryService.findConceptById(id).ifPresent(concept ->
            codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId()));
        codeSystemDomainService.deleteConcept(id);
    }

    /**
     * 左侧 TAB「枚举字典」：返回可选枚举目录（类路径扫描 {@link TerminologyEnumScanConstants#ENUM_BASE_PACKAGE} 下全部枚举名）。
     *
     * <p>对应前端：{@code POST /code/getEnumDictList}。</p>
     *
     * @return 枚举简单类名列表（当前 label 与 value 同为类名，便于前端表格复用）
     */
    public List<OrganizationDictDto> getEnumDictList() {
        return getEnumDictMap().keySet().stream()
            .map(k -> new OrganizationDictDto(k, k))
            .collect(Collectors.toList());
    }

    /**
     * 左侧 TAB「枚举字典」：返回某枚举的条目列表（映射为 {@link ConceptDto}，供现有表格复用）。
     *
     * <p>对应前端：{@code POST /code/getEnumConceptList}。</p>
     *
     * @param request 包含枚举简单类名 {@code enumName}
     * @return 与表格兼容的概念行列表（无持久化 id）
     * @throws ValidationException 参数为空或枚举名未在扫描结果中
     */
    public List<ConceptDto> getEnumConceptList(EnumDictRequest request) {
        // 左侧表格依赖枚举简单类名唯一定位 classpath 类型。
        if (request == null || request.getEnumName() == null || request.getEnumName().trim().isEmpty()) {
            throw new ValidationException("获取枚举字典失败：枚举名称不能为空");
        }
        String enumName = request.getEnumName().trim();
        Class<? extends Enum<?>> enumClass = getEnumDictMap().get(enumName);
        // 未出现在扫描包内的名称视为非法，避免反射任意类。
        if (enumClass == null) {
            throw new ValidationException("获取枚举字典失败：不支持的枚举 " + request.getEnumName());
        }

        // 优先走 EnumInterface：展示字段（编码、显示名、定义、排序）与术语概念对齐；解析失败则降级为 OptionDto 路径。
        try {
            if (EnumInterface.class.isAssignableFrom(enumClass)) {
                EnumInterface[] values = (EnumInterface[]) enumClass.getMethod("values").invoke(null);
                java.util.Arrays.sort(values, EnumUtil::compareSortOrderNullsLast);
                return java.util.Arrays.stream(values)
                    .map(v -> {
                        ConceptDto dto = new ConceptDto();
                        dto.setId(null);
                        dto.setCodeSystemId(v.getCodeSystemId());
                        dto.setCodeSystemName(enumName);
                        dto.setCode(v.getCode());
                        dto.setDisplay(v.getDisplay());
                        dto.setDefinition(v.getDefinition());
                        dto.setSortOrder(v.getSortOrder());
                        dto.setIsActive(true);
                        return dto;
                    })
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("枚举 {} 未能按 EnumInterface 解析，降级为 OptionDto 解析。", enumName, e);
        }

        // 降级：由术语服务将枚举转为通用 OptionDto，再适配为 ConceptDto（字段较少但保证可用）。
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<OptionDto> options = codeSystemQueryService.getOptionListByEnum((Class) enumClass);
        return options.stream()
            .map(o -> {
                ConceptDto dto = new ConceptDto();
                dto.setId(null);
                dto.setCodeSystemId(null);
                dto.setCodeSystemName(enumName);
                dto.setCode(o.getValue());
                dto.setDisplay(o.getLabel());
                dto.setDefinition(GeneralSharedConstants.EMPTY_STRING);
                dto.setSortOrder(o.getSort());
                dto.setIsActive(true);
                return dto;
            })
            .collect(Collectors.toList());
    }

}
