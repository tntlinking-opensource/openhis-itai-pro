import type {
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

/** 耗材卡片 status 合法展示文案 */
const SUPPLY_STATUS_TEXTS = new Set<SupplyItem['status']>([
  '已发放',
  '已配药',
  '待发放',
  '待退费',
  '已退费',
]);

/** statusCode → 默认中文文案 */
const SUPPLY_STATUS_BY_CODE: Record<SupplyDispenseStatusCode, SupplyItem['status']> = {
  PENDING: '待发放',
  PREPARED: '已配药',
  COMPLETED: '已发放',
  PART_COMPLETED: '已发放',
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
 */
export function toMedicalTechPatientCardPayload(
  row: MedicalTechPatientQueueItemDto,
): OutpatientPatientCardPayload {
  const encounterId = preserveApiIdForRequest(row.encounterId) ?? '';
  const balanceSummary = String(row.accountBalanceSummaryText ?? '').trim();
  const balanceSub = String(row.accountBalanceSubText ?? '').trim();
  return {
    encounterId,
    encounterBusNo: (row.slipNo ?? '').trim() || null,
    patientName: (row.patientName ?? '').trim() || null,
    genderEnumText: (row.genderEnumText ?? '').trim() || null,
    ageText: (row.ageText ?? '').trim() || null,
    startTime: (row.encounterTime ?? '').trim() || null,
    receptionTime: null,
    contractDisplay: (row.contractDisplay ?? '').trim() || null,
    hasPendingSupply: row.hasPendingDevice === true,
    accountBalanceSummary: balanceSummary || undefined,
    accountBalanceSub: balanceSub || undefined,
    lowBalanceWarning: row.lowBalanceWarning === true,
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
 * 解析耗材卡片展示文案：优先后端 enumText（落入合法集合时），否则按 statusCode。
 */
function resolveMedicalTechSupplyStatusText(
  statusCode: SupplyDispenseStatusCode,
  dispenseStatusEnumText: null | string | undefined,
): SupplyItem['status'] {
  const text = String(dispenseStatusEnumText ?? '').trim() as SupplyItem['status'];
  if (SUPPLY_STATUS_TEXTS.has(text)) {
    return text;
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
  // 单价展示：有值才格式化
  let unitPriceText: string | undefined;
  if (row.unitPrice !== null && row.unitPrice !== undefined && row.unitPrice !== '') {
    unitPriceText = formatMoneyDisplay(row.unitPrice);
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
    lotNumber: lotNumber || undefined,
    unitPriceText,
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
