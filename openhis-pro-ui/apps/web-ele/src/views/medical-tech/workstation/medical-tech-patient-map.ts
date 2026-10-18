import type {
  MedicalTechBoundSupplyItemDto,
  MedicalTechExecutionLineDto,
  MedicalTechPatientQueueItemDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type {
  OutpatientPatientCardPayload,
  PatientInfoBarPayload,
} from '#/components/outpatient/patient-card.types';
import type { SupplyItem } from '#/components/outpatient/supply-item-card.vue';
import type { SupplyDispenseStatusCode } from '#/utils/nurse-station/nurse-execute-status';

import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 耗材卡片 status 合法展示文案（医技计费口径） */
const SUPPLY_STATUS_TEXTS = new Set<SupplyItem['status']>([
  '已计费',
  '已配药',
  '待发放',
  '待退费',
  '已退费',
  '待计费',
]);

/** 后端/历史发放文案 → 医技计费展示文案 */
const SUPPLY_STATUS_TEXT_ALIAS: Record<string, SupplyItem['status']> = {
  已发放: '已计费',
  开立带出: '待计费',
};

/** statusCode → 默认中文文案 */
const SUPPLY_STATUS_BY_CODE: Record<SupplyDispenseStatusCode, SupplyItem['status']> = {
  PENDING: '待发放',
  PREPARED: '已配药',
  COMPLETED: '已计费',
  PART_COMPLETED: '已计费',
  REFUNDING: '待退费',
  REFUNDED: '已退费',
};

const KNOWN_DISPENSE_CODES = new Set<string>([
  'PENDING',
  'PREPARED',
  'COMPLETED',
  'PART_COMPLETED',
  'REFUNDING',
  'REFUNDED',
  'DRAFT',
  'IN_PROGRESS',
  'PREPARATION',
]);

/**
 * 组装耗材行稳定主键（requestId + dispenseId）。
 */
export function buildMedicalTechDeviceLineKey(row: MedicalTechExecutionLineDto): string {
  const requestId = preserveApiIdForRequest(row.requestId) ?? '';
  const dispenseId = preserveApiIdForRequest(row.dispenseId) ?? '';
  return `${requestId}+${dispenseId}`;
}

/**
 * 队列行 → 护士站变体 PatientCard 载荷。
 * 住院行带 admissionTime/dischargeTime，卡片展示入院/出院时间（区别门诊就诊时间）。
 */
export function toMedicalTechPatientCardPayload(
  row: MedicalTechPatientQueueItemDto,
): OutpatientPatientCardPayload {
  const encounterId = preserveApiIdForRequest(row.encounterId) ?? '';
  const balanceSummary = String(row.accountBalanceSummaryText ?? '').trim();
  const balanceSub = String(row.accountBalanceSubText ?? '').trim();
  const encounterTime = (row.encounterTime ?? '').trim() || null;
  const dischargeTime = (row.dischargeTime ?? '').trim() || null;
  // 住院队列有 statusEnum：入院/出院分列；门诊仅就诊时间
  const inpatientQueue = Boolean(String(row.statusEnum ?? '').trim());
  return {
    encounterId,
    encounterBusNo: (row.slipNo ?? '').trim() || null,
    patientName: (row.patientName ?? '').trim() || null,
    genderEnumText: (row.genderEnumText ?? '').trim() || null,
    ageText: (row.ageText ?? '').trim() || null,
    startTime: encounterTime,
    receptionTime: null,
    contractDisplay: (row.contractDisplay ?? '').trim() || null,
    hasPendingSupply: row.hasPendingDevice === true,
    accountBalanceSummary: balanceSummary || undefined,
    accountBalanceSub: balanceSub || undefined,
    lowBalanceWarning: row.lowBalanceWarning === true,
    admissionTime: inpatientQueue ? encounterTime : null,
    dischargeTime: inpatientQueue ? dischargeTime : null,
  };
}

/**
 * 当前选中队列行 → PatientInfoBar 载荷。
 * 手机号/身份证：门诊与住院均回显（有值才展示）。
 */
export function toMedicalTechPatientInfoBarPayload(
  row: MedicalTechPatientQueueItemDto | null | undefined,
): PatientInfoBarPayload {
  if (!row) {
    return {
      encounterId: '',
      patientName: '',
      genderEnumText: undefined,
      ageText: '',
      startTime: '',
    };
  }
  return {
    encounterId: preserveApiIdForRequest(row.encounterId) ?? '',
    patientName: (row.patientName ?? '').trim() || null,
    genderEnumText: (row.genderEnumText ?? '').trim() || null,
    ageText: (row.ageText ?? '').trim() || null,
    startTime: (row.encounterTime ?? '').trim() || null,
    phone: (row.phone ?? '').trim() || null,
    idCard: (row.idCard ?? '').trim() || null,
    serviceTypeName: (row.contractDisplay ?? '').trim() || null,
  };
}

/**
 * 自发放状态码解析 SupplyItem.statusCode；未知码按可勾选待发放（PREPARATION 同类）。
 */
export function resolveMedicalTechSupplyStatusCode(
  dispenseStatusEnum: null | string | undefined,
): SupplyDispenseStatusCode {
  const raw = String(dispenseStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (!raw || !KNOWN_DISPENSE_CODES.has(raw)) {
    // 未知码按 PREPARATION 同类：可勾选待发放
    return 'PENDING';
  }
  if (raw === 'PREPARED') {
    return 'PREPARED';
  }
  if (raw === 'COMPLETED') {
    return 'COMPLETED';
  }
  if (raw === 'PART_COMPLETED') {
    return 'PART_COMPLETED';
  }
  if (raw === 'REFUNDING') {
    return 'REFUNDING';
  }
  if (raw === 'REFUNDED') {
    return 'REFUNDED';
  }
  // DRAFT / IN_PROGRESS / PREPARATION / PENDING → 待发放
  return 'PENDING';
}

/**
 * 解析耗材卡片展示文案：优先后端 enumText（落入合法集合或历史别名时），否则按 statusCode。
 */
function resolveMedicalTechSupplyStatusText(
  statusCode: SupplyDispenseStatusCode,
  dispenseStatusEnumText: null | string | undefined,
): SupplyItem['status'] {
  const text = String(dispenseStatusEnumText ?? '').trim();
  const aliased = SUPPLY_STATUS_TEXT_ALIAS[text];
  if (aliased) {
    return aliased;
  }
  if (SUPPLY_STATUS_TEXTS.has(text as SupplyItem['status'])) {
    return text as SupplyItem['status'];
  }
  return SUPPLY_STATUS_BY_CODE[statusCode] ?? '待发放';
}

/**
 * 耗材执行行 → SupplyItemCard 形状。
 */
export function toMedicalTechSupplyItem(row: MedicalTechExecutionLineDto): SupplyItem {
  const id = buildMedicalTechDeviceLineKey(row);
  const statusCode = resolveMedicalTechSupplyStatusCode(row.dispenseStatusEnum);
  const dispenseId = preserveApiIdForRequest(row.dispenseId);
  const qtyRaw = row.quantity;
  const quantity =
    typeof qtyRaw === 'number' ? qtyRaw : Number.parseFloat(String(qtyRaw ?? '0')) || 0;
  const lotNumber = String(row.lotNumber ?? '').trim();
  const itemCode = String(row.itemCode ?? '').trim();
  // 单价展示：有值才格式化
  let unitPriceText: string | undefined;
  if (row.unitPrice !== null && row.unitPrice !== undefined && row.unitPrice !== '') {
    unitPriceText = formatMoneyDisplay(row.unitPrice);
  }
  // 总价：优先账单合计，否则单价×数量
  let totalPriceText: string | undefined;
  if (row.chargeAmount !== null && row.chargeAmount !== undefined && row.chargeAmount !== '') {
    totalPriceText = formatMoneyDisplay(row.chargeAmount);
  } else if (
    row.unitPrice !== null &&
    row.unitPrice !== undefined &&
    row.unitPrice !== '' &&
    quantity > 0
  ) {
    const unit = Number(row.unitPrice);
    if (Number.isFinite(unit)) {
      totalPriceText = formatMoneyDisplay(unit * quantity);
    }
  }
  // 已发放（含部分完成）且有发放单时可取消
  const canCancelExecute =
    Boolean(dispenseId) && (statusCode === 'COMPLETED' || statusCode === 'PART_COMPLETED');

  return {
    id,
    name: (row.itemName ?? '').trim() || '—',
    spec: (row.specification ?? '').trim() || '—',
    usage: (row.unitName ?? '').trim() || '—',
    quantity,
    itemCode: itemCode || undefined,
    lotNumber: lotNumber || undefined,
    unitPriceText,
    totalPriceText,
    status: resolveMedicalTechSupplyStatusText(statusCode, row.dispenseStatusEnumText),
    statusCode,
    canCancelExecute,
  };
}

/**
 * 批量映射耗材行。
 */
export function toMedicalTechSupplyItems(rows: MedicalTechExecutionLineDto[]): SupplyItem[] {
  // 按执行单顺序输出侧栏卡片
  return rows.map((row) => toMedicalTechSupplyItem(row));
}

/**
 * 开立带出耗材预览 → 只读卡片（不参与勾选执行）。
 *
 * @param row 后端 boundSupplyItems 行
 * @returns 侧栏 SupplyItem（canCancelExecute=false）
 */
export function toMedicalTechBoundSupplyItem(row: MedicalTechBoundSupplyItemDto): SupplyItem {
  const qtyRaw = row.quantity;
  let quantity = 0;
  if (typeof qtyRaw === 'number') {
    quantity = qtyRaw;
  } else {
    quantity = Number.parseFloat(String(qtyRaw ?? '0')) || 0;
  }
  let unitPriceText: string | undefined;
  // 有参考单价才格式化展示
  if (row.unitPrice !== null && row.unitPrice !== undefined && row.unitPrice !== '') {
    unitPriceText = formatMoneyDisplay(row.unitPrice);
  }
  // 总价：后端参考总价优先，否则单价×数量
  let totalPriceText: string | undefined;
  if (row.totalPrice !== null && row.totalPrice !== undefined && row.totalPrice !== '') {
    totalPriceText = formatMoneyDisplay(row.totalPrice);
  } else if (
    row.unitPrice !== null &&
    row.unitPrice !== undefined &&
    row.unitPrice !== '' &&
    quantity > 0
  ) {
    const unit = Number(row.unitPrice);
    if (Number.isFinite(unit)) {
      totalPriceText = formatMoneyDisplay(unit * quantity);
    }
  }
  const sourceName = String(row.sourceItemName ?? '').trim();
  const itemCode = String(row.itemCode ?? '').trim();
  const spec = String(row.specification ?? '').trim() || '—';
  return {
    id: String(row.previewKey ?? '').trim() || `bound-${preserveApiIdForRequest(row.bindingId) ?? ''}`,
    name: (row.itemName ?? '').trim() || '—',
    spec,
    usage: (row.unitName ?? '').trim() || '—',
    quantity,
    itemCode: itemCode || undefined,
    sourceLabel: sourceName || undefined,
    unitPriceText,
    totalPriceText,
    status: '待计费',
    statusCode: 'PENDING',
    canCancelExecute: false,
  };
}

/**
 * 批量映射开立带出预览行。
 */
export function toMedicalTechBoundSupplyItems(rows: MedicalTechBoundSupplyItemDto[]): SupplyItem[] {
  return rows.map((row) => toMedicalTechBoundSupplyItem(row));
}
