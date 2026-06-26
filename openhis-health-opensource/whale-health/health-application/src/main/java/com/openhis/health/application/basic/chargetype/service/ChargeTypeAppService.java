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

package com.openhis.health.application.basic.chargetype.service;

import com.openhis.health.application.basic.chargetype.dto.ChargeTypeDto;
import com.openhis.health.application.basic.chargetype.dto.ChargeTypeInitDto;
import com.openhis.health.application.basic.chargetype.mapper.ChargeTypeMapper;
import com.openhis.health.application.basic.chargetype.request.CreateChargeTypeRequest;
import com.openhis.health.application.basic.chargetype.request.UpdateChargeTypeRequest;
import com.openhis.health.domain.financial.contract.command.DeleteContractCommand;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractDomainService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础数据「费用性质」应用服务：对外以收费类型呈现，对内复用财务域「合约（Contract）」聚合的增删改查。
 *
 * <p>分类码与展示名以 {@link ChargeTypeCategory} 为唯一真源（B.10）；列表数据来自
 * {@link ContractQueryService} 全量合约，再经 {@link ChargeTypeMapper} 投影为前端 DTO。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChargeTypeAppService {

    /** 合约读侧：列表与按 id 详情，对外呈现为费用性质。 */
    private final ContractQueryService contractQueryService;
    /** 合约写侧：创建、编辑与逻辑删除。 */
    private final ContractDomainService contractDomainService;
    /** 请求/读模型与 DTO、领域命令之间的装配。 */
    private final ChargeTypeMapper chargeTypeMapper;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 返回新建/编辑表单所需的分类下拉数据。
     *
     * @return 初始化 DTO，当前仅含分类 {@link com.openhis.whale.core.application.dto.OptionDto} 列表
     */
    public ChargeTypeInitDto init() {
        ChargeTypeInitDto dto = new ChargeTypeInitDto();
        // 与 ChargeTypeCategory 枚举声明序及 sortOrder 一致，避免下拉与列表展示漂移（B.10.8）。
        List<OptionDto> options = new ArrayList<>();
        for (ChargeTypeCategory category : ChargeTypeCategory.values()) {
            options.add(new OptionDto(category.getSortOrder(), category.getCode(), category.getDisplay()));
        }
        dto.setCategoryOptions(options);
        return dto;
    }

    /**
     * 查询全部费用性质列表（底层为全部合约行），并为每条补充分类中文文案。
     *
     * @return 费用性质 DTO 列表；无数据时返回空列表（非 {@code null}）
     */
    public List<ChargeTypeDto> getChargeTypeList() {
        List<ChargeTypeDto> result = new ArrayList<>();
        // 当前主数据量可控，直接走查询服务全量；若后续增长须在查询服务增加分页或条件过滤（B.2）
        List<ContractListItemQueryModel> items = contractQueryService.listAllContracts();
        for (ContractListItemQueryModel item : items) {
            ChargeTypeDto dto = chargeTypeMapper.toDto(item);
            // 映射层对异常行可返回 null，此处跳过避免把空壳返回给前端
            if (dto == null) {
                continue;
            }
            dto.setCategoryEnumText(resolveCategoryText(dto.getCategoryEnum()));
            result.add(dto);
        }
        return result;
    }

    /**
     * 新增费用性质：校验入参后创建合约并回读详情。
     *
     * @param request 创建请求
     * @return 新建记录的完整 DTO（含分类展示文案）
     * @throws ValidationException 参数不合法时
     */
    public ChargeTypeDto createChargeType(CreateChargeTypeRequest request) {
        validateCreateRequest(request);
        // 领域返回技术主键，再统一走 getChargeType 装配展示字段，避免与编辑路径不一致
        Long id = contractDomainService.createContract(chargeTypeMapper.toCreateCommand(request));
        log.info("Created charge type (费用性质): {}", id);
        return getChargeType(id);
    }

    /**
     * 更新费用性质：校验后下发编辑命令并回读详情。
     *
     * @param request 编辑请求，须含主键
     * @return 更新后的完整 DTO
     * @throws ValidationException 参数不合法时
     */
    public ChargeTypeDto updateChargeType(UpdateChargeTypeRequest request) {
        validateUpdateRequest(request);
        contractDomainService.editContract(chargeTypeMapper.toEditCommand(request));
        log.info("Updated charge type (费用性质): {}", request.getId());
        return getChargeType(request.getId());
    }

    /**
     * 删除费用性质（逻辑删除语义由领域/底座实现）。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void deleteChargeType(Long id) {
        if (id == null) {
            throw new ValidationException("删除费用性质失败：id 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "请先登录后再删除费用性质");
        DeleteContractCommand command = new DeleteContractCommand();
        command.setId(id);
        command.setOperatorId(operatorId);
        contractDomainService.deleteContract(command);
        log.info("Deleted charge type (费用性质): {}", id);
    }

    /**
     * 按主键加载单条费用性质详情。
     *
     * @param id 合约主键
     * @return 费用性质 DTO
     * @throws ValidationException id 为空或记录不存在时
     */
    public ChargeTypeDto getChargeType(Long id) {
        if (id == null) {
            throw new ValidationException("费用性质ID不能为空");
        }
        ChargeTypeDto dto = chargeTypeMapper.toDto(contractQueryService.findContractById(id));
        if (dto == null) {
            throw new ValidationException("费用性质不存在");
        }
        dto.setCategoryEnumText(resolveCategoryText(dto.getCategoryEnum()));
        return dto;
    }

    /**
     * 新增路径入参校验：名称、编码、分类与显示顺序为必填/非负。
     *
     * @param request 创建请求
     * @throws ValidationException 任一规则不满足时
     */
    private void validateCreateRequest(CreateChargeTypeRequest request) {
        if (request == null) {
            throw new ValidationException("新增费用性质失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("新增费用性质失败：名称不能为空");
        }
        if (request.getBusNo() == null || request.getBusNo().isBlank()) {
            throw new ValidationException("新增费用性质失败：编码不能为空");
        }
        if (request.getCategoryEnum() == null || request.getCategoryEnum().isBlank()) {
            throw new ValidationException("新增费用性质失败：分类不能为空");
        }
        if (request.getDisplayOrder() == null || request.getDisplayOrder() < 0) {
            throw new ValidationException("新增费用性质失败：显示顺序必须大于等于0");
        }
    }

    /**
     * 编辑路径入参校验：在公共字段校验前先保证主键存在。
     *
     * @param request 编辑请求
     * @throws ValidationException id 缺失或公共字段不合法时
     */
    private void validateUpdateRequest(UpdateChargeTypeRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("编辑费用性质失败：id 不能为空");
        }
        validateCreateRequest(request);
    }

    /**
     * 将分类业务码解析为列表展示用中文；未知码原样回传，便于排障。
     *
     * @param categoryEnum 分类码（与下拉 code 对齐）
     * @return 中文简称；入参为 {@code null} 时返回 {@code null}
     */
    private String resolveCategoryText(String categoryEnum) {
        if (categoryEnum == null) {
            return null;
        }
        String key = categoryEnum.trim();
        ChargeTypeCategory parsed = EnumUtil.fromCode(ChargeTypeCategory.class, key);
        if (parsed != null) {
            return parsed.getDisplay();
        }
        return key;
    }
}
