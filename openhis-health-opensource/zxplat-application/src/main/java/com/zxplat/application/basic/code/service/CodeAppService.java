/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.service;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.constants.TerminologyEnumScanConstants;
import com.zxplat.application.basic.code.dto.CodeSystemDto;
import com.zxplat.application.basic.code.dto.ConceptDto;
import com.zxplat.application.basic.code.mapper.CodesystemMapper;
import com.zxplat.application.basic.code.request.CreateCodeSystemRequest;
import com.zxplat.application.basic.code.request.CreateConceptRequest;
import com.zxplat.application.basic.code.request.EnumDictRequest;
import com.zxplat.application.basic.code.request.QueryCodeSystemRequest;
import com.zxplat.application.basic.code.request.UpdateCodeSystemRequest;
import com.zxplat.application.basic.code.request.UpdateConceptRequest;
import com.zxplat.application.basic.code.dto.OrganizationDictDto;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.enums.EnumInterface;
import com.zxplat.domain.terminology.entity.CodeSystem;
import com.zxplat.domain.terminology.entity.Concept;
import com.zxplat.domain.terminology.service.CodeSystemDomainService;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
 * 编码系统（术语）维护应用服务：编码体系与概念 CRUD、枚举字典扫描与展示、运维刷缓存。
 *
 * <p>枚举目录通过 classpath 扫描 {@link TerminologyEnumScanConstants#ENUM_BASE_PACKAGE} 自动发现，无需手工维护白名单。</p>
 * <p>SQL/运维直写 {@code ter_concept} 后可调用 {@link #refreshConceptCache()} 清空概念字典与术语选项软缓存，无需重启。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAppService {

    /** 系统预置只读字典维护失败时的统一提示（与前端数据字典页对齐）。 */
    private static final String MSG_READONLY_CODE_SYSTEM = "该字典为系统预置，不允许新增/编辑/删除";

    /** 关键字典禁止删除/停用时的统一提示。 */
    private static final String MSG_NO_DELETE_DEACTIVATE_CODE_SYSTEM =
        "该字典为系统关键口径，不允许删除或停用";

    /** 术语读侧：编码体系与概念列表查询、枚举转 Option。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 术语写侧：体系与概念的创建、更新、删除。 */
    private final CodeSystemDomainService codeSystemDomainService;
    /** DTO/请求 ↔ 领域命令与实体展示映射。 */
    private final CodesystemMapper codesystemMapper;
    /** 术语下拉软缓存：概念变更后须同步失效。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

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
     * 扫描 terminology 包下全部枚举类，构建简单类名 → Class 映射。
     *
     * @return 枚举映射；扫描失败返回空映射
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
        // Gate：只读全量列表，当前未按 request 过滤
        // Pre：读侧加载全部编码体系
        // Asm：映射 DTO 列表
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
        // Gate：请求体非空，避免匿名命令污染术语聚合
        if (request == null) {
            throw new ValidationException("创建编码系统失败：请求体不能为空");
        }
        // Gate：禁止用预置只读体系名新建，避免绕过种子口径
        assertCodeSystemNameWritable(request.getName());
        // Pre：请求映射为领域创建命令并绑定当前租户
        var command = codesystemMapper.toCreateCommand(request);
        command.setTenantId(TenantUtils.getCurrentTenantId());
        // Cmd：领域创建编码体系
        CodeSystem saved = codeSystemDomainService.create(command);
        codeSystemQueryService.clearAllDictCache();
        // Asm：映射持久化结果 DTO
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
        // Gate：请求体与主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新编码系统失败：编码系统ID不能为空");
        }
        // Gate：预置只读体系禁止改元数据
        assertCodeSystemIdWritable(request.getId());
        // Pre：请求映射为领域更新命令
        var command = codesystemMapper.toUpdateCommand(request);
        // Cmd：领域更新编码体系
        CodeSystem saved = codeSystemDomainService.update(command);
        codeSystemQueryService.clearAllDictCache();
        // Asm：映射更新后 DTO
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
        // Gate：主键非空，避免误删全租户术语根
        if (id == null) {
            throw new ValidationException("删除编码系统失败：编码系统ID不能为空");
        }
        // Gate：预置只读体系禁止停用/删除
        assertCodeSystemIdWritable(id);
        // Cmd：领域删除编码体系
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
        // Gate：编码体系主键非空
        if (codeSystemId == null) {
            throw new ValidationException("获取编码列表失败：编码系统ID不能为空");
        }
        // Pre：读侧加载体系下全部概念
        // Asm：映射概念 DTO 列表
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
        // Gate：请求体与编码体系 id 非空
        if (request == null || request.getCodeSystemId() == null) {
            throw new ValidationException("新增编码失败：编码系统ID不能为空");
        }
        // Gate：预置只读体系禁止新增概念
        assertCodeSystemIdWritable(request.getCodeSystemId());
        // Pre：请求映射为领域创建概念命令
        var command = codesystemMapper.toCreateConceptCommand(request);
        // Cmd：领域新增概念
        Concept saved = codeSystemDomainService.createConcept(command);
        codeSystemQueryService.evictDictByCodeSystemId(saved.getCodeSystemId());
        terminologyOptionLoader.evictByCodeSystemId(saved.getCodeSystemId());
    }

    /**
     * 更新已有概念。
     *
     * <p>对应前端：{@code POST /code/updateConcept}。</p>
     *
     * @param request 更新请求（须含概念主键）
     * @throws ValidationException 请求或概念 id 为空
     */
    public void updateConcept(UpdateConceptRequest request) {
        // Gate：请求体与概念主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新编码失败：编码ID不能为空");
        }
        // Gate：预置只读体系禁止改概念
        assertConceptIdWritable(request.getId());
        // Gate：关键字典禁止通过 isActive=false 停用
        if (Boolean.FALSE.equals(request.getIsActive())) {
            assertConceptIdDeletableOrDeactivatable(request.getId());
        }
        // Pre：请求映射为领域更新概念命令
        var command = codesystemMapper.toUpdateConceptCommand(request);
        // Cmd：领域更新概念
        Concept saved = codeSystemDomainService.updateConcept(command);
        // Asm：失效该概念体系下的字典与术语选项缓存
        codeSystemQueryService.evictDictByCodeSystemId(saved.getCodeSystemId());
        terminologyOptionLoader.evictByCodeSystemId(saved.getCodeSystemId());
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
        // Gate：概念主键非空
        if (id == null) {
            throw new ValidationException("删除编码失败：编码ID不能为空");
        }
        // Gate：关键字典禁止删除概念
        assertConceptIdDeletableOrDeactivatable(id);
        // Pre：删除前失效对应体系字典缓存
        codeSystemQueryService.findConceptById(id).ifPresent(concept -> {
            codeSystemQueryService.evictDictByCodeSystemId(concept.getCodeSystemId());
            terminologyOptionLoader.evictByCodeSystemId(concept.getCodeSystemId());
        });
        // Cmd：领域删除概念
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
        // Gate：只读枚举目录，无写侧门禁
        // Pre：classpath 扫描枚举简单类名
        // Asm：映射为前端表格兼容行
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
        // Gate：枚举简单类名非空
        if (request == null || request.getEnumName() == null || request.getEnumName().trim().isEmpty()) {
            throw new ValidationException("获取枚举字典失败：枚举名称不能为空");
        }
        String enumName = request.getEnumName().trim();
        // Pre：从缓存映射解析枚举 Class
        Class<? extends Enum<?>> enumClass = getEnumDictMap().get(enumName);
        // Gate：未出现在扫描包内的名称视为非法
        if (enumClass == null) {
            throw new ValidationException("获取枚举字典失败：不支持的枚举 " + request.getEnumName());
        }

        // 业务分支：EnumInterface 优先走完整字段映射
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

        // 业务分支：降级为术语 OptionDto 再适配 ConceptDto
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<OptionDto> options = codeSystemQueryService.getOptionListByEnum((Class) enumClass);
        // Asm：映射为表格兼容概念行
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

    /**
     * 清空术语概念字典缓存与术语选项软缓存。
     *
     * <p>适用：SQL/运维脚本直接写入 {@code ter_concept} 后，业务下拉仍回显旧选项或裸 code；
     * 调用本接口即可让后续 init/下拉回源数据库，无需重启进程。</p>
     *
     * <p>对应前端：{@code POST /code/refreshConceptCache}（数据字典页「刷新缓存」）。</p>
     *
     * <p>注意：Java 枚举选项来自 classpath，不在本缓存范围内，改枚举仍须发版；
     * 诊疗业务类型已改术语 {@code activity_type_code}，本接口刷新后即可被目录 init 读到。</p>
     */
    public void refreshConceptCache() {
        // Gate：运维刷新入口，无入参；任意登录可触发（与字典维护同权限面由网关/菜单控制）
        // Cmd：清空概念字典 Caffeine 全量分片（CodeSystemQueryService）
        codeSystemQueryService.clearAllDictCache();
        // Cmd：清空应用层术语选项软缓存（TerminologyOptionLoader；须与概念缓存一并失效）
        terminologyOptionLoader.evictAll();
        // Asm：审计日志，便于运维确认本进程已刷缓存
        log.info("术语概念字典缓存与术语选项软缓存已手工清空");
    }

    /**
     * 断言编码体系 name 允许页面维护（非预置只读集合）。
     *
     * @param name 编码体系唯一名
     * @throws ValidationException 名称为预置只读体系时
     */
    private void assertCodeSystemNameWritable(String name) {
        if (name != null
            && TerminologyCodeSystemNameConstants.PAGE_READONLY_CODE_SYSTEM_NAMES.contains(name.trim())) {
            throw new ValidationException(MSG_READONLY_CODE_SYSTEM);
        }
    }

    /**
     * 断言编码体系主键允许页面维护。
     *
     * @param codeSystemId 编码体系主键
     * @throws ValidationException 体系不存在或为预置只读体系时
     */
    private void assertCodeSystemIdWritable(Long codeSystemId) {
        CodeSystem codeSystem = codeSystemQueryService.findById(codeSystemId)
            .orElseThrow(() -> new ValidationException("编码系统不存在"));
        assertCodeSystemNameWritable(codeSystem.getName());
    }

    /**
     * 断言概念所属编码体系允许页面维护。
     *
     * @param conceptId 概念主键
     * @throws ValidationException 概念不存在或所属体系为预置只读时
     */
    private void assertConceptIdWritable(Long conceptId) {
        Concept concept = codeSystemQueryService.findConceptById(conceptId)
            .orElseThrow(() -> new ValidationException("编码不存在"));
        assertCodeSystemIdWritable(concept.getCodeSystemId());
    }

    /**
     * 断言概念允许删除或停用（非关键口径集合）。
     *
     * @param conceptId 概念主键
     * @throws ValidationException 概念不存在或所属体系禁止删除/停用时
     */
    private void assertConceptIdDeletableOrDeactivatable(Long conceptId) {
        Concept concept = codeSystemQueryService.findConceptById(conceptId)
            .orElseThrow(() -> new ValidationException("编码不存在"));
        CodeSystem codeSystem = codeSystemQueryService.findById(concept.getCodeSystemId())
            .orElseThrow(() -> new ValidationException("编码系统不存在"));
        String name = codeSystem.getName();
        if (name != null
            && TerminologyCodeSystemNameConstants.PAGE_NO_DELETE_DEACTIVATE_CODE_SYSTEM_NAMES
                .contains(name.trim())) {
            throw new ValidationException(MSG_NO_DELETE_DEACTIVATE_CODE_SYSTEM);
        }
    }

}
