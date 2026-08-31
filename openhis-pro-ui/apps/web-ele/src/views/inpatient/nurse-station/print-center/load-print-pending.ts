import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import type { NurseStationOrderAuditRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';
import type { NurseStationOrderExecuteRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import dayjs from 'dayjs';

import { httpPageMedicationSummarySlipHeaders } from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.http';
import { httpQueryNurseStationInpatientCostDetail } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.http';
import type { NurseStationCostDetailStatusFilter } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';
import { httpListNurseStationNursingRecords } from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import { httpListNurseStationOrderAudit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.http';
import { httpListNurseStationOrderExecuteForPrint } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import type { NurseStationNursingRecordRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.types';
import {
  buildCostDetailLineRowKey,
  buildCostSummaryLineRowKey,
  formatCostDetailQuantity,
  formatCostSummaryQuantity,
  resolveCostDetailDisplayDateTime,
} from '#/views/inpatient/nurse-station/cost-details/services/map-nurse-station-cost-detail';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  INFUSION_METHOD_CODES,
  METHOD_CODE_IV_INJECTION,
  ORAL_METHOD_CODES,
  normalizeMedicationMethodCode,
} from '#/utils/print/constants/medication-method-codes';
import {
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
} from '#/utils/print/constants/service-request-category-codes';
import {
  formatOrderDispenseTotalQuantityText,
  shouldUsePackageBatchDispenseQuantity,
} from '#/utils/print/mappers/inpatient-order-dispense-sheet';
import { resolveStandingOrderPackageBatchSiteEnabled } from '#/utils/print/standing-order-package-batch-site';
import { resolveAuditOrderKindLabel } from '#/views/inpatient/nurse-station/order-audit/services/map-nurse-station-order-audit';

import {
  buildInfusionPrintListRowKey,
  buildOralMedPrintListRowKey,
  enrichPrintPendingPrintedStatus,
} from './enrich-printed-status';

export interface LoadPrintPendingQuery {
  docType: PrintDocType;
  patients: NurseStationModulePatient[];
  dateFrom: string;
  dateTo: string;
  /** 医嘱截止时间 YYYY-MM-DD HH:mm:ss */
  executionDeadline: string;
  /** 费用清单/明细：与费用明细页状态筛选一致；默认待结算 */
  costStatusEnum?: NurseStationCostDetailStatusFilter;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function patientByEncounter(
  patients: NurseStationModulePatient[],
  encounterId: string,
): NurseStationModulePatient | undefined {
  return patients.find((p) => str(p.id) === encounterId);
}

function bedNameOf(patient?: NurseStationModulePatient): { bedNo: string; patientName: string } {
  return {
    bedNo: str(patient?.bedNo) || '—',
    patientName: str(patient?.name) || '—',
  };
}

/** 是否输液途径（methodCode ∈ INFUSION_METHOD_CODES） */
function isInfusionMethodCode(methodCode?: null | string): boolean {
  const normalized = normalizeMedicationMethodCode(methodCode);
  if (!normalized) {
    return false;
  }
  return INFUSION_METHOD_CODES.has(normalized) || INFUSION_METHOD_CODES.has(str(methodCode));
}

/** 是否静脉注射途径（404；与医嘱执行右键「打印输液贴」口径一致） */
function isIvInjectionMethodCode(methodCode?: null | string): boolean {
  const raw = str(methodCode);
  if (!raw) {
    return false;
  }
  const normalized = normalizeMedicationMethodCode(raw);
  return normalized === METHOD_CODE_IV_INJECTION || raw === METHOD_CODE_IV_INJECTION;
}

/** 是否口服途径（methodCode ∈ ORAL_METHOD_CODES） */
function isOralMethodCode(methodCode?: null | string): boolean {
  const normalized = normalizeMedicationMethodCode(methodCode);
  if (!normalized) {
    return false;
  }
  return ORAL_METHOD_CODES.has(normalized) || ORAL_METHOD_CODES.has(str(methodCode));
}

/** 医嘱执行行是否口服药（仅 methodCode，禁止用 usageDisplay） */
function isOralExecuteOrder(order: NurseStationOrderExecuteRowDto): boolean {
  if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
    return false;
  }
  return isOralMethodCode(order.methodCode);
}

/** 医嘱执行行是否输液药品（优先 infusionFlag，其次 methodCode；字段未回传时不误杀） */
function isInfusionExecuteOrder(order: NurseStationOrderExecuteRowDto): boolean {
  if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
    return false;
  }
  // 明确输液标志
  if (order.infusionFlag === true) {
    return true;
  }
  const methodCode = str(order.methodCode);
  // 有途径 code：静滴 405/IVD，或静脉注射 404（右键打印输液贴固定对应）
  if (methodCode) {
    return isInfusionMethodCode(methodCode) || isIvInjectionMethodCode(methodCode);
  }
  // infusionFlag === false 且无 methodCode → 非输液
  if (order.infusionFlag === false) {
    return false;
  }
  // 旧后端未回传 methodCode/infusionFlag：无法按途径过滤，先纳入药嘱行，避免空列表
  return true;
}

/**
 * 患者用药单「输液药 / 非输液药品」筛选用（双判定，任一成立即输液）：
 * ① 药嘱/目录回传的 infusionFlag === true（后端可对空标记回落 inject_flag）
 * ② 用法 methodCode 为静滴(405/IVD) 或 静脉注射(404)
 * 二者皆无时按非输液。
 */
function resolveMedSheetInfusionFlag(order: NurseStationOrderExecuteRowDto): boolean {
  // ① 输液标记
  if (order.infusionFlag === true) {
    return true;
  }
  // ② 用法（禁止用 usageDisplay）
  const methodCode = str(order.methodCode);
  if (methodCode.length > 0) {
    return isInfusionMethodCode(methodCode) || isIvInjectionMethodCode(methodCode);
  }
  return false;
}

/**
 * 输液巡视卡待打：查询日有待执行或已执行槽位的输液药患者各一行。
 * 与输液贴同源（医嘱执行双 Tab），执行过的也可打。
 */
async function loadPatrolPending(
  patients: NurseStationModulePatient[],
  executionDeadline: string,
  queryDate?: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length === 0) {
    return [];
  }

  const date =
    str(queryDate) ||
    (executionDeadline
      ? dayjs(executionDeadline).format('YYYY-MM-DD')
      : dayjs().format('YYYY-MM-DD'));
  const deadline = str(executionDeadline) || dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline: deadline,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  /** 按就诊归并：查询日有槽位的输液药嘱 */
  const byEncounter = new Map<
    string,
    {
      encounterId: string;
      patientId: string;
      groupKeys: Set<string>;
      drugNames: string[];
      hasPending: boolean;
      hasExecuted: boolean;
    }
  >();

  function mergeTabOrders(
    groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      const encounterId = str(group.encounterId);
      if (!encounterId) {
        continue;
      }
      for (const order of group.orders ?? []) {
        if (!isInfusionExecuteOrder(order)) {
          continue;
        }
        const slots = tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
        if (!slots.some((slot) => isSlotOnQueryDate(slot, date))) {
          continue;
        }
        const existing = byEncounter.get(encounterId);
        const groupKey = str(order.groupId) || `r:${str(order.orderId)}`;
        const drugName = str(order.adviceDisplayName);
        if (!existing) {
          byEncounter.set(encounterId, {
            encounterId,
            patientId: str(order.patientId),
            groupKeys: new Set([groupKey]),
            drugNames: drugName ? [drugName] : [],
            hasPending: tab === 'pending',
            hasExecuted: tab === 'executed',
          });
          continue;
        }
        existing.groupKeys.add(groupKey);
        if (drugName && !existing.drugNames.includes(drugName)) {
          existing.drugNames.push(drugName);
        }
        if (tab === 'pending') {
          existing.hasPending = true;
        } else {
          existing.hasExecuted = true;
        }
      }
    }
  }

  mergeTabOrders(pendingRes.groups ?? [], 'pending');
  mergeTabOrders(executedRes.groups ?? [], 'executed');

  const rows: PrintPendingRow[] = [];
  for (const bucket of byEncounter.values()) {
    const patient = patientByEncounter(patients, bucket.encounterId);
    const meta = bedNameOf(patient);
    const groupCount = bucket.groupKeys.size;
    const namePreview =
      bucket.drugNames.length > 0
        ? `${bucket.drugNames.slice(0, 2).join('、')}${bucket.drugNames.length > 2 ? '…' : ''}`
        : '—';
    rows.push({
      id: `patrol-${bucket.encounterId}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      // 展示实际药品，不写「输液巡视卡」汇总标题
      title: namePreview,
      subtitle: `共 ${groupCount} 组`,
      timeText: date,
      statusCode: 'pending',
      statusText: '待打',
      pageHint: `${groupCount}`,
      encounterId: bucket.encounterId,
      patientId: bucket.patientId || str(patient?.patientId),
    });
  }
  return rows;
}

/** 腕带：每位选中患者一行（对齐纸单：诊断/住院号/性别年龄/入院日） */
function buildWristbandPending(patients: NurseStationModulePatient[]): PrintPendingRow[] {
  return patients.map((patient) => {
    const meta = bedNameOf(patient);
    const genderAge = [str(patient.gender), patient.age != null ? `${patient.age}岁` : '']
      .filter(Boolean)
      .join(' · ');
    const admissionRaw = str(patient.admissionAt) || str(patient.inWardAt);
    return {
      id: `wristband-${str(patient.id)}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: str(patient.diagnosis) || '—',
      subtitle: str(patient.encounterBusNo) || '—',
      timeText: admissionRaw
        ? dayjs(admissionRaw).isValid()
          ? dayjs(admissionRaw).format('YYYY-MM-DD')
          : admissionRaw.slice(0, 10)
        : '—',
      statusCode: 'pending' as const,
      statusText: '待打',
      pageHint: genderAge || '—',
      encounterId: str(patient.id),
      patientId: str(patient.patientId),
    };
  });
}

