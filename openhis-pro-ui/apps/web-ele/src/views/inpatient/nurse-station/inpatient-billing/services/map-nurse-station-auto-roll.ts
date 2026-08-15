import type {
  NurseStationAutoRollConfigRowDto,
  NurseStationAutoRollNursingReadonlyDto,
  NurseStationAutoRollPostedChargeRowDto,
  NurseStationPricingCatalogRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  SERVICE_CATEGORY_DISCHARGE,
  SERVICE_CATEGORY_ENTRUST,
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_TRANSFER,
} from '#/utils/print/constants/service-request-category-codes';

/** 禁止配置自动计费的诊疗类目（护理防双计；转科/出院/嘱托不计费）。 */
const AUTO_ROLL_EXCLUDED_CATEGORY_CODES = new Set([
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_TRANSFER,
  SERVICE_CATEGORY_DISCHARGE,
  SERVICE_CATEGORY_ENTRUST,
]);

export type AutoRollConfigStatus = 'ACTIVE' | 'INACTIVE';

/** 配置表格展示行 */
export interface AutoRollConfigTableRow {
  id: string;
  ruleId: string;
  ruleName: string;
  statusEnum: AutoRollConfigStatus;
  statusText: string;
  activityName: string;
  chargeDefinitionName: string;
  unitPrice?: number | string;
  quantityValue: number;
  performingOrgText: string;
  priority: number;
  remark: string;
  activityId: string;
  chargeDefinitionId: string;
  isActive: boolean;
  /** 业务时刻 HH:mm:ss */
  businessTime: string;
  billingPractitionerId: string;
  billingPractitionerName: string;
}

/** 配置展开：已滚费用展示行 */
export interface AutoRollPostedChargeTableRow {
  id: string;
  chargeItemId: string;
  procedureId: string;
  occurrenceTime: string;
  quantityText: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  statusEnum: string;
  statusText: string;
  cancelable: boolean;
}

/** 新增/编辑弹窗表单模型 */
export interface AutoRollConfigFormModel {
  quantityValue: number;
  remark: string;
  /** 业务时刻 HH:mm:ss */
  businessTime: string;
  billingPractitionerId: string;
}

/** 默认滚费业务时刻（与后端日末口径一致）。 */
export const AUTO_ROLL_DEFAULT_BUSINESS_TIME = '23:59:59';

/** 选中的诊疗项目目录项 */
export interface AutoRollSelectedCatalogItem {
  rowKey: string;
  catalog: NurseStationPricingCatalogRowDto;
}

/** 将后端配置 DTO 转为表格行。 */
export function mapAutoRollConfigRowToTableRow(
  row: NurseStationAutoRollConfigRowDto,
): AutoRollConfigTableRow {
  const ruleId = preserveApiIdForRequest(row.ruleId) ?? '';
  const statusEnum = (
    row.statusEnum === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE'
  ) as AutoRollConfigStatus;
  const quantity = Number(row.quantityValue ?? 1);
  const performingOrgId = preserveApiIdForRequest(row.performingOrgId);
  const businessTime = String(row.businessTime ?? '').trim() || AUTO_ROLL_DEFAULT_BUSINESS_TIME;
  return {
    id: ruleId,
    ruleId,
    ruleName: row.ruleName?.trim() || '—',
    statusEnum,
    statusText: row.statusDictText?.trim() || (statusEnum === 'ACTIVE' ? '启用' : '停用'),
    activityName: row.activityName?.trim() || '—',
    chargeDefinitionName: row.chargeDefinitionName?.trim() || '—',
    unitPrice: row.unitPrice,
    quantityValue: Number.isFinite(quantity) && quantity > 0 ? quantity : 1,
    performingOrgText: performingOrgId ? performingOrgId : '全院',
    priority: row.priority ?? 0,
    remark: row.remark?.trim() || '',
    activityId: preserveApiIdForRequest(row.triggerSourceId) ?? '',
    chargeDefinitionId: preserveApiIdForRequest(row.chargeDefinitionId) ?? '',
    isActive: statusEnum === 'ACTIVE',
    businessTime,
    billingPractitionerId: preserveApiIdForRequest(row.billingPractitionerId) ?? '',
    billingPractitionerName: row.billingPractitionerName?.trim() || '—',
  };
}

/** 批量映射配置列表。 */
export function mapAutoRollConfigRows(
  configs: NurseStationAutoRollConfigRowDto[] | undefined,
): AutoRollConfigTableRow[] {
  return (configs ?? []).map(mapAutoRollConfigRowToTableRow).filter((row) => row.ruleId.length > 0);
}

