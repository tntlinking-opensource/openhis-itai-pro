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

package com.openhis.health.domain.workflow.activitydefinition.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ActivityDefinitionActivatedEvent;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ActivityDefinitionCreatedEvent;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ActivityDefinitionPriceUpdatedEvent;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ActivityDefinitionRetiredEvent;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ActivityDefinitionUpdatedEvent;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.health.domain.workflow.activitydefinition.command.ActivateActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.ImportActivityCatalogCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.ImportActivityCatalogResult;
import com.openhis.health.domain.workflow.activitydefinition.command.DeleteActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.RetireActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.SyncActivityYbMappingCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinitionChild;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionChildRepository;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗项目定义写侧领域服务。
 *
 * <p>边界：本服务仅对 ActivityDefinition 聚合内写模型落库与事件发布负责；收费定价事实、医保网关交互等跨域事务由应用服务编排。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityDefinitionDomainService {

    private final ActivityDefinitionRepository activityDefinitionRepository;
    private final ActivityDefinitionChildRepository activityDefinitionChildRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 诊疗项目定义 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#ACTIVITY_DEFINITION}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 新建诊疗项目。
     *
     * @param command 命令（支持 item/items）
     * @return 新建后的主键（当批量输入时返回首条保存记录的主键）
     */
    public Long createActivityDefinition(CreateActivityDefinitionCommand command) {
        List<CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand> items = command.resolveItems();

        List<ActivityDefinition> toSave = new ArrayList<>();
        Map<ActivityDefinition, List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand>> childrenByDefinition = new HashMap<>();

        for (CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand item : items) {
            ActivityDefinition definition = new ActivityDefinition();

            // 业务编码允许外部传入；若为空则系统生成稳定编码。
            if (StringUtils.isBlank(item.getBusNo())) {
                definition.setBusNo(this.generateBusNo());
            } else {
                definition.setBusNo(item.getBusNo().trim());
            }

            definition.setCategoryCode(item.getCategoryCode());
            definition.setName(item.getName());
            definition.setPackageUnitCode(item.getPackageUnitCode());
            definition.setActivityVersion(item.getActivityVersion());
            definition.setMerchandiseName(item.getMerchandiseName());
            definition.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
            definition.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
            definition.rebuildMerchandiseSearchCodes();
            definition.setTypeEnum(item.getTypeEnum());
            definition.applyInitStatus(this.resolveInitStatus(item.getStatusEnum()));
            definition.setDescriptionText(item.getDescriptionText());
            definition.setBodySiteCode(item.getBodySiteCode());
            definition.setSpecimenCode(item.getSpecimenCode());
            definition.setOrgId(item.getOrgId());
            definition.setLocationId(item.getLocationId());
            definition.setRuleId(item.getRuleId());
            definition.setPricingFlag(item.getPricingFlag());
            definition.setYbFlag(item.getYbFlag());
            definition.setYbNo(item.getYbNo());
            definition.setYbMatchFlag(item.getYbMatchFlag());
            definition.setChrgitmLv(item.getChrgitmLv());
            definition.setChildrenJson(item.getChildrenJson());

            // 前置校验：必填与业务编码存在性。
            definition.assertRequiredFieldsProvided();
            definition.assertBusNoGenerated();
            definition.rebuildSearchCodes();

            // 唯一性校验：先内存去重，再下推数据库校验。
            this.assertBusNoUnique(definition.getBusNo());

            toSave.add(definition);

            // 子项写入：创建阶段允许为空；若提供则按提供集合后续落库（需先获得聚合根主键）。
            childrenByDefinition.put(definition, item.getChildren());
        }

        // 先落库，再发布事件，避免“幽灵事件”。
        List<ActivityDefinition> saved = activityDefinitionRepository.saveAll(toSave);
        List<ActivityDefinitionChild> childrenToSave = new ArrayList<>();
        for (ActivityDefinition definition : saved) {
            List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> childItems = childrenByDefinition.get(definition);
            List<ActivityDefinitionChild> builtChildren = this.buildChildren(definition, childItems);
            childrenToSave.addAll(builtChildren);
        }
        // 业务分支处理。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }

        activityDefinitionRepository.flush();

        for (ActivityDefinition definition : saved) {
            domainEventPublisher.publish(new ActivityDefinitionCreatedEvent(
                definition.getId(), definition.getBusNo(), definition.getName()));
        }

        // 返回首条新建记录的主键；若入参为空则 saved 为空，返回 null。
        if (saved.isEmpty()) {
            return null;
        }
        return saved.get(0).getId();
    }

    /**
     * 维护诊疗项目。
     *
     * @param command 命令（支持 item/items）
     */
    public void updateActivityDefinition(UpdateActivityDefinitionCommand command) {
        List<UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand> items = command.resolveItems();

        List<ActivityDefinition> toSave = new ArrayList<>();
        List<ActivityDefinitionChild> childrenToSave = new ArrayList<>();
        List<ActivityDefinitionChild> childrenToDelete = new ArrayList<>();
        Map<Long, List<String>> changedFieldsById = new HashMap<>();
        Map<Long, BigDecimal> oldStandardPriceById = new HashMap<>();
        Map<Long, BigDecimal> newStandardPriceById = new HashMap<>();
        Map<Long, String> priceReasonCodeById = new HashMap<>();

        for (UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand item : items) {
            // 业务分支处理。
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "维护诊疗项目时 id 不能为空");
            }

            ActivityDefinition definition = this.loadActivityDefinition(item.getId());
            List<String> changedFields = new ArrayList<>();

            // 逐字段更新：命令字段为空时不覆盖历史值（与“可编辑字段集合”语义一致）。
            if (item.getName() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getName(), item.getName())) {
                    changedFields.add(FieldNameConstants.NAME);
                }
                definition.setName(item.getName());
                definition.rebuildSearchCodes();
                changedFields.add(FieldNameConstants.PY_STR);
                changedFields.add(FieldNameConstants.WB_STR);
            }
            // 业务分支处理。
            if (item.getCategoryCode() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getCategoryCode(), item.getCategoryCode())) {
                    changedFields.add(FieldNameConstants.CATEGORY_CODE);
                }
                definition.setCategoryCode(item.getCategoryCode());
            }
            // 业务分支处理。
            if (item.getPackageUnitCode() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getPackageUnitCode(), item.getPackageUnitCode())) {
                    changedFields.add(FieldNameConstants.PACKAGE_UNIT_CODE);
                }
                definition.setPackageUnitCode(item.getPackageUnitCode());
            }
            if (item.getActivityVersion() != null) {
                if (!StringUtils.equals(definition.getActivityVersion(), item.getActivityVersion())) {
                    changedFields.add(FieldNameConstants.ACTIVITY_VERSION);
                }
                definition.setActivityVersion(item.getActivityVersion());
            }
            if (item.getMerchandiseName() != null) {
                if (!StringUtils.equals(definition.getMerchandiseName(), item.getMerchandiseName())) {
                    changedFields.add(FieldNameConstants.MERCHANDISE_NAME);
                    changedFields.add(FieldNameConstants.MERCHANDISE_PY_STR);
                    changedFields.add(FieldNameConstants.MERCHANDISE_WB_STR);
                    definition.setMerchandiseName(item.getMerchandiseName());
                    definition.rebuildMerchandiseSearchCodes();
                }
            }
            if (item.getDefEncounterUnitCode() != null) {
                if (!StringUtils.equals(definition.getDefEncounterUnitCode(), item.getDefEncounterUnitCode())) {
                    changedFields.add(FieldNameConstants.DEF_ENCOUNTER_UNIT_CODE);
                }
                definition.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
            }
            if (item.getDefInhospitalUnitCode() != null) {
                if (!StringUtils.equals(definition.getDefInhospitalUnitCode(), item.getDefInhospitalUnitCode())) {
                    changedFields.add(FieldNameConstants.DEF_INHOSPITAL_UNIT_CODE);
                }
                definition.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
            }
            // 业务分支处理。
            if (item.getTypeEnum() != null) {
                // 业务分支处理。
                if (definition.getTypeEnum() == null || !definition.getTypeEnum().equals(item.getTypeEnum())) {
                    changedFields.add(FieldNameConstants.TYPE_ENUM);
                }
                definition.setTypeEnum(item.getTypeEnum());
            }
            // 业务分支处理。
            if (item.getDescriptionText() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getDescriptionText(), item.getDescriptionText())) {
                    changedFields.add(FieldNameConstants.DESCRIPTION_TEXT);
                }
                definition.setDescriptionText(item.getDescriptionText());
            }
            // 业务分支处理。
            if (item.getBodySiteCode() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getBodySiteCode(), item.getBodySiteCode())) {
                    changedFields.add(FieldNameConstants.BODY_SITE_CODE);
                }
                definition.setBodySiteCode(item.getBodySiteCode());
            }
            // 业务分支处理。
            if (item.getSpecimenCode() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getSpecimenCode(), item.getSpecimenCode())) {
                    changedFields.add(FieldNameConstants.SPECIMEN_CODE);
                }
                definition.setSpecimenCode(item.getSpecimenCode());
            }
            // 业务分支处理。
            if (item.getOrgId() != null) {
                // 业务分支处理。
                if (definition.getOrgId() == null || !definition.getOrgId().equals(item.getOrgId())) {
                    changedFields.add(FieldNameConstants.ORG_ID);
                }
                definition.setOrgId(item.getOrgId());
            }
            // 业务分支处理。
            if (item.getLocationId() != null) {
                // 业务分支处理。
                if (definition.getLocationId() == null || !definition.getLocationId().equals(item.getLocationId())) {
                    changedFields.add(FieldNameConstants.LOCATION_ID);
                }
                definition.setLocationId(item.getLocationId());
            }
            // 业务分支处理。
            if (item.getRuleId() != null) {
                // 业务分支处理。
                if (definition.getRuleId() == null || !definition.getRuleId().equals(item.getRuleId())) {
                    changedFields.add(FieldNameConstants.RULE_ID);
                }
                definition.setRuleId(item.getRuleId());
            }
            // 业务分支处理。
            if (item.getPricingFlag() != null) {
                // 业务分支处理。
                if (definition.getPricingFlag() == null || !definition.getPricingFlag().equals(item.getPricingFlag())) {
                    changedFields.add(FieldNameConstants.PRICING_FLAG);
                }
                definition.setPricingFlag(item.getPricingFlag());
            }
            // 业务分支处理。
            if (item.getYbFlag() != null) {
                // 业务分支处理。
                if (definition.getYbFlag() == null || !definition.getYbFlag().equals(item.getYbFlag())) {
                    changedFields.add(FieldNameConstants.YB_FLAG);
                }
                definition.setYbFlag(item.getYbFlag());
            }
            // 业务分支处理。
            if (item.getYbNo() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getYbNo(), item.getYbNo())) {
                    changedFields.add(FieldNameConstants.YB_NO);
                }
                definition.setYbNo(item.getYbNo());
            }
            // 业务分支处理。
            if (item.getYbMatchFlag() != null) {
                // 业务分支处理。
                if (definition.getYbMatchFlag() == null || !definition.getYbMatchFlag().equals(item.getYbMatchFlag())) {
                    changedFields.add(FieldNameConstants.YB_MATCH_FLAG);
                }
                definition.setYbMatchFlag(item.getYbMatchFlag());
            }
            // 业务分支处理。
            if (item.getChrgitmLv() != null) {
                // 业务分支处理。
                if (definition.getChrgitmLv() == null || !definition.getChrgitmLv().equals(item.getChrgitmLv())) {
                    changedFields.add(FieldNameConstants.CHRGITM_LV);
                }
                definition.setChrgitmLv(item.getChrgitmLv());
            }
            // 业务分支处理。
            if (item.getChildrenJson() != null) {
                // 业务分支处理。
                if (!StringUtils.equals(definition.getChildrenJson(), item.getChildrenJson())) {
                    changedFields.add(FieldNameConstants.CHILDREN_JSON);
                }
                definition.setChildrenJson(item.getChildrenJson());
            }

            // 子项同步：若命令带 children，则按“全量对齐 + 逻辑删除旧行”口径执行。
            if (item.getChildren() != null) {
                List<ActivityDefinitionChild> existed = activityDefinitionChildRepository.findByActivityId(definition.getId());
                List<ActivityDefinitionChild> builtChildren = this.alignChildren(definition, existed, item.getChildren(), command.getOperatorId());
                childrenToSave.addAll(builtChildren);

                List<ActivityDefinitionChild> deleted = this.collectChildrenToDelete(definition, existed, item.getChildren(), command.getOperatorId());
                childrenToDelete.addAll(deleted);

                List<ActivityDefinitionChild> finalChildren = this.snapshotNotDeletedChildren(existed, builtChildren, deleted);
                boolean changed = definition.syncChildrenFlag(finalChildren);
                // 业务分支处理。
                if (changed) {
                    changedFields.add(FieldNameConstants.CHILDREN_FLAG);
                }
            }

            // 必填校验：修改后仍须满足 DM BR-ACTDEF-001。
            definition.assertRequiredFieldsProvided();

            toSave.add(definition);
            changedFieldsById.put(definition.getId(), definition.snapshotChangedFields(changedFields));
            oldStandardPriceById.put(definition.getId(), item.getOldStandardPrice());
            newStandardPriceById.put(definition.getId(), item.getNewStandardPrice());
            priceReasonCodeById.put(definition.getId(), item.getPriceChangeReasonCode());
        }

        // 先落主表，再落子表，最后发布事件。
        activityDefinitionRepository.saveAll(toSave);
        // 业务分支处理。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }
        // 业务分支处理。
        if (!childrenToDelete.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToDelete);
        }

        activityDefinitionRepository.flush();

        for (ActivityDefinition definition : toSave) {
            List<String> changedFields = changedFieldsById.get(definition.getId());
            // 业务分支处理。
            if (changedFields == null) {
                changedFields = new ArrayList<>();
            }
            domainEventPublisher.publish(new ActivityDefinitionUpdatedEvent(definition.getId(), String.join(",", changedFields)));

            BigDecimal oldStandardPrice = oldStandardPriceById.get(definition.getId());
            BigDecimal newStandardPrice = newStandardPriceById.get(definition.getId());
            String reasonCode = priceReasonCodeById.get(definition.getId());
            if (oldStandardPrice != null || newStandardPrice != null) {
                domainEventPublisher.publish(new ActivityDefinitionPriceUpdatedEvent(
                    definition.getId(), oldStandardPrice, newStandardPrice, reasonCode));
            }
        }
    }

    /**
     * 批量停用。
     *
     * @param command 命令（支持 item/items）
     */
    public void retireActivityDefinitions(RetireActivityDefinitionsCommand command) {
        List<RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand> items = command.resolveItems();

        List<Long> ids = this.collectIdList(items, "批量停用诊疗项目时 idList 不能为空");
        List<ActivityDefinition> definitions = activityDefinitionRepository.findByIdIn(ids);
        // 业务分支处理。
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "批量停用诊疗项目时存在无效主键");
        }

        for (ActivityDefinition definition : definitions) {
            definition.retire();
        }

        activityDefinitionRepository.saveAll(definitions);
        // 批量操作发布一个聚合事件，携带所有 ID 列表；aggregateId 以 0L 占位（见 ActivityDefinitionRetiredEvent 说明）。
        List<Long> retiredIdList = new ArrayList<>();
        for (ActivityDefinition definition : definitions) {
            retiredIdList.add(definition.getId());
        }
        String retiredStatus = null;
        if (!definitions.isEmpty()) {
            // 批量停用事件载荷带首条状态码，读模型按同源 code 投影。
            retiredStatus = definitions.get(0).getStatusEnum();
        }
        domainEventPublisher.publish(new ActivityDefinitionRetiredEvent(retiredIdList, retiredStatus));
    }

    /**
     * 批量删除（逻辑删除）诊疗项目。
     *
     * @param command 命令（支持 item/items）
     */
    public void deleteActivityDefinitions(DeleteActivityDefinitionsCommand command) {
        List<DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand> items = command.resolveItems();
        List<Long> ids = this.collectIdList(items, "批量删除诊疗项目时 idList 不能为空");
        List<ActivityDefinition> definitions = activityDefinitionRepository.findByIdIn(ids);
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "批量删除诊疗项目时存在无效主键");
        }
        Long operatorId = command.getOperatorId();
        for (ActivityDefinition definition : definitions) {
            definition.markDeleted(operatorId);
        }
        activityDefinitionRepository.saveAll(definitions);
    }

    /**
     * 批量启用（含草稿发布与停用恢复）。
     *
     * <p>维护端「启用」口径：草稿项执行 {@link ActivityDefinition#publish()}，停用项执行 {@link ActivityDefinition#activate()}；
     * 已发布项幂等跳过。对应 DM-WKF-001 §2.3 状态机。</p>
     *
     * @param command 命令（支持 item/items）
     */
    public void activateActivityDefinitions(ActivateActivityDefinitionsCommand command) {
        List<ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand> items = command.resolveItems();

        List<Long> ids = this.collectIdList(items, "批量启用诊疗项目时 idList 不能为空");
        List<ActivityDefinition> definitions = activityDefinitionRepository.findByIdIn(ids);
        // 业务分支处理。
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "批量启用诊疗项目时存在无效主键");
        }

        for (ActivityDefinition definition : definitions) {
            String status = definition.getStatusEnum();
            if (PublicationStatus.DRAFT.getCode().equals(status)) {
                // 新建默认草稿：首次启用即发布，供医嘱开立目录（仅 ACTIVE）检索。
                definition.publish();
            } else if (PublicationStatus.INACTIVE.getCode().equals(status)) {
                definition.activate();
            } else if (PublicationStatus.ACTIVE.getCode().equals(status)) {
                // 已发布：重复启用不报错，便于维护端批量操作。
                continue;
            } else {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "当前发布状态不允许启用：" + status);
            }
        }

        activityDefinitionRepository.saveAll(definitions);
        // 批量操作发布一个聚合事件，携带所有 ID 列表；aggregateId 以 0L 占位（见 ActivityDefinitionActivatedEvent 说明）。
        List<Long> activatedIdList = new ArrayList<>();
        for (ActivityDefinition definition : definitions) {
            activatedIdList.add(definition.getId());
        }
        String activatedStatus = null;
        if (!definitions.isEmpty()) {
            // 批量启用事件载荷带首条状态码，供读模型投影。
            activatedStatus = definitions.get(0).getStatusEnum();
        }
        domainEventPublisher.publish(new ActivityDefinitionActivatedEvent(activatedIdList, activatedStatus));
    }

    /**
     * 批量导入诊疗目录。
     *
     * @param command 命令（支持 item/items）
     * @return 导入结果摘要
     */
    public ImportActivityCatalogResult importActivityCatalog(ImportActivityCatalogCommand command) {
        List<ImportActivityCatalogCommand.ImportActivityCatalogItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            // DOC-CONFLICT: SDD 约定 import 为单次导入动作，但 BatchCommand 默认允许批量；此处按单次导入约束收敛。
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "批量导入诊疗目录一次仅允许提交一组导入 rows");
        }

        ImportActivityCatalogCommand.ImportActivityCatalogItemCommand batch = items.get(0);
        // 业务分支处理。
        if (batch.getRows() == null || batch.getRows().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入诊疗目录 rows 不能为空");
        }

        // 批量入参内存去重：以 busNo 为业务唯一键。
        Map<String, Integer> seenBusNo = new HashMap<>();
        List<String> busNos = new ArrayList<>();
        for (ImportActivityCatalogCommand.ActivityCatalogRowCommand row : batch.getRows()) {
            // 业务分支处理。
            if (StringUtils.isBlank(row.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入诊疗目录时 busNo 不能为空");
            }
            String normalized = row.getBusNo().trim();
            Integer existedLine = seenBusNo.get(normalized);
            // 业务分支处理。
            if (existedLine != null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "导入诊疗目录时 busNo 重复，重复值=" + normalized + "，首次行=" + existedLine);
            }
            seenBusNo.put(normalized, busNos.size() + 1);
            busNos.add(normalized);
        }

        // 批量下推数据库校验：一次性查出已存在记录，避免循环单条 exists。
        List<ActivityDefinition> existed = activityDefinitionRepository.findByBusNoIn(busNos);
        Map<String, ActivityDefinition> existedByBusNo = new HashMap<>();
        for (ActivityDefinition definition : existed) {
            existedByBusNo.put(definition.getBusNo(), definition);
        }

        List<ActivityDefinition> toSave = new ArrayList<>();
        List<ActivityDefinitionChild> childrenToSave = new ArrayList<>();
        List<ActivityDefinitionChild> childrenToDelete = new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;
        List<ImportActivityCatalogResult.ImportFailure> failures = new ArrayList<>();

        for (int idx = 0; idx < batch.getRows().size(); idx++) {
            ImportActivityCatalogCommand.ActivityCatalogRowCommand row = batch.getRows().get(idx);
            int lineNo = idx + 1;
            try {
                String busNo = row.getBusNo().trim();
                ActivityDefinition target = existedByBusNo.get(busNo);

                // 业务分支处理。
                if (target == null) {
                    ActivityDefinition created = new ActivityDefinition();
                    created.setBusNo(busNo);
                    created.setCategoryCode(row.getCategoryCode());
                    created.setName(row.getName());
                    created.setPackageUnitCode(row.getPackageUnitCode());
                    created.setActivityVersion(row.getActivityVersion());
                    created.setMerchandiseName(row.getMerchandiseName());
                    created.setDefEncounterUnitCode(row.getDefEncounterUnitCode());
                    created.setDefInhospitalUnitCode(row.getDefInhospitalUnitCode());
                    created.rebuildMerchandiseSearchCodes();
                    created.setTypeEnum(row.getTypeEnum());
                    created.applyInitStatus(this.resolveInitStatus(row.getStatusEnum()));
                    created.setDescriptionText(row.getDescriptionText());
                    created.setBodySiteCode(row.getBodySiteCode());
                    created.setSpecimenCode(row.getSpecimenCode());
                    created.setOrgId(row.getOrgId());
                    created.setLocationId(row.getLocationId());
                    created.setRuleId(row.getRuleId());
                    created.setPricingFlag(row.getPricingFlag());
                    created.setYbFlag(row.getYbFlag());
                    created.setYbNo(row.getYbNo());
                    created.setYbMatchFlag(row.getYbMatchFlag());
                    created.setChrgitmLv(row.getChrgitmLv());
                    created.setChildrenJson(row.getChildrenJson());

                    created.assertRequiredFieldsProvided();
                    created.assertBusNoGenerated();
                    created.rebuildSearchCodes();

                    toSave.add(created);
                    createdCount++;
                    // 子项写入依赖聚合根主键，落库后再构建并保存。
                } else {
                    ActivityDefinition updated = target;
                    updated.setCategoryCode(row.getCategoryCode());
                    updated.setName(row.getName());
                    updated.setPackageUnitCode(row.getPackageUnitCode());
                    updated.setActivityVersion(row.getActivityVersion());
                    updated.setMerchandiseName(row.getMerchandiseName());
                    updated.setDefEncounterUnitCode(row.getDefEncounterUnitCode());
                    updated.setDefInhospitalUnitCode(row.getDefInhospitalUnitCode());
                    updated.rebuildMerchandiseSearchCodes();
                    updated.setTypeEnum(row.getTypeEnum());
                    // 业务分支处理。
                    if (row.getStatusEnum() != null) {
                        updated.setStatusEnum(row.getStatusEnum());
                    }
                    updated.setDescriptionText(row.getDescriptionText());
                    updated.setBodySiteCode(row.getBodySiteCode());
                    updated.setSpecimenCode(row.getSpecimenCode());
                    updated.setOrgId(row.getOrgId());
                    updated.setLocationId(row.getLocationId());
                    updated.setRuleId(row.getRuleId());
                    updated.setPricingFlag(row.getPricingFlag());
                    updated.setYbFlag(row.getYbFlag());
                    updated.setYbNo(row.getYbNo());
                    updated.setYbMatchFlag(row.getYbMatchFlag());
                    updated.setChrgitmLv(row.getChrgitmLv());
                    updated.setChildrenJson(row.getChildrenJson());

                    updated.assertRequiredFieldsProvided();
                    updated.rebuildSearchCodes();

                    toSave.add(updated);
                    updatedCount++;

                    List<ActivityDefinitionChild> existedChildren = activityDefinitionChildRepository.findByActivityId(updated.getId());
                    childrenToSave.addAll(this.alignChildrenFromImportRow(updated, existedChildren, row.getChildren(), command.getOperatorId()));
                    childrenToDelete.addAll(this.collectChildrenToDeleteFromImportRow(updated, existedChildren, row.getChildren(), command.getOperatorId()));
                }
            } catch (BusinessException ex) {
                failures.add(new ImportActivityCatalogResult.ImportFailure(lineNo, ex.getMessage()));
            }
        }

        // 落库顺序：主表 → 子表新增/更新 → 子表逻辑删除 → 事件发布。
        List<ActivityDefinition> saved = activityDefinitionRepository.saveAll(toSave);

        // 为新增记录补齐子项（需要主键）。
        for (ActivityDefinition definition : saved) {
            ActivityDefinition existedEntity = existedByBusNo.get(definition.getBusNo());
            // 业务分支处理。
            if (existedEntity == null) {
                ImportActivityCatalogCommand.ActivityCatalogRowCommand row = this.findRowByBusNo(batch.getRows(), definition.getBusNo());
                // 业务分支处理。
                if (row != null) {
                    childrenToSave.addAll(this.buildChildrenFromImportRow(definition, row.getChildren()));
                }
            }
        }

        // 业务分支处理。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }
        // 业务分支处理。
        if (!childrenToDelete.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToDelete);
        }

        // 发布事件：先确保主表主键可用，再逐行发布。
        for (ActivityDefinition definition : saved) {
            ActivityDefinition existedEntity = existedByBusNo.get(definition.getBusNo());
            // 业务分支处理。
            if (existedEntity == null) {
                domainEventPublisher.publish(new ActivityDefinitionCreatedEvent(definition.getId(), definition.getBusNo(), definition.getName()));
            } else {
                // 导入更新：不逐字段差分 changedFields，约定传空串；下游以 aggregateId 做全量刷新或按业务另行补偿。
                domainEventPublisher.publish(new ActivityDefinitionUpdatedEvent(definition.getId(), ""));
            }
        }

        ImportActivityCatalogResult result = new ImportActivityCatalogResult();
        result.setCreatedCount(createdCount);
        result.setUpdatedCount(updatedCount);
        result.setFailureCount((long) failures.size());
        result.setFailures(failures);
        return result;
    }

    /**
     * 同步医保对码回写。
     *
     * @param command 命令（支持 item/items）
     */
    public void syncActivityYbMapping(SyncActivityYbMappingCommand command) {
        List<SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand> items = command.resolveItems();

        List<Long> ids = CommandBatchUtil.extractIds(items, SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand::getActivityId);
        // 业务分支处理。
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 不能为空");
        }

        List<ActivityDefinition> definitions = activityDefinitionRepository.findByIdIn(ids);
        // 业务分支处理。
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "同步医保对码回写时存在无效主键");
        }

        Map<Long, SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand> byId = new HashMap<>();
        for (SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item : items) {
            // 业务分支处理。
            if (item.getActivityId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 不能为空");
            }
            // 业务分支处理。
            if (byId.containsKey(item.getActivityId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 重复");
            }
            byId.put(item.getActivityId(), item);
        }

        for (ActivityDefinition definition : definitions) {
            SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item = byId.get(definition.getId());
            // 业务分支处理。
            if (item == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时存在无法匹配的 activityId");
            }
            definition.setYbNo(item.getYbNo());
            definition.setYbMatchFlag(item.getYbMatchFlag());
            definition.setChrgitmLv(item.getChrgitmLv());
        }

        activityDefinitionRepository.saveAll(definitions);
        for (ActivityDefinition definition : definitions) {
            // syncActivityYbMapping 固定更新 ybNo/ybMatchFlag/chrgitmLv 三个字段。
            domainEventPublisher.publish(new ActivityDefinitionUpdatedEvent(
                definition.getId(), "ybNo,ybMatchFlag,chrgitmLv"));
        }
    }

    /**
     * 私有方法：loadActivityDefinition。
     */
    private ActivityDefinition loadActivityDefinition(Long id) {
        return activityDefinitionRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在"));
    }

    /**
     * 私有方法：assertBusNoUnique。
     */
    private void assertBusNoUnique(String busNo) {
        // 业务分支处理。
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目业务编码不能为空");
        }
        // 业务分支处理。
        if (activityDefinitionRepository.existsByBusNo(busNo)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目业务编码已存在，busNo=" + busNo);
        }
    }

    /**
     * 私有方法：generateBusNo。
     *
     * <p>诊疗项目业务编码生成：使用 {@link AggregateTypeConstants#ACTIVITY_DEFINITION} 前缀获取序列号。</p>
     *
     * @return 生成的业务编码
     */
    private String generateBusNo() {
        return businessSequenceService.getSeq(AggregateTypeConstants.ACTIVITY_DEFINITION);
    }

    /**
     * 创建路径初始化状态兜底：未传则默认草稿。
     *
     * <p>BR-ACTDEF-002：新建诊疗项目时状态默认为 DRAFT；若外部传入状态则使用传入值。</p>
     *
     * @param statusEnum 外部传入的状态枚举
     * @return 最终使用的状态码
     */
    private String resolveInitStatus(String statusEnum) {
        if (StringUtils.isBlank(statusEnum)) {
            return PublicationStatus.DRAFT.getCode();
        }
        return statusEnum;
    }

    /**
     * 私有方法：buildChildren。
     *
     * <p>根据创建命令的子项列表，构建诊疗项目子项实体集合。执行以下逻辑：</p>
     * <ul>
     *   <li>Gate：校验子项 childrenId 非空且在同一父项下不重复（BR-ACTDEF-007）</li>
     *   <li>Cmd：创建子项实体并设置关联关系</li>
     *   <li>Asm：返回构建完成的子项列表</li>
     * </ul>
     *
     * @param definition 父诊疗项目实体
     * @param children   创建命令的子项列表
     * @return 构建完成的子项实体列表
     * @throws BusinessException 子项 childrenId 为空或重复时
     */
    private List<ActivityDefinitionChild> buildChildren(
        ActivityDefinition definition,
        List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> children) {
        List<ActivityDefinitionChild> built = new ArrayList<>();
        // 业务分支处理：子项列表为空时，标记父项无子项并返回空列表。
        if (children == null || children.isEmpty()) {
            definition.setChildrenFlag(Boolean.FALSE);
            return built;
        }

        // 同一父项下 childrenId 不重复（BR-ACTDEF-007）。
        Set<Long> seenChildrenId = new HashSet<>();
        for (CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : children) {
            // 业务分支处理。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项 childrenId 不能为空");
            }
            // 业务分支处理。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同一父项下子项 childrenId 不允许重复");
            }
            seenChildrenId.add(item.getChildrenId());

            ActivityDefinitionChild child = new ActivityDefinitionChild();
            child.setActivityId(definition.getId());
            child.setChildrenId(item.getChildrenId());
            child.setChildrenNum(item.getChildrenNum());
            child.setChildrenName(item.getChildrenName());
            child.assertChildrenIdProvided();
            child.assertChildrenNameValidWhenProvided();
            built.add(child);
        }

        definition.setChildrenFlag(Boolean.TRUE);
        return built;
    }

    /**
     * 私有方法：buildChildrenFromImportRow。
     *
     * <p>从导入行构建诊疗项目子项实体集合，与 {@link #buildChildren} 逻辑类似，用于导入路径。</p>
     *
     * @param definition 父诊疗项目实体
     * @param children   导入命令的子项列表
     * @return 构建完成的子项实体列表
     * @throws BusinessException 子项 childrenId 为空或重复时
     */
    private List<ActivityDefinitionChild> buildChildrenFromImportRow(
        ActivityDefinition definition,
        List<ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand> children) {
        List<ActivityDefinitionChild> built = new ArrayList<>();
        // 业务分支处理：导入子项为空时，标记父项无子项并返回空列表。
        if (children == null || children.isEmpty()) {
            definition.setChildrenFlag(Boolean.FALSE);
            return built;
        }

        Set<Long> seenChildrenId = new HashSet<>();
        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : children) {
            // 业务分支处理。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 不能为空");
            }
            // 业务分支处理。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 重复");
            }
            seenChildrenId.add(item.getChildrenId());

            ActivityDefinitionChild child = new ActivityDefinitionChild();
            child.setActivityId(definition.getId());
            child.setChildrenId(item.getChildrenId());
            child.setChildrenNum(item.getChildrenNum());
            child.setChildrenName(item.getChildrenName());
            child.assertChildrenIdProvided();
            child.assertChildrenNameValidWhenProvided();
            built.add(child);
        }

        definition.setChildrenFlag(Boolean.TRUE);
        return built;
    }

    /**
     * 私有方法：alignChildren。
     *
     * <p>子项全量对齐：将输入子项列表与现存子项对比，执行新增或更新操作。</p>
     * <ul>
     *   <li>Pre：按 childrenId 建立现存子项索引</li>
     *   <li>Cmd：遍历输入子项，存在则更新，不存在则新建</li>
     *   <li>Asm：返回待保存的子项列表</li>
     * </ul>
     *
     * @param definition    父诊疗项目实体
     * @param existed       现存子项列表
     * @param inputChildren 输入子项命令列表
     * @param operatorId    操作人ID
     * @return 待保存的子项实体列表
     * @throws BusinessException childrenId 为空或重复时
     */
    private List<ActivityDefinitionChild> alignChildren(
        ActivityDefinition definition,
        List<ActivityDefinitionChild> existed,
        List<UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> inputChildren,
        Long operatorId) {
        List<ActivityDefinitionChild> toSave = new ArrayList<>();

        // Pre：按 childrenId 建立现存子项索引，加速后续查找。
        Map<Long, ActivityDefinitionChild> existedByChildrenId = new HashMap<>();
        for (ActivityDefinitionChild child : existed) {
            existedByChildrenId.put(child.getChildrenId(), child);
        }

        Set<Long> seenChildrenId = new HashSet<>();
        for (UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            // 业务分支处理。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项 childrenId 不能为空");
            }
            // 业务分支处理。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同一父项下子项 childrenId 不允许重复");
            }
            seenChildrenId.add(item.getChildrenId());

            ActivityDefinitionChild target = existedByChildrenId.get(item.getChildrenId());
            // 业务分支处理。
            if (target == null) {
                target = new ActivityDefinitionChild();
                target.setActivityId(definition.getId());
                target.setChildrenId(item.getChildrenId());
            } else {
                target.assertBelongsTo(definition.getId());
            }
            target.setChildrenNum(item.getChildrenNum());
            target.setChildrenName(item.getChildrenName());
            target.assertChildrenIdProvided();
            target.assertChildrenNameValidWhenProvided();
            toSave.add(target);
        }

        return toSave;
    }

    /**
     * 私有方法：collectChildrenToDelete。
     *
     * <p>收集需要逻辑删除的子项：存在于数据库但不在输入列表中的子项。</p>
     * <ul>
     *   <li>Pre：收集输入列表中所有需要保留的 childrenId</li>
     *   <li>Cmd：遍历现存子项，标记不在保留列表中的为删除</li>
     * </ul>
     *
     * @param definition    父诊疗项目实体
     * @param existed       现存子项列表
     * @param inputChildren 输入子项命令列表
     * @param operatorId    操作人ID（用于审计字段）
     * @return 待逻辑删除的子项实体列表
     */
    private List<ActivityDefinitionChild> collectChildrenToDelete(
        ActivityDefinition definition,
        List<ActivityDefinitionChild> existed,
        List<UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> inputChildren,
        Long operatorId) {
        // Pre：收集所有需要保留的 childrenId。
        Set<Long> keepChildrenId = new HashSet<>();
        for (UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            // 业务分支处理：仅收集非空的 childrenId。
            if (item.getChildrenId() != null) {
                keepChildrenId.add(item.getChildrenId());
            }
        }

        List<ActivityDefinitionChild> toDelete = new ArrayList<>();
        for (ActivityDefinitionChild child : existed) {
            // 业务分支处理：childrenId 为空的跳过处理。
            if (child.getChildrenId() == null) {
                continue;
            }
            // 业务分支处理：不在保留列表中的子项标记为删除。
            if (!keepChildrenId.contains(child.getChildrenId())) {
                child.markDeleted(operatorId);
                toDelete.add(child);
            }
        }
        return toDelete;
    }

    /**
     * 私有方法：alignChildrenFromImportRow。
     *
     * <p>导入路径的子项全量对齐：将导入子项与现存子项对比，执行新增或更新操作。</p>
     *
     * @param definition    父诊疗项目实体
     * @param existed       现存子项列表
     * @param inputChildren 导入命令的子项列表
     * @param operatorId    操作人ID
     * @return 待保存的子项实体列表
     */
    private List<ActivityDefinitionChild> alignChildrenFromImportRow(
        ActivityDefinition definition,
        List<ActivityDefinitionChild> existed,
        List<ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand> inputChildren,
        Long operatorId) {
        List<ActivityDefinitionChild> toSave = new ArrayList<>();

        // 业务分支处理：输入为空时直接返回空列表。
        if (inputChildren == null) {
            return toSave;
        }

        // Pre：按 childrenId 建立现存子项索引。
        Map<Long, ActivityDefinitionChild> existedByChildrenId = new HashMap<>();
        for (ActivityDefinitionChild child : existed) {
            existedByChildrenId.put(child.getChildrenId(), child);
        }

        Set<Long> seenChildrenId = new HashSet<>();
        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            // 业务分支处理。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 不能为空");
            }
            // 业务分支处理。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 重复");
            }
            seenChildrenId.add(item.getChildrenId());

            ActivityDefinitionChild target = existedByChildrenId.get(item.getChildrenId());
            // 业务分支处理。
            if (target == null) {
                target = new ActivityDefinitionChild();
                target.setActivityId(definition.getId());
                target.setChildrenId(item.getChildrenId());
            } else {
                target.assertBelongsTo(definition.getId());
            }
            target.setChildrenNum(item.getChildrenNum());
            target.setChildrenName(item.getChildrenName());
            target.assertChildrenIdProvided();
            target.assertChildrenNameValidWhenProvided();
            toSave.add(target);
        }

        return toSave;
    }

    /**
     * 私有方法：collectChildrenToDeleteFromImportRow。
     *
     * <p>导入路径的子项删除收集：存在于数据库但不在导入列表中的子项。</p>
     *
     * @param definition    父诊疗项目实体
     * @param existed       现存子项列表
     * @param inputChildren 导入命令的子项列表
     * @param operatorId    操作人ID
     * @return 待逻辑删除的子项实体列表
     */
    private List<ActivityDefinitionChild> collectChildrenToDeleteFromImportRow(
        ActivityDefinition definition,
        List<ActivityDefinitionChild> existed,
        List<ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand> inputChildren,
        Long operatorId) {
        List<ActivityDefinitionChild> toDelete = new ArrayList<>();
        // 业务分支处理：输入为空时无需删除，直接返回。
        if (inputChildren == null) {
            return toDelete;
        }

        // Pre：收集所有需要保留的 childrenId。
        Set<Long> keepChildrenId = new HashSet<>();
        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            // 业务分支处理：仅收集非空的 childrenId。
            if (item.getChildrenId() != null) {
                keepChildrenId.add(item.getChildrenId());
            }
        }

        for (ActivityDefinitionChild child : existed) {
            // 业务分支处理：childrenId 为空的跳过处理。
            if (child.getChildrenId() == null) {
                continue;
            }
            // 业务分支处理：不在保留列表中的子项标记为删除。
            if (!keepChildrenId.contains(child.getChildrenId())) {
                child.markDeleted(operatorId);
                toDelete.add(child);
            }
        }
        return toDelete;
    }

    /**
     * 私有方法：collectIdList。
     *
     * <p>从批量命令项中提取并去重 ID 列表。支持停用/启用命令的类型。</p>
     * <ul>
     *   <li>Loop：遍历命令项列表</li>
     *   <li>Cmd：提取每项的 idList 并去重合并</li>
     *   <li>Gate：若最终列表为空则抛出业务异常</li>
     * </ul>
     *
     * @param items       批量命令项列表
     * @param emptyMessage ID 列表为空时的异常消息
     * @return 去重后的 ID 列表
     * @throws BusinessException ID 列表为空时
     */
    private <T> List<Long> collectIdList(List<T> items, String emptyMessage) {
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // Loop：遍历命令项，提取所有 ID。
        for (T item : items) {
            List<Long> idList;
            // 业务分支处理：根据命令类型提取 idList。
            if (item instanceof RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand) {
                idList = ((RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand) item).getIdList();
            } else if (item instanceof ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand) {
                idList = ((ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand) item).getIdList();
            } else {
                idList = null;
            }
            // 业务分支处理：idList 为空时跳过当前项。
            if (idList == null || idList.isEmpty()) {
                continue;
            }
            // Loop：遍历当前项的 ID 列表，去重合并。
            for (Long id : idList) {
                // 业务分支处理：ID 为空的跳过。
                if (id == null) {
                    continue;
                }
                // 业务分支处理：未出现过才加入结果列表。
                if (!seen.contains(id)) {
                    seen.add(id);
                    ids.add(id);
                }
            }
        }

        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, emptyMessage);
        }
        return ids;
    }

    /**
     * 私有方法：snapshotNotDeletedChildren。
     *
     * <p>生成未删除子项的快照：合并现存、对齐待保存的子项，移除已删除的，过滤软删标记。</p>
     * <ul>
     *   <li>Pre：按 childrenId 合并现存和对齐的子项</li>
     *   <li>Cmd：移除已标记删除的子项</li>
     *   <li>Asm：过滤掉已软删的子项，返回最终快照</li>
     * </ul>
     *
     * @param existed       现存子项列表
     * @param alignedToSave 对齐后待保存的子项列表
     * @param deleted       已标记删除的子项列表
     * @return 未删除子项的快照列表
     */
    private List<ActivityDefinitionChild> snapshotNotDeletedChildren(
        List<ActivityDefinitionChild> existed,
        List<ActivityDefinitionChild> alignedToSave,
        List<ActivityDefinitionChild> deleted) {
        Map<Long, ActivityDefinitionChild> byChildrenId = new HashMap<>();

        // Pre：合并现存子项到索引（排除空 childrenId）。
        if (existed != null) {
            for (ActivityDefinitionChild child : existed) {
                if (child.getChildrenId() != null) {
                    byChildrenId.put(child.getChildrenId(), child);
                }
            }
        }

        // Pre：合并对齐后待保存的子项到索引（覆盖现存）。
        if (alignedToSave != null) {
            for (ActivityDefinitionChild child : alignedToSave) {
                if (child.getChildrenId() != null) {
                    byChildrenId.put(child.getChildrenId(), child);
                }
            }
        }

        if (deleted != null) {
            for (ActivityDefinitionChild child : deleted) {
                if (child.getChildrenId() != null) {
                    byChildrenId.remove(child.getChildrenId());
                }
            }
        }

        // Asm：过滤掉已软删的子项，构建最终快照。
        List<ActivityDefinitionChild> snapshot = new ArrayList<>();
        for (ActivityDefinitionChild child : byChildrenId.values()) {
            // 业务分支处理：跳过已软删的子项。
            if (child.getIsDeleted() != null && child.getIsDeleted()) {
                continue;
            }
            snapshot.add(child);
        }
        return snapshot;
    }

    /**
     * 私有方法：findRowByBusNo。
     *
     * <p>从导入行列表中按业务编码查找匹配的行。</p>
     *
     * @param rows 导入行列表
     * @param busNo 业务编码
     * @return 匹配的行，未找到返回 null
     */
    private ImportActivityCatalogCommand.ActivityCatalogRowCommand findRowByBusNo(
        List<ImportActivityCatalogCommand.ActivityCatalogRowCommand> rows,
        String busNo) {
        // 业务分支处理：空列表直接返回 null。
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        // Loop：遍历列表查找匹配的业务编码。
        for (ImportActivityCatalogCommand.ActivityCatalogRowCommand row : rows) {
            // 业务分支处理：空行跳过。
            if (row == null) {
                continue;
            }
            // 业务分支处理：业务编码匹配则返回当前行。
            if (StringUtils.equals(StringUtils.trim(row.getBusNo()), busNo)) {
                return row;
            }
        }
        return null;
    }
}