/** 床头卡：每位选中患者一行（对齐纸单：护理级别/诊断/入院日/主治·医保） */
function buildBedCardPending(patients: NurseStationModulePatient[]): PrintPendingRow[] {
  return patients.map((patient) => {
    const meta = bedNameOf(patient);
    const doctorOrFee = [str(patient.attendingDoctorName), str(patient.feeType)]
      .filter(Boolean)
      .join(' · ');
    const admissionRaw = str(patient.admissionAt) || str(patient.inWardAt);
    return {
      id: `bed-card-${str(patient.id)}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: str(patient.nurseLevel) || '—',
      subtitle: str(patient.diagnosis) || '—',
      timeText: admissionRaw
        ? dayjs(admissionRaw).isValid()
          ? dayjs(admissionRaw).format('YYYY-MM-DD')
          : admissionRaw.slice(0, 10)
        : '—',
      statusCode: 'pending' as const,
      statusText: '待打',
      pageHint: doctorOrFee || '—',
      encounterId: str(patient.id),
      patientId: str(patient.patientId),
    };
  });
}

/** 费用分类展示：优先产品分类，回落费用大类 */
function resolveFeeCategoryText(line: {
  categoryCode?: string;
  categoryDisplay?: string;
  expenseBlockCode?: string;
  expenseBlockDisplay?: string;
}): string {
  return (
    str(line.categoryDisplay) ||
    str(line.categoryCode) ||
    str(line.expenseBlockDisplay) ||
    str(line.expenseBlockCode) ||
    '—'
  );
}

/** 金额展示（空则 —） */
function feeAmountText(value: null | number | string | undefined): string {
  if (value == null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return formatMoneyDisplay(num);
}

/** 费用清单待打：SUMMARY 正净额行拆开展示（与打印同源口径） */
async function loadFeeListPending(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
  statusEnum: NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT',
): Promise<PrintPendingRow[]> {
  const patient = patients[0];
  if (!patient) {
    return [];
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId || !dateFrom || !dateTo) {
    return [];
  }
  const res = await httpQueryNurseStationInpatientCostDetail({
    encounterId,
    patientId,
    dateFrom,
    dateTo,
    presentationMode: 'SUMMARY',
    statusEnum,
    excludeOffsetPairs: true,
  });
  const meta = bedNameOf(patient);
  const rows: PrintPendingRow[] = [];
  for (const line of res.summaryLines ?? []) {
    const amount = Number(line.lineTotalAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      continue;
    }
    const listRowKey = buildCostSummaryLineRowKey(line);
    const qty = formatCostSummaryQuantity(line);
    const spec = str(line.itemSpec);
    const titleParts = [str(line.itemName) || '—'];
    if (spec) {
      titleParts.push(`（${spec}）`);
    }
    if (qty && qty !== '—') {
      titleParts.push(` · ${qty}`);
    }
    rows.push({
      id: `fee-list-${listRowKey}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: titleParts.join(''),
      subtitle: resolveFeeCategoryText(line),
      timeText: `${dateFrom} ~ ${dateTo}`,
      statusCode: 'pending',
      statusText: '待打',
      pageHint: feeAmountText(line.lineTotalAmount),
      encounterId,
      patientId,
      listRowKey,
    });
  }
  return rows;
}

