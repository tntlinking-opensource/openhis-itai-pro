import type {
  NurseStationAutoRollConfigRowDto,
  NurseStationAutoRollNursingReadonlyDto,
  NurseStationPricingCatalogRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 床位服务类目 code（自动计费选品过滤） */
export const AUTO_ROLL_BED_SERVICE_CATEGORY_CODE = '51';

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
}

/** 新增/编辑弹窗表单模型 */
export interface AutoRollConfigFormModel {
  quantityValue: number;
  remark: string;
}

/** 选中的床位服务目录项 */
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
  };
}

/** 批量映射配置列表。 */
export function mapAutoRollConfigRows(
  configs: NurseStationAutoRollConfigRowDto[] | undefined,
): AutoRollConfigTableRow[] {
  return (configs ?? []).map(mapAutoRollConfigRowToTableRow).filter((row) => row.ruleId.length > 0);
}

/** 构建床位服务选品目录行 key。 */
export function buildAutoRollCatalogRowKey(row: NurseStationPricingCatalogRowDto): string {
  const activityId = preserveApiIdForRequest(row.activityId ?? row.productId) ?? '';
  const chargeDefinitionId = preserveApiIdForRequest(row.chargeItemDefinitionId) ?? '';
  return `${activityId}::${chargeDefinitionId}`;
}

/** 根据选品行生成默认规则名称。 */
export function buildDefaultAutoRollRuleName(catalog: NurseStationPricingCatalogRowDto): string {
  const name = catalog.name?.trim();
  if (!name) {
    return '床位自动计费';
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