/** 将已滚费用 DTO 转为展开子表行。 */
export function mapAutoRollPostedChargeRow(
  row: NurseStationAutoRollPostedChargeRowDto,
): AutoRollPostedChargeTableRow | null {
  const chargeItemId = preserveApiIdForRequest(row.chargeItemId);
  if (!chargeItemId) {
    return null;
  }
  const quantity = row.quantityValue;
  const unit = row.quantityUnit?.trim() || '';
  let quantityText = '—';
  // 数量+单位拼展示；缺数量时占位
  if (quantity !== undefined && quantity !== null && String(quantity) !== '') {
    quantityText = unit ? `${quantity}${unit}` : String(quantity);
  }
  return {
    id: chargeItemId,
    chargeItemId,
    procedureId: preserveApiIdForRequest(row.procedureId) ?? '',
    occurrenceTime: row.occurrenceTime?.trim() || '—',
    quantityText,
    unitPrice: row.unitPrice,
    totalPrice: row.totalPrice,
    statusEnum: row.statusEnum?.trim() || '',
    statusText: row.statusDisplay?.trim() || row.statusEnum?.trim() || '—',
    cancelable: row.cancelable === true,
  };
}

/** 批量映射已滚费用行。 */
export function mapAutoRollPostedChargeRows(
  charges: NurseStationAutoRollPostedChargeRowDto[] | undefined,
): AutoRollPostedChargeTableRow[] {
  const rows: AutoRollPostedChargeTableRow[] = [];
  for (const charge of charges ?? []) {
    const mapped = mapAutoRollPostedChargeRow(charge);
    if (mapped) {
      rows.push(mapped);
    }
  }
  return rows;
}

/** 构建诊疗项目选品目录行 key。 */
export function buildAutoRollCatalogRowKey(row: NurseStationPricingCatalogRowDto): string {
  const activityId = preserveApiIdForRequest(row.activityId ?? row.productId) ?? '';
  const chargeDefinitionId = preserveApiIdForRequest(row.chargeItemDefinitionId) ?? '';
  return `${activityId}::${chargeDefinitionId}`;
}

/**
 * 目录行是否允许配置自动计费。
 * 排除护理/转科/出院/嘱托；类目缺失时放行交由后端校验。
 */
export function isAutoRollConfigurableCatalogRow(
  row: NurseStationPricingCatalogRowDto | null | undefined,
): boolean {
  if (!row) {
    return false;
  }
  const categoryCode = String(row.categoryCode ?? '').trim();
  if (!categoryCode) {
    return true;
  }
  return !AUTO_ROLL_EXCLUDED_CATEGORY_CODES.has(categoryCode);
}

/** 根据选品行生成默认规则名称。 */
export function buildDefaultAutoRollRuleName(catalog: NurseStationPricingCatalogRowDto): string {
  const name = catalog.name?.trim();
  if (!name) {
    return '自动计费';
  }
  return `${name}自动计费`;
}

/** 从编辑行还原弹窗表单与选品快照。 */
export function buildAutoRollFormFromTableRow(
  row: AutoRollConfigTableRow,
): AutoRollConfigFormModel {
  return {
    quantityValue: row.quantityValue,
    remark: row.remark,
    businessTime: row.businessTime || AUTO_ROLL_DEFAULT_BUSINESS_TIME,
    billingPractitionerId: row.billingPractitionerId,
  };
}

/** 从编辑行构造选品快照（无单价时仅用于展示名称）。 */
export function buildAutoRollCatalogFromTableRow(
  row: AutoRollConfigTableRow,
): AutoRollSelectedCatalogItem | null {
  if (!row.activityId || !row.chargeDefinitionId) {
    return null;
  }
  const catalog: NurseStationPricingCatalogRowDto = {
    activityId: row.activityId,
    productId: row.activityId,
    name: row.activityName === '—' ? undefined : row.activityName,
    chargeItemDefinitionId: row.chargeDefinitionId,
    unitPrice: row.unitPrice,
  };
  return {
    rowKey: buildAutoRollCatalogRowKey(catalog),
    catalog,
  };
}

/** 护理只读区是否有有效数据。 */
export function hasAutoRollNursingReadonly(
  nursing: NurseStationAutoRollNursingReadonlyDto | null | undefined,
): boolean {
  return Boolean(nursing?.activityName?.trim());
}