/** 费用明细单待打：DETAIL 流水逐行展示 */
async function loadFeeDetailPending(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
  statusEnum: NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT',
): Promise<PrintPendingRow[]> {
  const patient = patients[0];
  if (!patient) {
    return [];
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId || !dateFrom || !dateTo) {
    return [];
  }
  const res = await httpQueryNurseStationInpatientCostDetail({
    encounterId,
    patientId,
    dateFrom,
    dateTo,
    presentationMode: 'DETAIL',
    statusEnum,
  });
  const meta = bedNameOf(patient);
  const rows: PrintPendingRow[] = [];
  for (const line of res.detailLines ?? []) {
    const listRowKey = buildCostDetailLineRowKey(line);
    const when = resolveCostDetailDisplayDateTime(line);
    const qty = formatCostDetailQuantity(line);
    const spec = str(line.itemSpec);
    const titleParts = [str(line.itemName) || '—'];
    if (spec) {
      titleParts.push(`（${spec}）`);
    }
    if (qty && qty !== '—') {
      titleParts.push(` · ${qty}`);
    }
    rows.push({
      id: `fee-detail-${listRowKey}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: titleParts.join(''),
      subtitle: resolveFeeCategoryText(line),
      timeText: when
        ? dayjs(when).isValid()
          ? dayjs(when).format('MM-DD HH:mm')
          : when
        : `${dateFrom} ~ ${dateTo}`,
      statusCode: 'pending',
      statusText: '待打',
      pageHint: feeAmountText(line.totalPrice),
      encounterId,
      patientId,
      listRowKey,
    });
  }
  return rows;
}

/** 医嘱校对列表 → 待打行（已校对/已停止；可按开嘱日区间过滤） */
function mapOrderAuditToPending(
  orders: NurseStationOrderAuditRowDto[],
  patients: NurseStationModulePatient[],
  therapyLabel: string,
  dateFrom?: string,
  dateTo?: string,
  /** 长期待打时护理级别不受开嘱日区间裁掉 */
  keepNursingLevelOutsideRange?: boolean,
): PrintPendingRow[] {
  const rows: PrintPendingRow[] = [];
  const fromDay = str(dateFrom);
  const toDay = str(dateTo);
  for (const order of orders) {
    const encounterId = str(order.encounterId);
    const patient = patientByEncounter(patients, encounterId);
    const meta = bedNameOf(patient);
    const listRowKey = str(order.listRowKey) || `${str(order.requestId)}-${str(order.orderType)}`;
    const opened = str(order.reqOpenedTime);
    const isNursingLevel =
      keepNursingLevelOutsideRange && str(order.categoryEnum) === SERVICE_CATEGORY_NURSING;
    if (!isNursingLevel && fromDay && toDay && opened && dayjs(opened).isValid()) {
      const day = dayjs(opened).format('YYYY-MM-DD');
      if (day < fromDay || day > toDay) {
        continue;
      }
    }
    const title =
      str(order.adviceDisplayName) || (isNursingLevel ? str(patient?.nurseLevel) : '') || '—';
    const orderKindLabel = resolveAuditOrderKindLabel(order);
    rows.push({
      id: listRowKey,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title,
      // 说明列展示医嘱类型（西药/嘱托/诊疗等），长临放 pageHint
      subtitle: orderKindLabel,
      timeText: opened && dayjs(opened).isValid() ? dayjs(opened).format('MM-DD HH:mm') : '',
      // 业务态保留「已校对」；打印态由 enrich 写入 printStatus*
      statusCode: 'executable',
      statusText: '已校对',
      pageHint: therapyLabel,
      encounterId,
      patientId: str(order.patientId) || str(patient?.patientId),
      listRowKey,
      orderId: str(order.requestId),
      groupId: str(order.groupId),
    });
  }
  return rows;
}

/**
 * 医嘱执行摆药单待打：查询日已执行全部药品（同医嘱多次执行合并为一行）。
 * 不过滤口服/非口服；西药/中药分 Tab 拉取后合并；仅 MED_REQUEST。
 */
async function loadOrderDispenseSheetPending(
  patients: NurseStationModulePatient[],
  queryDate: string,
): Promise<PrintPendingRow[]> {
  return loadExecutedMedSheetPending(patients, queryDate, 'all');
}

/** 患者用药单待打：按日已执行全部药品（打印时按就诊合块、一张多人；勾选打印） */
async function loadOralMedSheetPending(
  patients: NurseStationModulePatient[],
  queryDate: string,
): Promise<PrintPendingRow[]> {
  return loadExecutedMedSheetPending(patients, queryDate, 'patient_med');
}

/**
 * 按日已执行药品待打行。
 * mode=all → 摆药单；mode=patient_med → 患者用药单（全部药品，勾选打印）。
 */
async function loadExecutedMedSheetPending(
  patients: NurseStationModulePatient[],
  queryDate: string,
  mode: 'all' | 'patient_med',
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length === 0) {
    return [];
  }
  const date = queryDate || dayjs().format('YYYY-MM-DD');
  const executionDeadline = dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    executionDeadline,
    executeTab: 'executed' as const,
  };
  // 后端 medicineCategoryTab 单值：西药与中药各拉一次再合并
  const [westRes, tcmRes, packageBatchSiteEnabled] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'west' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'tcm' }),
    resolveStandingOrderPackageBatchSiteEnabled(),
  ]);

  const seenOrderKeys = new Set<string>();
  const rows: PrintPendingRow[] = [];
  const idPrefix = mode === 'patient_med' ? 'patient-med-sheet' : 'order-dispense';

  function accumulateGroups(
    groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
  ) {
    for (const group of groups) {
      const encounterId = str(group.encounterId);
      if (!encounterId) {
        continue;
      }
      const patient = patientByEncounter(patients, encounterId);
      const meta = bedNameOf(patient);
      for (const order of group.orders ?? []) {
        if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
          continue;
        }
        const orderKey =
          str(order.listRowKey) || `${encounterId}-${str(order.orderId)}-${str(order.orderType)}`;
        if (!orderKey || seenOrderKeys.has(orderKey)) {
          continue;
        }
        const slotsOnDay = (order.executedSlots ?? []).filter((slot) => {
          const at = str(slot.recordedTime) || str(slot.planTime);
          if (!at) {
            return false;
          }
          if (at.startsWith(date)) {
            return true;
          }
          return dayjs(at).isValid() && dayjs(at).format('YYYY-MM-DD') === date;
        });
        if (slotsOnDay.length <= 0) {
          continue;
        }
        seenOrderKeys.add(orderKey);
        const latestSlot = slotsOnDay[slotsOnDay.length - 1];
        const latestAt = str(latestSlot?.recordedTime) || str(latestSlot?.planTime);
        const packageBatchQuantity = shouldUsePackageBatchDispenseQuantity({
          packageBatchSiteEnabled,
          isOral: isOralMethodCode(order.methodCode),
          therapyEnum: order.therapyEnum,
          packageSplitRatioText: order.packageSplitRatioText,
          packageUnitDisplay: order.packageUnitDisplay,
        });
        rows.push({
          id: `${idPrefix}-${orderKey}`,
          bedNo: meta.bedNo,
          patientName: meta.patientName,
          title: str(order.adviceDisplayName) || '—',
          subtitle: str(order.specificationText),
          timeText: latestAt ? dayjs(latestAt).format('HH:mm') : date,
          statusCode: 'executable',
          statusText: '已执行',
          pageHint: formatOrderDispenseTotalQuantityText(order.dosageDisplay, slotsOnDay.length, {
            orderTotalQuantityText: order.quantityDisplay,
            packageBatchQuantity,
            packageUnitDisplay: order.packageUnitDisplay,
          }),
          encounterId,
          patientId: str(order.patientId) || str(patient?.patientId),
          listRowKey: orderKey,
          orderId: str(order.orderId),
          groupId: str(order.groupId),
          infusionFlag: resolveMedSheetInfusionFlag(order),
        });
      }
    }
  }

  accumulateGroups(westRes.groups ?? []);
  accumulateGroups(tcmRes.groups ?? []);

  rows.sort((a, b) => {
    const bedCmp = a.bedNo.localeCompare(b.bedNo, 'zh-CN');
    if (bedCmp !== 0) {
      return bedCmp;
    }
    return a.title.localeCompare(b.title, 'zh-CN');
  });

  // 医嘱执行摆药单 / 患者用药单：不在此 enrich，统一走 loadPrintPendingRows 出口
  return rows;
}

/** 加载临嘱/长嘱待打：已校对 + 已停止（可按开嘱日区间收窄） */
async function loadOrderPending(
  patients: NurseStationModulePatient[],
  therapyType: 'long' | 'temp',
  executionDeadline: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length === 0) {
    return [];
  }
  // 已校对 Tab 已含已停嘱校对行（同一条只一行）
  const passedRes = await httpListNurseStationOrderAudit({
    encounterIds,
    auditTab: 'passed',
    therapyType,
  });
  const seen = new Set<string>();
  const orders: NurseStationOrderAuditRowDto[] = [];
  for (const group of passedRes.groups ?? []) {
    for (const order of group.orders ?? []) {
      const key = str(order.listRowKey) || `${str(order.requestId)}-${str(order.orderType)}`;
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      orders.push(order);
    }
  }
  const rangeTo =
    str(dateTo) || (executionDeadline ? dayjs(executionDeadline).format('YYYY-MM-DD') : '');
  return mapOrderAuditToPending(
    orders,
    patients,
    therapyType === 'temp' ? '临时' : '长期',
    str(dateFrom) || undefined,
    rangeTo || undefined,
    therapyType === 'long',
  );
}

/** 计划点是否落在查询日（锚定 planTime 日期） */
function isSlotOnQueryDate(slot: { planTime?: string }, queryDate: string): boolean {
  const planTime = str(slot.planTime);
  if (!planTime || !queryDate) {
    return false;
  }
  // 优先前缀匹配，兼容 "YYYY-MM-DD HH:mm:ss" / ISO
  if (planTime.startsWith(queryDate)) {
    return true;
  }
  const parsed = dayjs(planTime);
  if (!parsed.isValid()) {
    return false;
  }
  return parsed.format('YYYY-MM-DD') === queryDate;
}

/** 槽位归属日：优先计划点，已执行可回落 recordedTime（一盒一点长嘱） */
function resolveOralSlotDay(slot: { planTime?: string; recordedTime?: string }): string {
  const raw = str(slot.planTime) || str(slot.recordedTime);
  if (!raw) {
    return '';
  }
  if (/^\d{4}-\d{2}-\d{2}/.test(raw)) {
    return raw.slice(0, 10);
  }
  const parsed = dayjs(raw);
  if (!parsed.isValid()) {
    return '';
  }
  return parsed.format('YYYY-MM-DD');
}

/** 槽位是否落在查询闭区间（含起止日） */
function isOralSlotInQueryRange(
  slot: { planTime?: string; recordedTime?: string },
  dateFrom: string,
  dateTo: string,
): boolean {
  const day = resolveOralSlotDay(slot);
  if (!day || !dateFrom) {
    return false;
  }
  const to = dateTo || dateFrom;
  return day >= dateFrom && day <= to;
}

/** 单日输液贴候选：同 listRowKey 合并 pending/executed 当日槽位 */
interface InfusionDayOrderBucket {
  order: NurseStationOrderExecuteRowDto;
  pendingOnDay: NonNullable<NurseStationOrderExecuteRowDto['pendingSlots']>;
  executedOnDay: NonNullable<NurseStationOrderExecuteRowDto['executedSlots']>;
}

/**
 * 输液贴：已校对（执行列表仅 VERIFIED）药嘱中，查询日有待执行或已执行槽位的输液组。
 * 同 encounter + groupId 合并为一张贴；无组号则按 orderId 单独成组。
 */
async function loadInfusionPending(
  patients: NurseStationModulePatient[],
  queryDate: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients.map((p) => String(p.id ?? '').trim()).filter((id) => id.length > 0);
  if (encounterIds.length === 0) {
    return [];
  }
  const date = queryDate || dayjs().format('YYYY-MM-DD');
  // 截止到查询日末，与医嘱执行页 executeDeadline 同构
  const executionDeadline = dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
  };
  // 已校对医嘱：并行拉未执行 + 已执行，再按日合并
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  const byRowKey = new Map<string, InfusionDayOrderBucket>();

  /** 把某一 Tab 的输液行并入当日桶 */
  function mergeTabOrders(
    groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      for (const order of group.orders ?? []) {
        if (!isInfusionExecuteOrder(order)) {
          continue;
        }
        const listRowKey = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
        if (!listRowKey) {
          continue;
        }
        const slots = tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
        // 只保留查询日计划点
        const onDay = slots.filter((slot) => isSlotOnQueryDate(slot, date));
        if (onDay.length <= 0) {
          continue;
        }
        const existing = byRowKey.get(listRowKey);
        if (!existing) {
          byRowKey.set(listRowKey, {
            order,
            pendingOnDay: tab === 'pending' ? onDay : [],
            executedOnDay: tab === 'executed' ? onDay : [],
          });
          continue;
        }
        if (tab === 'pending') {
          existing.pendingOnDay = onDay;
        } else {
          existing.executedOnDay = onDay;
        }
      }
    }
  }

  mergeTabOrders(pendingRes.groups ?? [], 'pending');
  mergeTabOrders(executedRes.groups ?? [], 'executed');

  // 按就诊 + 组号归并（同组多药打一张贴）
  const byEncounterGroup = new Map<
    string,
    { encounterId: string; groupKey: string; buckets: InfusionDayOrderBucket[] }
  >();
  for (const bucket of byRowKey.values()) {
    const encounterId = str(bucket.order.encounterId);
    if (!encounterId) {
      continue;
    }
    const groupKey = str(bucket.order.groupId) || `r:${str(bucket.order.orderId)}`;
    const mapKey = `${encounterId}::${groupKey}`;
    const existing = byEncounterGroup.get(mapKey);
    if (!existing) {
      byEncounterGroup.set(mapKey, { encounterId, groupKey, buckets: [bucket] });
      continue;
    }
    existing.buckets.push(bucket);
  }

  const rows: PrintPendingRow[] = [];
  // 同就诊内组序号（展示用）
  const groupIndexByEncounter = new Map<string, number>();
  for (const { encounterId, groupKey, buckets } of byEncounterGroup.values()) {
    const patient = patientByEncounter(patients, encounterId);
    const meta = bedNameOf(patient);
    const groupIndex = (groupIndexByEncounter.get(encounterId) ?? 0) + 1;
    groupIndexByEncounter.set(encounterId, groupIndex);

    const names = buckets.map((b) => str(b.order.adviceDisplayName)).filter(Boolean);
    const head = buckets[0]?.order;
    const usage = str(head?.usageDisplay) || '输液';

    // 组内任一条有当日待执行 → 未执行/部分已执行；否则已执行
    const hasPending = buckets.some((b) => b.pendingOnDay.length > 0);
    const hasExecuted = buckets.some((b) => b.executedOnDay.length > 0);
    let statusCode: PrintPendingRow['statusCode'] = 'pending';
    let statusText = '未执行';
    if (hasPending && hasExecuted) {
      statusCode = 'pending';
      statusText = '部分已执行';
    } else if (hasExecuted) {
      statusCode = 'executable';
      statusText = '已执行';
    }

    // 时间文案：优先取当日最早计划点
    const dayTimes = buckets
      .flatMap((b) => [...b.pendingOnDay, ...b.executedOnDay])
      .map((s) => str(s.planTime))
      .filter(Boolean)
      .slice()
      .sort((a, b) => a.localeCompare(b));
    const timeText = dayTimes[0] ? dayjs(dayTimes[0]).format('HH:mm') : date;
    const listRowKey = buildInfusionPrintListRowKey(encounterId, groupKey, date);

    rows.push({
      id: `infusion-${encounterId}-${groupKey}-${date}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: names.join(' + ') || str(head?.adviceDisplayName) || '输液组',
      subtitle: `第 ${groupIndex} 组 · ${usage}`,
      timeText,
      statusCode,
      statusText,
      pageHint: '贴签',
      encounterId,
      patientId: str(head?.patientId) || str(patient?.patientId),
      groupId: str(head?.groupId) || groupKey,
      orderId: str(head?.orderId),
      listRowKey,
    });
  }
  return rows;
}

