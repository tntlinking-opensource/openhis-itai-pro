package com.openhis.health.application.basic.orderbinding.service;

import com.openhis.health.application.common.converter.TextValueConverter;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.application.basic.orderbinding.dto.OrderBindingDto;
import com.openhis.health.application.basic.orderbinding.dto.OrderBindingInitDto;
import com.openhis.health.application.basic.orderbinding.dto.UnitOptionsByTargetDto;
import com.openhis.health.application.basic.orderbinding.mapper.OrderBindingMapper;
import com.openhis.health.application.basic.orderbinding.request.DeleteOrderBindingRequest;
import com.openhis.health.application.basic.orderbinding.request.PageOrderBindingRequest;
import com.openhis.health.application.basic.orderbinding.request.SaveOrderBindingRequest;
import com.openhis.health.application.basic.orderbinding.request.UnitOptionsByTargetRequest;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.custom.servicedevicetemplate.command.DeleteBindingCommand;
import com.openhis.health.domain.custom.servicedevicetemplate.queries.model.ServiceDeviceTemplateQueryModel;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateDomainService;
import com.openhis.health.domain.custom.servicedevicetemplate.service.ServiceDeviceTemplateQueryService;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateRangeCode;
import com.openhis.health.domain.share.enums.ServiceDeviceTemplateTypeCode;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionEnabledQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基础数据「服务设备模板绑定」应用服务：维护号源/用法/诊疗来源项与设备或诊疗活动之间的数量、单位与状态，
 * 查询与落库委托服务设备模板领域服务。
 *
 * <p>绑定类型与来源表、领域 {@link ServiceDeviceTemplateTypeCode} 的映射由本类与
 * {@link com.openhis.health.application.basic.orderbinding.mapper.OrderBindingMapperImpl} 对称实现；写操作操作人当前固定为 0。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderAppService {

    private static final String ITEM_TABLE_USAGE = TableNameConstants.TER_CONCEPT;
    private static final String ITEM_TABLE_ACTIVITY = TableNameConstants.WKF_ACTIVITY_DEFINITION;
    private static final String ITEM_TABLE_SLOT = TableNameConstants.ADM_HEALTHCARE_SERVICE;
    private static final String TARGET_TABLE_ACTIVITY = TableNameConstants.WKF_ACTIVITY_DEFINITION;
    private static final String TARGET_TABLE_DEVICE = TableNameConstants.ADM_DEVICE_DEFINITION;
    private static final String UNIT_CODE_SYSTEM = TerminologyCodeSystemNameConstants.UNIT_CODE;

    /** 绑定 DTO 与领域命令映射。 */
    private final OrderBindingMapper orderBindingMapper;
    /** 服务设备模板读侧：分页与按来源查询绑定。 */
    private final ServiceDeviceTemplateQueryService serviceDeviceTemplateQueryService;
    /** 服务设备模板写侧：创建、编辑与删除绑定。 */
    private final ServiceDeviceTemplateDomainService serviceDeviceTemplateDomainService;
    /** 术语读侧：单位、用法等下拉与概念解析。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 诊疗活动定义读侧：来源/目标候选与按 id 查找。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 耗材定义读侧：设备目标与单位码收集。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    /** 医疗服务读侧：号源类绑定来源下拉。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /**
     * 返回绑定配置页初始化数据：状态、范围、绑定类型、单位、各来源候选及目标表选项。
     *
     * @return 初始化 DTO
     */
    public OrderBindingInitDto init() {
        OrderBindingInitDto dto = new OrderBindingInitDto();
        dto.setStatusOptions(List.of(
            new OptionDto(1L, "1", "启用"),
            new OptionDto(2L, "2", "停用")
        ));
        dto.setRangeOptions(List.of(
            new OptionDto(1L, ServiceDeviceTemplateRangeCode.HOSPITAL.getCode(), "全院")
        ));
        dto.setBindTypeOptions(List.of(
            new OptionDto(1L, "1", "号源"),
            new OptionDto(2L, "2", "用法"),
            new OptionDto(3L, "3", "诊疗")
        ));
        dto.setUnitOptions(codeSystemQueryService.getOptionListByCodeSystemName(UNIT_CODE_SYSTEM));
        dto.setUsageSourceOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        dto.setActivitySourceOptions(loadActivitySourceOptions());
        dto.setSourceSourceOptions(loadHealthcareServiceOptions());
        dto.setDeviceDefOptions(loadDeviceOptions());
        dto.setTargetTableOptions(List.of(
            new OptionDto(1L, TARGET_TABLE_ACTIVITY, "诊疗定义"),
            new OptionDto(2L, TARGET_TABLE_DEVICE, "耗材定义")
        ));
        return dto;
    }

    /**
     * 分页查询绑定列表：可按来源项精确过滤或仅按范围+类型浏览，状态过滤可选。
     *
     * <p>查询策略分支：带来源 id 时走按来源分页，否则按范围+类型浏览全部绑定。</p>
     *
     * @param request 分页条件；{@code null} 时由内部补默认 {@link PageOrderBindingRequest}
     * @return 分页绑定 DTO
     * @throws ValidationException 绑定类型为空或不合法时
     */
    public PagedResult<OrderBindingDto> pageBindings(PageOrderBindingRequest request) {
        // Step：空请求保护，补全默认值
        PageOrderBindingRequest query = ensurePageRequest(request);
        // Step：前端绑定类型转领域类型码（1/2/3 → slot/usage/activity）
        String typeCode = toDomainTypeCode(query.getBindType());
        // Gate：绑定类型为必选项，缺失时阻断查询
        if (typeCode == null) {
            throw new ValidationException("绑定类型不能为空");
        }
        // Step：范围码归一（空则默认全院）
        String rangeCode = normalizeRangeCode(query.getRangeCode());
        // Step：计算 Spring Data 0-based 页索引与页大小
        int pageIndex = 0;
        if (query.getPageNo() != null && query.getPageNo() > 0) {
            pageIndex = query.getPageNo() - 1;
        }
        int pageSize;
        if (query.getPageSize() == null || query.getPageSize() <= 0) {
            pageSize = 50;
        } else {
            pageSize = query.getPageSize();
        }
        // Step：前端状态筛选转领域状态码（1/2 → active/retired）
        String statusCode = toDomainStatus(query.getStatusEnum());
        Page<ServiceDeviceTemplateQueryModel> page;

        // Branch：按是否指定来源项，选择不同的查询路径
        if (query.getSourceId() != null && !query.getSourceId().isBlank()) {
            // Branch：带来源 id 时，先解析来源表名，再走按来源分页查询
            String itemTable = toSourceTable(query.getBindType());
            if (itemTable == null) {
                throw new ValidationException("绑定类型不合法");
            }
            // Branch：按是否存在状态过滤，调用对应的查询服务方法
            if (statusCode == null) {
                page = serviceDeviceTemplateQueryService.pageBindingsBySource(
                    rangeCode, typeCode, itemTable, query.getSourceId().trim(), PageRequest.of(pageIndex, pageSize));
            } else {
                page = serviceDeviceTemplateQueryService.pageBindingsBySourceAndStatus(
                    rangeCode, typeCode, itemTable, query.getSourceId().trim(), statusCode, PageRequest.of(pageIndex, pageSize));
            }
        } else {
            // Branch：未指定来源时，按范围+类型浏览全部绑定
            if (statusCode == null) {
                page = serviceDeviceTemplateQueryService.pageBindingsByRangeAndType(
                    rangeCode, typeCode, PageRequest.of(pageIndex, pageSize));
            } else {
                page = serviceDeviceTemplateQueryService.pageBindingsByRangeAndTypeAndStatus(
                    rangeCode, typeCode, statusCode, PageRequest.of(pageIndex, pageSize));
            }
        }
        return PagedResult.fromPage(page.map(orderBindingMapper::toDto));
    }

    /**
     * 保存绑定：无主键走创建，有主键走更新。
     *
     * @param request 保存请求
     * @return 新建时返回领域生成的主键；更新时返回原主键
     * @throws ValidationException 入参校验失败时
     */
    public Long saveBinding(SaveOrderBindingRequest request) {
        validateSaveRequest(request);
        // 无主键走创建，有主键走编辑，与前端同一保存按钮契约一致
        if (request.getId() == null) {
            return serviceDeviceTemplateDomainService.createBinding(orderBindingMapper.toCreateCommand(request));
        }
        serviceDeviceTemplateDomainService.editBinding(orderBindingMapper.toEditCommand(request));
        return request.getId();
    }

    /**
     * 删除单条绑定。
     *
     * @param request 含绑定 id 字符串的删除请求
     * @throws ValidationException id 缺失或非法时
     */
    public void deleteBinding(DeleteOrderBindingRequest request) {
        String deleteBindingIdValue = null;
        if (request != null) {
            deleteBindingIdValue = request.getValue();
        }
        Long id = TextValueConverter.requireLong(deleteBindingIdValue, "删除失败：绑定ID不能为空");
        DeleteBindingCommand command = new DeleteBindingCommand();
        command.setBindingId(id);
        command.setOperatorId(0L);
        serviceDeviceTemplateDomainService.deleteBinding(command);
    }

    /**
     * 按绑定目标（诊疗活动定义或设备定义）解析推荐单位码与可选单位下拉，供保存前单位控件联动。
     *
     * <p>单位推荐策略：诊疗活动取销售单位；设备取首个非空业务单位（采购/库存/销售/剂量/拆零）。</p>
     *
     * @param request 目标表名与目标 id
     * @return 默认单位码及选项列表
     * @throws ValidationException 目标表或目标 id 不合法时
     */
    public UnitOptionsByTargetDto unitOptionsByTarget(UnitOptionsByTargetRequest request) {
        // Gate：请求体与目标表非空校验
        if (request == null || request.getTargetTable() == null || request.getTargetTable().isBlank()) {
            throw new ValidationException("目标表不能为空");
        }
        // Step：解析目标 id 字符串为 Long（含格式校验）
        Long targetId = TextValueConverter.requireLong(request.getTargetId(), "目标ID不能为空");
        UnitOptionsByTargetDto dto = new UnitOptionsByTargetDto();
        List<OptionDto> options = new ArrayList<>();
        String preferredUnitCode = null;
        String table = request.getTargetTable().trim();

        // Branch：按目标表类型分别处理单位推荐逻辑
        if (TARGET_TABLE_ACTIVITY.equals(table)) {
            // Branch：诊疗活动目标：优先取活动定义上的销售单位，并至少生成一条单位选项
            ActivityDefinitionEnabledQueryModel model = findActivityById(targetId);
            if (model != null) {
                preferredUnitCode = model.getPackageUnitCode();
                appendUnitOptionByCode(options, preferredUnitCode);
            }
        } else if (TARGET_TABLE_DEVICE.equals(table)) {
            // Branch：设备/耗材目标：收集各业务单位码去重后逐条解析为 Option
            DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(targetId);
            if (device != null) {
                // Step：默认单位取首个非空业务单位字段（优先级：采购→库存→销售→剂量→拆零）
                preferredUnitCode = firstNonBlank(device.getPurchaseUnitCode(), device.getStockUnitCode(),
                    device.getPackageUnitCode(), device.getDoseUnitCode(), device.getSplitUnitCode());
                // Step：收集全部非空单位码到去重集合
                Set<String> unitCodes = new LinkedHashSet<>();
                appendIfNotBlank(unitCodes, device.getPurchaseUnitCode());
                appendIfNotBlank(unitCodes, device.getStockUnitCode());
                appendIfNotBlank(unitCodes, device.getPackageUnitCode());
                appendIfNotBlank(unitCodes, device.getDoseUnitCode());
                appendIfNotBlank(unitCodes, device.getSplitUnitCode());
                // Step：逐条解析单位码为 OptionDto（含术语库查询）
                for (String unitCode : unitCodes) {
                    appendUnitOptionByCode(options, unitCode);
                }
            }
        }

        dto.setUnitCode(preferredUnitCode);
        dto.setUnitOptions(options);
        return dto;
    }

    /**
     * 分页请求空值保护：返回默认实例，避免 NPE。
     *
     * @param request 原始请求
     * @return 非 {@code null} 请求
     */
    private PageOrderBindingRequest ensurePageRequest(PageOrderBindingRequest request) {
        if (request != null) {
            return request;
        }
        return new PageOrderBindingRequest();
    }

    /**
     * 保存前业务校验：绑定类型、来源、目标、数量与单位必填且数量大于 0。
     *
     * @param request 保存请求
     * @throws ValidationException 任一规则不满足时
     */
    private void validateSaveRequest(SaveOrderBindingRequest request) {
        if (request == null) {
            throw new ValidationException("保存失败：请求体不能为空");
        }
        if (request.getBindType() == null) {
            throw new ValidationException("保存失败：绑定类型不能为空");
        }
        if (request.getSourceId() == null || request.getSourceId().isBlank()) {
            throw new ValidationException("保存失败：来源项不能为空");
        }
        if (request.getTargetTable() == null || request.getTargetTable().isBlank()) {
            throw new ValidationException("保存失败：目标表不能为空");
        }
        if (request.getTargetId() == null || request.getTargetId().isBlank()) {
            throw new ValidationException("保存失败：目标ID不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
            throw new ValidationException("保存失败：数量必须大于0");
        }
        if (request.getUnitCode() == null || request.getUnitCode().isBlank()) {
            throw new ValidationException("保存失败：单位不能为空");
        }
    }

    /**
     * 范围码归一：空则默认全院范围。
     *
     * @param rangeCode 前端传入范围码
     * @return 非空 trim 后范围码
     */
    private String normalizeRangeCode(String rangeCode) {
        if (rangeCode == null || rangeCode.isBlank()) {
            return ServiceDeviceTemplateRangeCode.HOSPITAL.getCode();
        }
        return rangeCode.trim();
    }

    /**
     * 前端绑定类型 1/2/3 转领域 {@link ServiceDeviceTemplateTypeCode} 码。
     *
     * @param bindType 绑定类型
     * @return 领域类型码；无法识别时 {@code null}
     */
    private String toDomainTypeCode(Integer bindType) {
        if (bindType == null) {
            return null;
        }
        if (bindType == 1) {
            return ServiceDeviceTemplateTypeCode.SLOT.getCode();
        }
        if (bindType == 2) {
            return ServiceDeviceTemplateTypeCode.USAGE.getCode();
        }
        if (bindType == 3) {
            return ServiceDeviceTemplateTypeCode.ACTIVITY.getCode();
        }
        return null;
    }

    /**
     * 与 {@link #toDomainTypeCode(Integer)} 同步：解析来源项所在表名常量，供按来源分页查询使用。
     *
     * @param bindType 绑定类型
     * @return 来源表名；不识别时 {@code null}
     */
    private String toSourceTable(Integer bindType) {
        if (bindType == null) {
            return null;
        }
        if (bindType == 1) {
            return ITEM_TABLE_SLOT;
        }
        if (bindType == 2) {
            return ITEM_TABLE_USAGE;
        }
        if (bindType == 3) {
            return ITEM_TABLE_ACTIVITY;
        }
        return null;
    }

    /**
     * 前端状态筛选 1/2 转领域 {@link PublicationStatus} 持久化码；其他值返回 {@code null} 表示不按状态过滤。
     *
     * @param statusEnum 前端状态
     * @return 领域状态码或 {@code null}
     */
    private String toDomainStatus(Integer statusEnum) {
        if (statusEnum == null) {
            return null;
        }
        if (statusEnum == 1) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (statusEnum == 2) {
            return PublicationStatus.RETIRED.getCode();
        }
        return null;
    }

    /**
     * 加载可选诊疗活动定义列表为下拉项（code 为活动 id 字符串）。
     *
     * @return 选项列表，可能为空列表
     */
    private List<OptionDto> loadActivitySourceOptions() {
        List<ActivityDefinitionEnabledQueryModel> activityList = activityDefinitionQueryService.diseaseTreatmentList(null, null);
        List<OptionDto> options = new ArrayList<>();
        for (ActivityDefinitionEnabledQueryModel item : activityList) {
            if (item.getId() == null) {
                continue;
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), item.getName()));
        }
        return options;
    }

    /**
     * 加载挂号相关医疗服务（号源）列表为下拉项，标签优先展示业务号+名称。
     *
     * @return 选项列表
     */
    private List<OptionDto> loadHealthcareServiceOptions() {
        List<HealthcareService> sourceList = healthcareServiceQueryService.listServicesForRegistration();
        List<OptionDto> options = new ArrayList<>();
        for (HealthcareService item : sourceList) {
            if (item.getId() == null) {
                continue;
            }
            String label = item.getName();
            if (item.getBusNo() != null && !item.getBusNo().isBlank()) {
                label = item.getBusNo() + " " + item.getName();
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), label));
        }
        return options;
    }

    /**
     * 加载设备/耗材定义初始化列表为下拉项，供目标为设备时辅助选择。
     *
     * @return 选项列表
     */
    private List<OptionDto> loadDeviceOptions() {
        List<DeviceDefinition> deviceList = deviceDefinitionQueryService.getDeviceManageInit();
        List<OptionDto> options = new ArrayList<>();
        for (DeviceDefinition item : deviceList) {
            if (item.getId() == null) {
                continue;
            }
            String label = item.getName();
            if (item.getBusNo() != null && !item.getBusNo().isBlank()) {
                label = item.getBusNo() + " " + item.getName();
            }
            options.add(new OptionDto(0L, String.valueOf(item.getId()), label));
        }
        return options;
    }

    /**
     * 在已加载的诊疗活动列表中线性查找指定 id；数据量较大时仅为当前实现的简单策略。
     *
     * @param id 活动定义主键
     * @return 匹配行或 {@code null}
     */
    private ActivityDefinitionEnabledQueryModel findActivityById(Long id) {
        List<ActivityDefinitionEnabledQueryModel> options = activityDefinitionQueryService.diseaseTreatmentList(null, null);
        for (ActivityDefinitionEnabledQueryModel option : options) {
            if (option.getId() != null && option.getId().equals(id)) {
                return option;
            }
        }
        return null;
    }

    /**
     * 将单位码解析为 {@link OptionDto} 追加到列表；术语无命中时用码值同时作为 code 与 label 兜底。
     *
     * @param options   目标列表
     * @param unitCode  单位码
     */
    private void appendUnitOptionByCode(List<OptionDto> options, String unitCode) {
        if (unitCode == null || unitCode.isBlank()) {
            return;
        }
        Concept concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(UNIT_CODE_SYSTEM, unitCode.trim());
        if (concept == null) {
            options.add(new OptionDto(0L, unitCode.trim(), unitCode.trim()));
            return;
        }
        options.add(new OptionDto(concept.getSortOrder(), concept.getCode(), concept.getDisplay()));
    }

    /**
     * 返回参数列表中第一个非空白字符串，用于设备多单位字段择优。
     *
     * @param values 候选字符串
     * @return 首个非空值或 {@code null}
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 非空白字符串 trim 后加入集合，用于设备单位码去重收集。
     *
     * @param target 目标集合
     * @param value  原始码值
     */
    private void appendIfNotBlank(Set<String> target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        target.add(value.trim());
    }
}
