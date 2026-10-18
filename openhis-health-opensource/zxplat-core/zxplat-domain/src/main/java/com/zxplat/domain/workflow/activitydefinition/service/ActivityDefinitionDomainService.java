/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.service;

import java.util.Objects;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BuiltInActivityDefinitionConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.ActivityPermittedGender;
import com.zxplat.domain.share.events.workflow.activitydefinition.ActivityDefinitionActivatedEvent;
import com.zxplat.domain.share.events.workflow.activitydefinition.ActivityDefinitionCreatedEvent;
import com.zxplat.domain.share.events.workflow.activitydefinition.ActivityDefinitionPriceUpdatedEvent;
import com.zxplat.domain.share.events.workflow.activitydefinition.ActivityDefinitionRetiredEvent;
import com.zxplat.domain.share.events.workflow.activitydefinition.ActivityDefinitionUpdatedEvent;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.domain.workflow.activitydefinition.command.ActivateActivityDefinitionsCommand;
import com.zxplat.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.command.ImportActivityCatalogCommand;
import com.zxplat.domain.workflow.activitydefinition.command.ImportActivityCatalogResult;
import com.zxplat.domain.workflow.activitydefinition.command.DeleteActivityDefinitionsCommand;
import com.zxplat.domain.workflow.activitydefinition.command.RetireActivityDefinitionsCommand;
import com.zxplat.domain.workflow.activitydefinition.command.SyncActivityYbMappingCommand;
import com.zxplat.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinitionChild;
import com.zxplat.domain.workflow.activitydefinition.repository.ActivityDefinitionChildRepository;
import com.zxplat.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import com.zxplat.framework.core.application.util.StringUtils;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;
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
 *
 * <p>产品内置项（{@link BuiltInActivityDefinitionConstants}）：禁止删除/停用、改名、改 {@code type_enum}/类目。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityDefinitionDomainService {

    private final ActivityDefinitionRepository activityDefinitionRepository;
    private final ActivityDefinitionChildRepository activityDefinitionChildRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 诊疗项目定义 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#ACTIVITY_DEFINITION}，共 10 位。 */
    private final BusinessSequenceService businessSequenceService;

    /** 采番撞号时单条最大重试次数（序号落后或并发占用）。 */
    private static final int BUS_NO_ALLOCATE_MAX_ATTEMPTS = 32;

    /**
     * 新建诊疗项目。
     *
     * @param command 命令（支持 item/items）
     * @return 新建后的主键（当批量输入时返回首条保存记录的主键）
     */
    public Long createActivityDefinition(CreateActivityDefinitionCommand command) {
        List<CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand> items = command.resolveItems();

        List<ActivityDefinition> toSave = new ArrayList<>();
        // 用 busNo 关联子项，避免实体 equals/hashCode 随主键赋值变化导致 HashMap 丢映射
        Map<String, List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand>> childrenByBusNo = new HashMap<>();

        for (CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand item : items) {
            // Gate：产品内置名已存在则禁止再建（按名解析须唯一）
            this.assertCreateBuiltInNameAllowed(item.getName());

            ActivityDefinition definition = new ActivityDefinition();

            // 业务编码由领域层采番，禁止外部注入（B.10.9）；冲突时自动跳号。
            definition.setBusNo(this.allocateUniqueBusNo());

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
            definition.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
            definition.setChildrenJson(item.getChildrenJson());
            // 适用性别：命令未传时落 BOTH，保证非空列与体检过滤语义一致
            if (StringUtils.isNotEmpty(item.getPermittedGenderEnum())) {
                definition.setPermittedGenderEnum(item.getPermittedGenderEnum().trim());
            } else {
                definition.setPermittedGenderEnum(ActivityPermittedGender.BOTH.getCode());
            }
            definition.setSortOrder(item.getSortOrder());

            // 前置校验：必填与业务编码存在性。
            definition.assertRequiredFieldsProvided();
            definition.assertBusNoGenerated();
            definition.rebuildSearchCodes();

            toSave.add(definition);

            // 子项写入：创建阶段允许为空；若提供则按提供集合后续落库（需先获得聚合根主键）。
            childrenByBusNo.put(definition.getBusNo(), item.getChildren());
        }

        // 先落库，再发布事件，避免“幽灵事件”。
        List<ActivityDefinition> saved = activityDefinitionRepository.saveAll(toSave);
        List<ActivityDefinitionChild> childrenToSave = new ArrayList<>();
        for (ActivityDefinition definition : saved) {
            List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> childItems =
                childrenByBusNo.get(definition.getBusNo());
            List<ActivityDefinitionChild> builtChildren = this.buildChildren(definition, childItems);
            childrenToSave.addAll(builtChildren);
        }
        // 存在待保存子项时才批量落库，避免空 saveAll。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }
        // buildChildren 已对齐 childrenFlag，须再次落主表，否则首次 INSERT 仍是未绑定
        activityDefinitionRepository.saveAll(saved);
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
            // 维护命令必须携带主键，否则无法定位要更新的诊疗项目。
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "维护诊疗项目时 id 不能为空");
            }

            ActivityDefinition definition = this.loadActivityDefinition(item.getId());
            // Gate：产品内置项禁止改名/改类型/改类目；非内置禁止抢占内置名
            this.assertBuiltInIdentityUpdateAllowed(
                definition, item.getName(), item.getCategoryCode(), item.getTypeEnum());
            List<String> changedFields = new ArrayList<>();

            // 逐字段更新：命令字段为空时不覆盖历史值（与“可编辑字段集合”语义一致）。
            if (item.getName() != null) {
                // 项目名称变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getName(), item.getName())) {
                    changedFields.add(FieldNameConstants.NAME);
                }
                definition.setName(item.getName());
                definition.rebuildSearchCodes();
                changedFields.add(FieldNameConstants.PY_STR);
                changedFields.add(FieldNameConstants.WB_STR);
            }
            // 命令携带分类编码时才更新，空值表示不覆盖历史值。
            if (item.getCategoryCode() != null) {
                // 分类编码变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getCategoryCode(), item.getCategoryCode())) {
                    changedFields.add(FieldNameConstants.CATEGORY_CODE);
                }
                definition.setCategoryCode(item.getCategoryCode());
            }
            // 命令携带包装单位时才更新，空值表示不覆盖历史值。
            if (item.getPackageUnitCode() != null) {
                // 包装单位变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getPackageUnitCode(), item.getPackageUnitCode())) {
                    changedFields.add(FieldNameConstants.PACKAGE_UNIT_CODE);
                }
                definition.setPackageUnitCode(item.getPackageUnitCode());
            }
            if (item.getActivityVersion() != null) {
                if (!Objects.equals(definition.getActivityVersion(), item.getActivityVersion())) {
                    changedFields.add(FieldNameConstants.ACTIVITY_VERSION);
                }
                definition.setActivityVersion(item.getActivityVersion());
            }
            if (item.getMerchandiseName() != null) {
                if (!Objects.equals(definition.getMerchandiseName(), item.getMerchandiseName())) {
                    changedFields.add(FieldNameConstants.MERCHANDISE_NAME);
                    changedFields.add(FieldNameConstants.MERCHANDISE_PY_STR);
                    changedFields.add(FieldNameConstants.MERCHANDISE_WB_STR);
                    definition.setMerchandiseName(item.getMerchandiseName());
                    definition.rebuildMerchandiseSearchCodes();
                }
            }
            if (item.getDefEncounterUnitCode() != null) {
                if (!Objects.equals(definition.getDefEncounterUnitCode(), item.getDefEncounterUnitCode())) {
                    changedFields.add(FieldNameConstants.DEF_ENCOUNTER_UNIT_CODE);
                }
                definition.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
            }
            if (item.getDefInhospitalUnitCode() != null) {
                if (!Objects.equals(definition.getDefInhospitalUnitCode(), item.getDefInhospitalUnitCode())) {
                    changedFields.add(FieldNameConstants.DEF_INHOSPITAL_UNIT_CODE);
                }
                definition.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
            }
            // 命令携带项目类型时才更新，空值表示不覆盖历史值。
            if (item.getTypeEnum() != null) {
                // 项目类型变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getTypeEnum() == null || !definition.getTypeEnum().equals(item.getTypeEnum())) {
                    changedFields.add(FieldNameConstants.TYPE_ENUM);
                }
                definition.setTypeEnum(item.getTypeEnum());
            }
            // 命令携带说明文本时才更新，空值表示不覆盖历史值。
            if (item.getDescriptionText() != null) {
                // 说明文本变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getDescriptionText(), item.getDescriptionText())) {
                    changedFields.add(FieldNameConstants.DESCRIPTION_TEXT);
                }
                definition.setDescriptionText(item.getDescriptionText());
            }
            // 命令携带部位编码时才更新，空值表示不覆盖历史值。
            if (item.getBodySiteCode() != null) {
                // 部位编码变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getBodySiteCode(), item.getBodySiteCode())) {
                    changedFields.add(FieldNameConstants.BODY_SITE_CODE);
                }
                definition.setBodySiteCode(item.getBodySiteCode());
            }
            // 命令携带标本编码时才更新，空值表示不覆盖历史值。
            if (item.getSpecimenCode() != null) {
                // 标本编码变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getSpecimenCode(), item.getSpecimenCode())) {
                    changedFields.add(FieldNameConstants.SPECIMEN_CODE);
                }
                definition.setSpecimenCode(item.getSpecimenCode());
            }
            // 命令携带归属机构时才更新，空值表示不覆盖历史值。
            if (item.getOrgId() != null) {
                // 归属机构变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getOrgId() == null || !definition.getOrgId().equals(item.getOrgId())) {
                    changedFields.add(FieldNameConstants.ORG_ID);
                }
                definition.setOrgId(item.getOrgId());
            }
            // 命令携带执行地点时才更新，空值表示不覆盖历史值。
            if (item.getLocationId() != null) {
                // 执行地点变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getLocationId() == null || !definition.getLocationId().equals(item.getLocationId())) {
                    changedFields.add(FieldNameConstants.LOCATION_ID);
                }
                definition.setLocationId(item.getLocationId());
            }
            // 命令携带计费规则时才更新，空值表示不覆盖历史值。
            if (item.getRuleId() != null) {
                // 计费规则变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getRuleId() == null || !definition.getRuleId().equals(item.getRuleId())) {
                    changedFields.add(FieldNameConstants.RULE_ID);
                }
                definition.setRuleId(item.getRuleId());
            }
            // 命令携带计价标志时才更新，空值表示不覆盖历史值。
            if (item.getPricingFlag() != null) {
                // 计价标志变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getPricingFlag() == null || !definition.getPricingFlag().equals(item.getPricingFlag())) {
                    changedFields.add(FieldNameConstants.PRICING_FLAG);
                }
                definition.setPricingFlag(item.getPricingFlag());
            }
            // 命令携带医保标志时才更新，空值表示不覆盖历史值。
            if (item.getYbFlag() != null) {
                // 医保标志变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getYbFlag() == null || !definition.getYbFlag().equals(item.getYbFlag())) {
                    changedFields.add(FieldNameConstants.YB_FLAG);
                }
                definition.setYbFlag(item.getYbFlag());
            }
            // 命令携带医保编码时才更新，空值表示不覆盖历史值。
            if (item.getYbNo() != null) {
                // 医保编码变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getYbNo(), item.getYbNo())) {
                    changedFields.add(FieldNameConstants.YB_NO);
                }
                definition.setYbNo(item.getYbNo());
            }
            // 命令携带医保对码标志时才更新，空值表示不覆盖历史值。
            if (item.getYbMatchFlag() != null) {
                // 医保对码标志变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getYbMatchFlag() == null || !definition.getYbMatchFlag().equals(item.getYbMatchFlag())) {
                    changedFields.add(FieldNameConstants.YB_MATCH_FLAG);
                }
                definition.setYbMatchFlag(item.getYbMatchFlag());
            }
            // 命令携带收费项目等级时才更新，空值表示不覆盖历史值。
            if (item.getChrgitmLv() != null) {
                // 收费项目等级变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (definition.getChrgitmLv() == null || !definition.getChrgitmLv().equals(item.getChrgitmLv())) {
                    changedFields.add(FieldNameConstants.CHRGITM_LV);
                }
                definition.setChrgitmLv(item.getChrgitmLv());
            }
            // 命令携带病案结算类型时才更新，空值表示不覆盖历史值。
            if (item.getMrSettlementTypeCode() != null) {
                if (!Objects.equals(definition.getMrSettlementTypeCode(), item.getMrSettlementTypeCode())) {
                    changedFields.add(FieldNameConstants.MR_SETTLEMENT_TYPE_CODE);
                }
                definition.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
            }
            // 命令携带子项 JSON 时才更新，空值表示不覆盖历史值。
            if (item.getChildrenJson() != null) {
                // 子项 JSON 变更时记入 changedFields，供 UpdatedEvent 增量同步读模型。
                if (!Objects.equals(definition.getChildrenJson(), item.getChildrenJson())) {
                    changedFields.add(FieldNameConstants.CHILDREN_JSON);
                }
                definition.setChildrenJson(item.getChildrenJson());
            }
            // 命令携带适用性别时才更新，空值表示不覆盖历史值。
            if (item.getPermittedGenderEnum() != null) {
                String genderCode = item.getPermittedGenderEnum().trim();
                if (!Objects.equals(definition.getPermittedGenderEnum(), genderCode)) {
                    changedFields.add(FieldNameConstants.PERMITTED_GENDER_ENUM);
                }
                definition.setPermittedGenderEnum(genderCode);
            }
            // 命令携带展示顺序时才更新（含显式置空语义：传 Integer 即覆盖）。
            if (item.getSortOrder() != null) {
                if (!Objects.equals(definition.getSortOrder(), item.getSortOrder())) {
                    changedFields.add(FieldNameConstants.SORT_ORDER);
                }
                definition.setSortOrder(item.getSortOrder());
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
                // 子项集合变更导致 childrenFlag 变化时，记入 changedFields 通知下游。
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
        // 存在待保存子项时才批量落库，避免空 saveAll。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }
        // 存在待逻辑删除子项时才批量落库，完成全量对齐。
        if (!childrenToDelete.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToDelete);
        }

        activityDefinitionRepository.flush();

        for (ActivityDefinition definition : toSave) {
            List<String> changedFields = changedFieldsById.get(definition.getId());
            // 无字段差分时仍发布 UpdatedEvent，changedFields 用空列表兜底。
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
        // 批量停用须全部主键有效，任一缺失则拒绝整批操作。
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "批量停用诊疗项目时存在无效主键");
        }

        for (ActivityDefinition definition : definitions) {
            // Gate：产品内置诊疗项目禁止停用
            this.assertBuiltInNotRemovable(definition, "停用");
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
            // Gate：产品内置诊疗项目禁止删除
            this.assertBuiltInNotRemovable(definition, "删除");
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
        // 批量启用须全部主键有效，任一缺失则拒绝整批操作。
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
        // 目录导入按单次动作约束，一次仅允许一组 rows。
        if (items.size() != 1) {
            // DOC-CONFLICT: SDD 约定 import 为单次导入动作，但 BatchCommand 默认允许批量；此处按单次导入约束收敛。
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "批量导入诊疗目录一次仅允许提交一组导入 rows");
        }

        ImportActivityCatalogCommand.ImportActivityCatalogItemCommand batch = items.get(0);
        // 导入 rows 为空则无有效数据，直接拒绝。
        if (batch.getRows() == null || batch.getRows().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入诊疗目录 rows 不能为空");
        }

        // 批量入参内存去重：以 busNo 为业务唯一键。
        Map<String, Integer> seenBusNo = new HashMap<>();
        List<String> busNos = new ArrayList<>();
        for (ImportActivityCatalogCommand.ActivityCatalogRowCommand row : batch.getRows()) {
            // busNo 为目录唯一键，导入行缺失则无法 upsert。
            if (StringUtils.isEmpty(row.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入诊疗目录时 busNo 不能为空");
            }
            String normalized = row.getBusNo().trim();
            Integer existedLine = seenBusNo.get(normalized);
            // 同一导入批次内 busNo 重复会导致覆盖歧义，按首次行号报错。
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

                // busNo 未命中存量记录时走新建路径，由领域层生成业务编码。
                if (target == null) {
                    // Gate：产品内置名已存在则禁止再建
                    this.assertCreateBuiltInNameAllowed(row.getName());

                    ActivityDefinition created = new ActivityDefinition();
                    created.setBusNo(this.allocateUniqueBusNo());
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
                    // Gate：产品内置项禁止改名/改类型/改类目；导入同口径
                    this.assertBuiltInIdentityUpdateAllowed(
                        updated, row.getName(), row.getCategoryCode(), row.getTypeEnum());
                    updated.setCategoryCode(row.getCategoryCode());
                    updated.setName(row.getName());
                    updated.setPackageUnitCode(row.getPackageUnitCode());
                    updated.setActivityVersion(row.getActivityVersion());
                    updated.setMerchandiseName(row.getMerchandiseName());
                    updated.setDefEncounterUnitCode(row.getDefEncounterUnitCode());
                    updated.setDefInhospitalUnitCode(row.getDefInhospitalUnitCode());
                    updated.rebuildMerchandiseSearchCodes();
                    updated.setTypeEnum(row.getTypeEnum());
                    // 导入行显式携带状态时才覆盖，否则保留存量发布状态。
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
            // 新建主表记录需补齐子项，仅对本次导入新增的行构建子项。
            if (existedEntity == null) {
                ImportActivityCatalogCommand.ActivityCatalogRowCommand row = this.findRowByBusNo(batch.getRows(), definition.getBusNo());
                // 匹配到导入行则按行内子项清单构建并待落库。
                if (row != null) {
                    childrenToSave.addAll(this.buildChildrenFromImportRow(definition, row.getChildren()));
                }
            }
        }

        // 存在待保存子项时才批量落库，避免空 saveAll。
        if (!childrenToSave.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToSave);
        }
        // 存在待逻辑删除子项时才批量落库，完成全量对齐。
        if (!childrenToDelete.isEmpty()) {
            activityDefinitionChildRepository.saveAll(childrenToDelete);
        }
        // 导入新建/更新都可能改子表，按落库后的有效子项重对齐 childrenFlag 再写主表
        this.realignChildrenFlagAfterImport(saved);

        // 发布事件：先确保主表主键可用，再逐行发布。
        for (ActivityDefinition definition : saved) {
            ActivityDefinition existedEntity = existedByBusNo.get(definition.getBusNo());
            // 新建记录发布 CreatedEvent，供目录读模型增量接入。
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
        // 医保对码回写至少须一条 activityId，空列表无法定位目标。
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 不能为空");
        }

        List<ActivityDefinition> definitions = activityDefinitionRepository.findByIdIn(ids);
        // 医保对码回写须全部 activityId 有效，任一缺失则拒绝同步。
        if (definitions.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "同步医保对码回写时存在无效主键");
        }

        Map<Long, SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand> byId = new HashMap<>();
        for (SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item : items) {
            // 每条对码回写须携带 activityId，否则无法写入对应诊疗项目。
            if (item.getActivityId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 不能为空");
            }
            // 同一 activityId 重复提交会导致后写覆盖前写，整批拒绝。
            if (byId.containsKey(item.getActivityId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同步医保对码回写时 activityId 重复");
            }
            byId.put(item.getActivityId(), item);
        }

        for (ActivityDefinition definition : definitions) {
            SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item = byId.get(definition.getId());
            // 命令项与已加载聚合无法匹配，说明入参 id 与 items 不一致。
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
     * 新建门禁：产品内置名称若已存在未删行，禁止再建（按名解析须唯一）。
     *
     * @param name 拟创建名称
     */
    private void assertCreateBuiltInNameAllowed(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null || !BuiltInActivityDefinitionConstants.isBuiltInName(trimmed)) {
            return;
        }
        // 内置名已落库则拒绝再建，避免「名称不唯一」拖垮皮试/嘱托等链路
        if (activityDefinitionRepository.existsByNameAndIsDeletedFalse(trimmed)) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "系统内置诊疗项目「" + trimmed + "」已存在，禁止重复创建");
        }
    }

    /**
     * 维护/导入门禁：内置项禁止改名、改类型、改类目；非内置禁止改名为内置保留名。
     *
     * @param existing         当前聚合根
     * @param nextName         命令拟写名称（null 表示不改）
     * @param nextCategoryCode 命令拟写类目（null 表示不改）
     * @param nextTypeEnum     命令拟写类型（null 表示不改）
     */
    private void assertBuiltInIdentityUpdateAllowed(
        ActivityDefinition existing,
        String nextName,
        String nextCategoryCode,
        String nextTypeEnum) {
        String currentName = trimToNull(existing.getName());
        String trimmedNextName = trimToNull(nextName);

        if (!BuiltInActivityDefinitionConstants.isBuiltInName(currentName)) {
            // 非内置：禁止抢占内置保留名
            if (trimmedNextName != null
                && BuiltInActivityDefinitionConstants.isBuiltInName(trimmedNextName)
                && !trimmedNextName.equals(currentName)) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "「" + trimmedNextName + "」为系统内置诊疗项目名称，禁止占用");
            }
            return;
        }

        // 内置：名称不可变
        if (trimmedNextName != null && !trimmedNextName.equals(currentName)) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "系统内置诊疗项目「" + currentName + "」禁止改名");
        }
        // 内置：类型不可变
        if (nextTypeEnum != null && !Objects.equals(existing.getTypeEnum(), nextTypeEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "系统内置诊疗项目「" + currentName + "」禁止修改类型");
        }
        // 内置：类目不可变（嘱托 category=52 等与写死链路绑定）
        if (nextCategoryCode != null && !Objects.equals(existing.getCategoryCode(), nextCategoryCode)) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "系统内置诊疗项目「" + currentName + "」禁止修改类目");
        }
    }

    /**
     * 删除/停用门禁：产品内置诊疗项目不可移除。
     *
     * @param definition 待删或待停用聚合根
     * @param actionLabel 动作文案（删除/停用）
     */
    private void assertBuiltInNotRemovable(ActivityDefinition definition, String actionLabel) {
        String name = trimToNull(definition.getName());
        if (!BuiltInActivityDefinitionConstants.isBuiltInName(name)) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
            "系统内置诊疗项目「" + name + "」禁止" + actionLabel);
    }

    /**
     * 采番一条未被占用的诊疗业务编码；冲突则继续取下一号。
     *
     * @return 未占用的业务编码
     */
    private String allocateUniqueBusNo() {
        for (int attempt = 0; attempt < BUS_NO_ALLOCATE_MAX_ATTEMPTS; attempt++) {
            String busNo = this.generateBusNo();
            if (!activityDefinitionRepository.existsByBusNo(busNo)) {
                return busNo;
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目业务编码采番冲突，请稍后重试");
    }

    /**
     * 私有方法：generateBusNo。
     *
     * <p>诊疗项目业务编码生成：使用 {@link AggregateTypeConstants#ACTIVITY_DEFINITION} 前缀获取序列号。</p>
     *
     * @return 生成的业务编码
     */
    private String generateBusNo() {
        return businessSequenceService.getSeq(
            AggregateTypeConstants.ACTIVITY_DEFINITION, BusinessSequenceConstants.CATALOG_DEFINITION_BUS_NO_SEQ_LENGTH);
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
        if (StringUtils.isEmpty(statusEnum)) {
            return PublicationStatus.DRAFT.getCode();
        }
        return statusEnum;
    }

    /**
     * 套餐层级门禁（只允许一层父→子）。
     *
     * <ul>
     *   <li><strong>允许</strong>：同一明细子项被多个父套餐引用（子项可有多个父亲）</li>
     *   <li><strong>禁止</strong>：父套餐再被其它套餐引用为子项（父项不能再有父亲）—
     *       即「已是别人子项」的诊疗禁止再挂子项；「已是套餐」的诊疗禁止再被挂为子项</li>
     *   <li>子项不得引用自身</li>
     * </ul>
     *
     * @param parentActivityId 父诊疗主键；新建落库前可为空（跳过「父已是子项」校验）
     * @param childrenIds      拟挂载的子项主键集合
     */
    private void assertNoPackageNesting(Long parentActivityId, Set<Long> childrenIds) {
        if (childrenIds == null || childrenIds.isEmpty()) {
            return;
        }
        List<Long> childIdList = new ArrayList<>();
        for (Long childId : childrenIds) {
            if (childId == null) {
                continue;
            }
            // 自引用会造成环，一律拒绝
            if (parentActivityId != null && parentActivityId.equals(childId)) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "子项不能引用自身");
            }
            childIdList.add(childId);
        }
        if (childIdList.isEmpty()) {
            return;
        }

        // 父项已是其它套餐的子项 → 禁止再挂子项（父项不能再有父亲）
        if (parentActivityId != null) {
            List<ActivityDefinitionChild> parentAsChildRows =
                activityDefinitionChildRepository.findByChildrenIdIn(List.of(parentActivityId));
            if (parentAsChildRows != null && !parentAsChildRows.isEmpty()) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "该诊疗已是其它套餐的子项，禁止再维护子项（父项不能再有父亲）");
            }
        }

        // 拟挂子项自身仍有子表行 → 它已是套餐，不能再当别人的子项（父项不能再有父亲）
        // 注意：不校验「子项是否已被其它父套餐引用」——同一明细可挂多个父亲
        List<ActivityDefinitionChild> nestedChildRows =
            activityDefinitionChildRepository.findByActivityIdIn(childIdList);
        if (nestedChildRows != null && !nestedChildRows.isEmpty()) {
            Set<Long> nestedPackageIds = new HashSet<>();
            for (ActivityDefinitionChild row : nestedChildRows) {
                if (row.getActivityId() != null) {
                    nestedPackageIds.add(row.getActivityId());
                }
            }
            String nestedName = this.resolveFirstActivityName(nestedPackageIds);
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "子项「" + nestedName + "」已是套餐，不能再被其它套餐引用（父项不能再有父亲）");
        }

        // 双保险：childrenFlag=true 但子表暂空的脏数据也拦
        List<ActivityDefinition> childDefs = activityDefinitionRepository.findByIdIn(childIdList);
        for (ActivityDefinition childDef : childDefs) {
            if (Boolean.TRUE.equals(childDef.getChildrenFlag())) {
                String name = childDef.getName();
                if (StringUtils.isEmpty(name)) {
                    name = String.valueOf(childDef.getId());
                }
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                    "子项「" + name + "」已是套餐，不能再被其它套餐引用（父项不能再有父亲）");
            }
        }
    }

    /**
     * 取集合中首个诊疗名称，便于嵌套校验报错文案；找不到时回落主键。
     *
     * @param activityIds 诊疗主键集合
     * @return 展示名
     */
    private String resolveFirstActivityName(Set<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return "";
        }
        List<ActivityDefinition> defs = activityDefinitionRepository.findByIdIn(new ArrayList<>(activityIds));
        if (defs != null) {
            for (ActivityDefinition def : defs) {
                if (def != null && StringUtils.isNotEmpty(def.getName())) {
                    return def.getName();
                }
            }
        }
        return String.valueOf(activityIds.iterator().next());
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
        // 子项列表为空时标记父项无子项并返回空列表。
        if (children == null || children.isEmpty()) {
            definition.setChildrenFlag(Boolean.FALSE);
            return built;
        }

        // 同一父项下 childrenId 不重复（BR-ACTDEF-007）。
        Set<Long> seenChildrenId = new HashSet<>();
        for (CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : children) {
            // 子项 childrenId 为空则无法关联子项目，拒绝落库。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项 childrenId 不能为空");
            }
            // 同一父项下 childrenId 重复会导致数量歧义，拒绝。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同一父项下子项 childrenId 不允许重复");
            }
            seenChildrenId.add(item.getChildrenId());
        }
        // 禁止套餐嵌套：只允许一层父→子
        this.assertNoPackageNesting(definition.getId(), seenChildrenId);

        for (CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : children) {
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
        // 导入子项为空时标记父项无子项并返回空列表。
        if (children == null || children.isEmpty()) {
            definition.setChildrenFlag(Boolean.FALSE);
            return built;
        }

        Set<Long> seenChildrenId = new HashSet<>();
        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : children) {
            // 导入子项缺少 childrenId 无法关联子项目，拒绝落库。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 不能为空");
            }
            // 同一父项下 childrenId 重复会导致数量歧义，拒绝。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 重复");
            }
            seenChildrenId.add(item.getChildrenId());
        }
        // 禁止套餐嵌套：只允许一层父→子
        this.assertNoPackageNesting(definition.getId(), seenChildrenId);

        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : children) {
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
            // 子项 childrenId 为空则无法关联子项目，拒绝落库。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项 childrenId 不能为空");
            }
            // 同一父项下 childrenId 重复会导致数量歧义，拒绝。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "同一父项下子项 childrenId 不允许重复");
            }
            seenChildrenId.add(item.getChildrenId());
        }
        // 禁止套餐嵌套：只允许一层父→子（清空子项时不做嵌套校验）
        if (!seenChildrenId.isEmpty()) {
            this.assertNoPackageNesting(definition.getId(), seenChildrenId);
        }

        for (UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            ActivityDefinitionChild target = existedByChildrenId.get(item.getChildrenId());
            // 子项 childrenId 未命中存量行则新建关联行。
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
            // 仅收集非空 childrenId 作为全量对齐保留集。
            if (item.getChildrenId() != null) {
                keepChildrenId.add(item.getChildrenId());
            }
        }

        List<ActivityDefinitionChild> toDelete = new ArrayList<>();
        for (ActivityDefinitionChild child : existed) {
            // 存量子项 childrenId 为空的数据异常行跳过，不参与对齐。
            if (child.getChildrenId() == null) {
                continue;
            }
            // 输入未包含的存量子项视为删除，标记软删。
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

        // 导入对齐时未传子项则无需处理，返回空列表。
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
            // 导入子项缺少 childrenId 无法关联子项目，拒绝落库。
            if (item.getChildrenId() == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 不能为空");
            }
            // 同一父项下导入子项 childrenId 重复会导致数量歧义，拒绝。
            if (seenChildrenId.contains(item.getChildrenId())) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "导入子项 childrenId 重复");
            }
            seenChildrenId.add(item.getChildrenId());
        }
        // 禁止套餐嵌套：只允许一层父→子（清空子项时不做嵌套校验）
        if (!seenChildrenId.isEmpty()) {
            this.assertNoPackageNesting(definition.getId(), seenChildrenId);
        }

        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            ActivityDefinitionChild target = existedByChildrenId.get(item.getChildrenId());
            // 子项 childrenId 未命中存量行则新建关联行。
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
        // 未传子项列表时不做删除对齐，直接返回。
        if (inputChildren == null) {
            return toDelete;
        }

        // Pre：收集所有需要保留的 childrenId。
        Set<Long> keepChildrenId = new HashSet<>();
        for (ImportActivityCatalogCommand.ActivityDefinitionChildItemCommand item : inputChildren) {
            // 仅收集非空 childrenId 作为全量对齐保留集。
            if (item.getChildrenId() != null) {
                keepChildrenId.add(item.getChildrenId());
            }
        }

        for (ActivityDefinitionChild child : existed) {
            // 存量子项 childrenId 为空的数据异常行跳过，不参与对齐。
            if (child.getChildrenId() == null) {
                continue;
            }
            // 输入未包含的存量子项视为删除，标记软删。
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
     * <p>从批量命令项中提取并去重 ID 列表。支持停用/启用/删除命令的类型。</p>
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
            // 按命令类型提取批量 idList（停用/启用/删除路径结构不同）。
            if (item instanceof RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand) {
                idList = ((RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand) item).getIdList();
            } else if (item instanceof ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand) {
                idList = ((ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand) item).getIdList();
            } else if (item instanceof DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand) {
                idList = ((DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand) item).getIdList();
            } else {
                idList = null;
            }
            // 当前命令项未携带 idList 时跳过，继续合并其它项。
            if (idList == null || idList.isEmpty()) {
                continue;
            }
            // Loop：遍历当前项的 ID 列表，去重合并。
            for (Long id : idList) {
                // 空 id 跳过，避免无效主键进入批量操作。
                if (id == null) {
                    continue;
                }
                // 跨命令项 id 去重，同一主键只处理一次。
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
            // 快照排除已软删子项，childrenFlag 仅反映有效子项。
            if (child.getIsDeleted() != null && child.getIsDeleted()) {
                continue;
            }
            snapshot.add(child);
        }
        return snapshot;
    }

    /**
     * 导入落子表后，按有效子项重对齐主表 {@code childrenFlag} 并写回。
     *
     * <p>导入新建路径原先只在内存打标、未二次 save 主表；更新路径未调用 {@link ActivityDefinition#syncChildrenFlag}，
     * 导致列表「绑定子项」与子表事实不一致。</p>
     *
     * @param saved 本批次已保存的主表实体
     */
    private void realignChildrenFlagAfterImport(List<ActivityDefinition> saved) {
        if (saved == null || saved.isEmpty()) {
            return;
        }
        List<Long> savedIds = new ArrayList<>();
        for (ActivityDefinition definition : saved) {
            if (definition != null && definition.getId() != null) {
                savedIds.add(definition.getId());
            }
        }
        if (savedIds.isEmpty()) {
            return;
        }
        List<ActivityDefinitionChild> persistedChildren =
            activityDefinitionChildRepository.findByActivityIdIn(savedIds);
        Map<Long, List<ActivityDefinitionChild>> childrenByActivityId = new HashMap<>();
        for (ActivityDefinitionChild child : persistedChildren) {
            if (child == null || child.getActivityId() == null) {
                continue;
            }
            // 软删子项不计入绑定标记
            if (Boolean.TRUE.equals(child.getIsDeleted())) {
                continue;
            }
            List<ActivityDefinitionChild> bucket = childrenByActivityId.get(child.getActivityId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                childrenByActivityId.put(child.getActivityId(), bucket);
            }
            bucket.add(child);
        }
        for (ActivityDefinition definition : saved) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            List<ActivityDefinitionChild> children = childrenByActivityId.get(definition.getId());
            if (children == null) {
                children = new ArrayList<>();
            }
            definition.syncChildrenFlag(children);
        }
        activityDefinitionRepository.saveAll(saved);
        activityDefinitionRepository.flush();
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
        // 导入行列表为空时无法匹配 busNo，返回 null。
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        // Loop：遍历列表查找匹配的业务编码。
        for (ImportActivityCatalogCommand.ActivityCatalogRowCommand row : rows) {
            // 空行跳过，继续查找匹配 busNo。
            if (row == null) {
                continue;
            }
            // busNo 匹配则返回对应导入行，供新建后补子项。
            if (Objects.equals(trimToNull(row.getBusNo()), busNo)) {
                return row;
            }
        }
        return null;
    }
}