/** 口服药贴区间候选：同 listRowKey 合并 pending/executed 区间内槽位 */
interface OralDayOrderBucket {
  order: NurseStationOrderExecuteRowDto;
  pendingOnDay: NonNullable<NurseStationOrderExecuteRowDto['pendingSlots']>;
  executedOnDay: NonNullable<NurseStationOrderExecuteRowDto['executedSlots']>;
}

/**
 * 口服药贴：已校对药嘱中，查询区间内有待执行或已执行槽位的口服药。
 * 打印中心为日期区间，执行页抽屉为单日（from=to）；每条医嘱一张贴。
 */
async function loadOralMedPending(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length <= 0) {
    return [];
  }
  const fromDay = str(dateFrom) || dayjs().format('YYYY-MM-DD');
  const toDay = str(dateTo) || fromDay;
  const rangeFrom = fromDay <= toDay ? fromDay : toDay;
  const rangeTo = fromDay <= toDay ? toDay : fromDay;
  // 截止到区间末日，下界为区间首日，避免只按起始日截掉后续计划点
  const executionDeadline = dayjs(rangeTo).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const planTimeFrom = `${rangeFrom} 00:00:00`;
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    executionDeadline,
    planTimeFrom,
  };
  // 西药/中药分 Tab；待执行 + 已执行，再按区间槽位合并
  const [westPending, westExecuted, tcmPending, tcmExecuted] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'west',
      executeTab: 'pending',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'west',
      executeTab: 'executed',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'tcm',
      executeTab: 'pending',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'tcm',
      executeTab: 'executed',
    }),
  ]);

  const byRowKey = new Map<string, OralDayOrderBucket>();

  /** 把某一 Tab 的口服行并入区间桶 */
  function mergeTabOrders(
    groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      for (const order of group.orders ?? []) {
        // 只保留口服途径药嘱
        if (!isOralExecuteOrder(order)) {
          continue;
        }
        const listRowKey = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
        if (!listRowKey) {
          continue;
        }
        const slots = tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
        const inRange = slots.filter((slot) => isOralSlotInQueryRange(slot, rangeFrom, rangeTo));
        if (inRange.length <= 0) {
          continue;
        }
        const existing = byRowKey.get(listRowKey);
        if (!existing) {
          byRowKey.set(listRowKey, {
            order,
            pendingOnDay: tab === 'pending' ? inRange : [],
            executedOnDay: tab === 'executed' ? inRange : [],
          });
          continue;
        }
        if (tab === 'pending') {
          existing.pendingOnDay = inRange;
        } else {
          existing.executedOnDay = inRange;
        }
      }
    }
  }

  mergeTabOrders(westPending.groups ?? [], 'pending');
  mergeTabOrders(westExecuted.groups ?? [], 'executed');
  mergeTabOrders(tcmPending.groups ?? [], 'pending');
  mergeTabOrders(tcmExecuted.groups ?? [], 'executed');

  const rows: PrintPendingRow[] = [];
  const rangeKey = rangeFrom === rangeTo ? rangeFrom : `${rangeFrom}~${rangeTo}`;
  for (const bucket of byRowKey.values()) {
    const encounterId = str(bucket.order.encounterId);
    if (!encounterId) {
      continue;
    }
    const patient = patientByEncounter(patients, encounterId);
    const meta = bedNameOf(patient);
    // 口服贴按医嘱一行，不套输液组成组的「部分已执行」；有已执行槽即已执行
    const hasExecuted = bucket.executedOnDay.length > 0;
    const statusCode: PrintPendingRow['statusCode'] = hasExecuted ? 'executable' : 'pending';
    const statusText = hasExecuted ? '已执行' : '未执行';
    // 时间文案：区间内最早计划/执行点
    const dayTimes = [...bucket.pendingOnDay, ...bucket.executedOnDay]
      .map((slot) => str(slot.planTime) || str(slot.recordedTime))
      .filter(Boolean)
      .slice()
      .sort((a, b) => a.localeCompare(b));
    const timeFormat = rangeFrom === rangeTo ? 'HH:mm' : 'MM-DD HH:mm';
    const timeText = dayTimes[0]
      ? dayjs(dayTimes[0]).isValid()
        ? dayjs(dayTimes[0]).format(timeFormat)
        : dayTimes[0]
      : rangeFrom;
    const orderId = str(bucket.order.orderId);
    const orderListRowKey =
      str(bucket.order.listRowKey) || `${orderId}-${str(bucket.order.orderType)}`;
    const printListRowKey = buildOralMedPrintListRowKey(
      encounterId,
      orderId || orderListRowKey,
      rangeKey,
    );
    const dose = str(bucket.order.dosageDisplay);
    const spec = str(bucket.order.specificationText);
    rows.push({
      id: `oral-${encounterId}-${orderId || orderListRowKey}-${rangeKey}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: str(bucket.order.adviceDisplayName) || '口服药',
      subtitle: [spec, dose].filter(Boolean).join(' · '),
      timeText,
      statusCode,
      statusText,
      pageHint: '药贴',
      encounterId,
      patientId: str(bucket.order.patientId) || str(patient?.patientId),
      orderId,
      listRowKey: printListRowKey,
      groupId: str(bucket.order.groupId),
    });
  }
  rows.sort((a, b) => {
    const bedCmp = a.bedNo.localeCompare(b.bedNo, 'zh-CN');
    if (bedCmp !== 0) {
      return bedCmp;
    }
    return a.title.localeCompare(b.title, 'zh-CN');
  });
  return rows;
}

/** 是否目标类目的诊疗申请（用 categoryEnum code，禁止用名称猜测） */
function isServiceApplyOrder(order: NurseStationOrderExecuteRowDto, categoryCode: string): boolean {
  if (str(order.orderType).toUpperCase() !== 'SERVICE_REQUEST') {
    return false;
  }
  return str(order.categoryEnum) === categoryCode;
}

/**
 * 检查/检验申请单待打：已校对诊疗医嘱（待执行+已执行）中匹配类目的申请行。
 * categoryEnum：检查=23，检验=22；开嘱日区间交后端 reqOpenedTimeFrom/To 过滤。
 */
async function loadServiceApplyPending(
  patients: NurseStationModulePatient[],
  categoryCode: string,
  executionDeadline: string,
  pageHint: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length <= 0) {
    return [];
  }
  const fromDay = str(dateFrom);
  const toDay = str(dateTo);
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
    // 有查询日时传开嘱日闭区间，后端按 reqOpenedTime 收窄
    reqOpenedTimeFrom: fromDay ? `${fromDay} 00:00:00` : undefined,
    reqOpenedTimeTo: toDay ? `${toDay} 23:59:59` : undefined,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  const byRowKey = new Map<
    string,
    { encounterId: string; order: NurseStationOrderExecuteRowDto; hasPending: boolean }
  >();

  function mergeGroups(
    groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      const encounterId = str(group.encounterId);
      for (const order of group.orders ?? []) {
        if (!isServiceApplyOrder(order, categoryCode)) {
          continue;
        }
        const listRowKey = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
        if (!listRowKey) {
          continue;
        }
        const existing = byRowKey.get(listRowKey);
        if (!existing) {
          byRowKey.set(listRowKey, {
            encounterId: encounterId || str(order.encounterId),
            order,
            hasPending: tab === 'pending',
          });
          continue;
        }
        if (tab === 'pending') {
          existing.hasPending = true;
        }
      }
    }
  }

  mergeGroups(pendingRes.groups ?? [], 'pending');
  mergeGroups(executedRes.groups ?? [], 'executed');

  const rows: PrintPendingRow[] = [];
  for (const { encounterId, order, hasPending } of byRowKey.values()) {
    if (!encounterId) {
      continue;
    }
    const patient = patientByEncounter(patients, encounterId);
    const meta = bedNameOf(patient);
    const listRowKey = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
    const openedAt = str(order.reqOpenedTime);
    rows.push({
      id: `${pageHint}-${listRowKey}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: str(order.adviceDisplayName) || '—',
      subtitle: str(order.performerCheckOrgDisplay) || str(order.requesterName),
      timeText: openedAt ? dayjs(openedAt).format('MM-DD HH:mm') : '',
      statusCode: hasPending ? 'pending' : 'executable',
      statusText: hasPending ? '待执行' : '已执行',
      pageHint,
      encounterId,
      patientId: str(order.patientId) || str(patient?.patientId),
      listRowKey,
      orderId: str(order.orderId),
      specimenTubeMergeDeviceId:
        preserveApiIdForRequest(order.specimenTubeMergeDeviceId) ?? undefined,
    });
  }
  return rows;
}

/** 汇总领药单 / 摆药单：已生成汇总单头（时段须 LocalDateTime：YYYY-MM-DD HH:mm:ss） */
async function loadSummarySlipPending(
  kind: 'dispense_sheet' | 'dispense_summary',
  dateFrom: string,
  dateTo: string,
): Promise<PrintPendingRow[]> {
  const res = await httpPageMedicationSummarySlipHeaders({
    pageNo: 1,
    pageSize: 200,
    startDate: `${dateFrom} 00:00:00`,
    endDate: `${dateTo} 23:59:59`,
  });
  // rows 与 PagedResult.content 同源；兼容仅回 content 的网关形态
  const headers = res.rows ?? res.content ?? [];
  return headers.map((header) => {
    const summaryNo = str(header.busNo);
    const delivery = str(header.deliveryStatusEnum);
    const statusCode = delivery === 'DELIVERED' || delivery === 'RECEIVED' ? 'submitted' : 'ready';
    return {
      id: `${kind}-${summaryNo}`,
      bedNo: '病区',
      patientName: '—',
      title: kind === 'dispense_summary' ? `汇总领药单 ${summaryNo}` : `摆药单 ${summaryNo}`,
      subtitle: str(header.purposeLocationName) || str(header.supplyStatusEnumText),
      timeText: str(header.applyTime) || str(header.dispenseTime),
      statusCode,
      statusText: str(header.deliveryStatusEnumText) || str(header.supplyStatusEnumText) || '—',
      pageHint: kind === 'dispense_summary' ? '汇总' : '摆药',
      summaryNo,
    };
  });
}

/** 护理记录待打行标题：优先病情措施，否则生命体征摘要 */
function formatNursingPendingTitle(row: NurseStationNursingRecordRowDto): string {
  const measure = str(row.conditionMeasure);
  if (measure) {
    return measure;
  }
  const vitals = [
    str(row.temperature) ? `T${str(row.temperature)}` : '',
    str(row.pulse) ? `P${str(row.pulse)}` : '',
    str(row.respiratory) ? `R${str(row.respiratory)}` : '',
    str(row.bloodPressure) ? `BP${str(row.bloodPressure)}` : '',
  ].filter(Boolean);
  if (vitals.length > 0) {
    return vitals.join(' ');
  }
  const intake = [str(row.intakeName), str(row.intakeAmount)].filter(Boolean).join(' ');
  return intake || '—';
}

/** 护理记录：区间内实际记录逐行展示；无记录则空列表 */
async function loadNursingPending(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
): Promise<PrintPendingRow[]> {
  const patient = patients[0];
  if (!patient) {
    return [];
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    return [];
  }
  const res = await httpListNurseStationNursingRecords({
    encounterId,
    patientId,
    // 后端 LocalDate（yyyy-MM-dd），勿带时分秒，否则反序列化失败 → 待打空列表
    recordedAtFrom: dateFrom,
    recordedAtTo: dateTo,
    sortAsc: true,
  });
  const meta = bedNameOf(patient);
  const rows: PrintPendingRow[] = [];
  (res.rows ?? []).forEach((row, index) => {
    const recordDate = str(row.recordDate);
    const recordTime = str(row.recordTime);
    const listRowKey = str(row.vitalSignId) || `${recordDate}-${recordTime}-${index}`;
    const timeText =
      recordDate && recordTime
        ? `${dayjs(recordDate).isValid() ? dayjs(recordDate).format('MM-DD') : recordDate} ${recordTime}`
        : recordDate || recordTime || '—';
    rows.push({
      id: `nursing-${listRowKey}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: formatNursingPendingTitle(row),
      subtitle: str(row.nurseSignature) || str(row.consciousness) || '—',
      timeText,
      statusCode: 'pending',
      statusText: '待打',
      pageHint: str(row.temperature) || '—',
      encounterId,
      patientId,
      listRowKey,
    });
  });
  return rows;
}

/** 体温单：打印中心暂不拉数（后续纯前端打印） */
async function loadTempSheetPending(
  _patients: NurseStationModulePatient[],
): Promise<PrintPendingRow[]> {
  return [];
}

/**
 * 按单据类型拉取真实待打清单，并按打印审计 enrich「已打印」。
 * 无业务数据时返回空数组（页面展示空态，不抛错）。
 */
export async function loadPrintPendingRows(
  query: LoadPrintPendingQuery,
): Promise<PrintPendingRow[]> {
  const { docType, patients, dateFrom, dateTo, executionDeadline, costStatusEnum } = query;
  const feeStatusEnum = costStatusEnum ?? 'PENDING_SETTLEMENT';

  try {
    let rows: PrintPendingRow[] = [];
    if (docType === 'patrol_card') {
      rows = await loadPatrolPending(patients, executionDeadline, dateFrom || dateTo);
    } else if (docType === 'wristband') {
      rows = buildWristbandPending(patients);
    } else if (docType === 'bed_card') {
      rows = buildBedCardPending(patients);
    } else if (docType === 'fee_list') {
      rows = await loadFeeListPending(patients, dateFrom, dateTo, feeStatusEnum);
    } else if (docType === 'fee_detail') {
      rows = await loadFeeDetailPending(patients, dateFrom, dateTo, feeStatusEnum);
    } else if (docType === 'temp_order') {
      rows = await loadOrderPending(patients, 'temp', executionDeadline, dateFrom, dateTo);
    } else if (docType === 'long_order') {
      rows = await loadOrderPending(patients, 'long', executionDeadline, dateFrom, dateTo);
    } else if (docType === 'infusion_label') {
      // dateFrom 即查询日（单日，与 dateTo 相同）
      rows = await loadInfusionPending(patients, dateFrom || dateTo);
    } else if (docType === 'oral_med_label') {
      rows = await loadOralMedPending(patients, dateFrom, dateTo);
    } else if (docType === 'exam_apply') {
      rows = await loadServiceApplyPending(
        patients,
        SERVICE_CATEGORY_TEST,
        executionDeadline,
        '检查',
        dateFrom,
        dateTo,
      );
    } else if (docType === 'lab_apply') {
      rows = await loadServiceApplyPending(
        patients,
        SERVICE_CATEGORY_PROOF,
        executionDeadline,
        '检验',
        dateFrom,
        dateTo,
      );
    } else if (docType === 'specimen_barcode') {
      // 采血贴与检验单同源（categoryEnum=22），每张贴对应一条检验申请
      rows = await loadServiceApplyPending(
        patients,
        SERVICE_CATEGORY_PROOF,
        executionDeadline,
        '采血贴',
        dateFrom,
        dateTo,
      );
    } else if (docType === 'dispense_summary') {
      rows = await loadSummarySlipPending('dispense_summary', dateFrom, dateTo);
    } else if (docType === 'dispense_sheet') {
      rows = await loadSummarySlipPending('dispense_sheet', dateFrom, dateTo);
    } else if (docType === 'order_dispense_sheet') {
      rows = await loadOrderDispenseSheetPending(patients, dateFrom || dateTo);
    } else if (docType === 'oral_med_sheet') {
      rows = await loadOralMedSheetPending(patients, dateFrom || dateTo);
    } else if (docType === 'nursing_record') {
      rows = await loadNursingPending(patients, dateFrom, dateTo);
    } else if (docType === 'temp_sheet') {
      rows = await loadTempSheetPending(patients);
    }

    await enrichPrintPendingPrintedStatus({
      docType,
      rows,
      dateFrom,
      dateTo,
    });
    return rows;
  } catch (error) {
    // 无数据/业务空结果不应打断页面；仅记录后回空列表
    console.warn('[print-center] 加载待打列表失败，按空列表处理', docType, error);
    if (docType === 'infusion_label' || docType === 'oral_med_label') {
      // 贴签依赖医嘱执行双 Tab；失败时给出提示，避免误以为「无医嘱」
      console.error('[print-center] 贴签待打列表加载异常', docType, error);
    }
    return [];
  }
}
