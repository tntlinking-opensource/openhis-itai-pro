import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import type { NurseStationOrderExecuteRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import dayjs from 'dayjs';

import {
  httpPageMedicationSummarySlipHeaders,
  httpPagePendingDispenseSummaryLines,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.http';
import { httpListNurseStationNursingRecords } from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import { httpListNurseStationOrderExecute } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import {
  INFUSION_METHOD_CODES,
  METHOD_CODE_IV_INJECTION,
  ORAL_METHOD_CODES,
  normalizeMedicationMethodCode,
} from '#/utils/print/constants/medication-method-codes';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
} from '#/utils/print/constants/service-request-category-codes';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export interface LoadPrintPendingQuery {
  docType: PrintDocType;
  patients: NurseStationModulePatient[];
  dateFrom: string;
  dateTo: string;
  /** 医嘱截止时间 YYYY-MM-DD HH:mm:ss */
  executionDeadline: string;
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
 * 输液巡视卡待打：待汇总明细中有输液途径药品的患者各一行。
 * 无输液药品 → 空列表（不可打）。
 */
async function loadPatrolPending(
  patients: NurseStationModulePatient[],
  executionDeadline: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length === 0) {
    return [];
  }

  const res = await httpPagePendingDispenseSummaryLines({
    encounterIds,
    medicineCategoryTab: 'west',
    therapyFilter: 'all',
    queryDeadline: executionDeadline,
    pageNo: 1,
    pageSize: 500,
  });

  const rows: PrintPendingRow[] = [];
  for (const group of res.patientGroups ?? []) {
    const encounterId = str(group.encounterId);
    if (!encounterId) {
      continue;
    }
    // 仅保留输液途径药品，按 groupId / medReqId 成组计数
    const infusionOrders = (group.orderRows ?? []).filter((order) =>
      isInfusionMethodCode(order.methodCode),
    );
    if (infusionOrders.length <= 0) {
      continue;
    }
    const groupKeys = new Set<string>();
    for (const order of infusionOrders) {
      groupKeys.add(str(order.groupId) || `r:${str(order.medReqId)}`);
    }
    const drugNames = infusionOrders.map((order) => str(order.medicationName)).filter(Boolean);
    const namePreview =
      drugNames.length > 0
        ? `${drugNames.slice(0, 2).join('、')}${drugNames.length > 2 ? '…' : ''}`
        : `${infusionOrders.length} 种药`;
    const patient = patientByEncounter(patients, encounterId);
    rows.push({
      id: `patrol-${encounterId}`,
      bedNo: str(group.bedDisplay) || bedNameOf(patient).bedNo,
      patientName: str(group.patientName) || bedNameOf(patient).patientName,
      title: '输液巡视卡',
      subtitle: `共 ${groupKeys.size} 组 · ${namePreview}`,
      timeText: dayjs().format('YYYY-MM-DD'),
      statusCode: 'pending',
      statusText: '待打',
      pageHint: `${groupKeys.size} 组`,
      encounterId,
      patientId: str(group.patientId) || str(patient?.patientId),
    });
  }
  return rows;
}

/** 腕带：每位选中患者一行 */
function buildWristbandPending(patients: NurseStationModulePatient[]): PrintPendingRow[] {
  return patients.map((patient) => {
    const meta = bedNameOf(patient);
    return {
      id: `wristband-${str(patient.id)}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: '住院腕带',
      subtitle: str(patient.encounterBusNo) || str(patient.wardName),
      timeText: dayjs().format('YYYY-MM-DD'),
      statusCode: 'pending' as const,
      statusText: '待打',
      pageHint: '腕带',
      encounterId: str(patient.id),
      patientId: str(patient.patientId),
    };
  });
}

/** 费用清单：当前日期区间一行 */
function buildFeeListPending(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
): PrintPendingRow[] {
  const patient = patients[0];
  if (!patient) {
    return [];
  }
  const meta = bedNameOf(patient);
  return [
    {
      id: `fee-${str(patient.id)}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: '住院费用清单',
      subtitle: `${dateFrom} ~ ${dateTo}`,
      timeText: dayjs().format('YYYY-MM-DD'),
      statusCode: 'pending',
      statusText: '待打',
      pageHint: '清单',
      encounterId: str(patient.id),
      patientId: str(patient.patientId ?? patient.id),
    },
  ];
}

/** 医嘱执行列表 → 待打行（仅已执行；可按执行日区间过滤槽位） */
function mapOrderExecuteToPending(
  groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
  patients: NurseStationModulePatient[],
  therapyLabel: string,
  dateFrom?: string,
  dateTo?: string,
): PrintPendingRow[] {
  const rows: PrintPendingRow[] = [];
  const fromDay = str(dateFrom);
  const toDay = str(dateTo);
  for (const group of groups) {
    const encounterId = str(group.encounterId);
    const patient = patientByEncounter(patients, encounterId);
    const meta = bedNameOf(patient);
    for (const order of group.orders ?? []) {
      const listRowKey = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
      const executedSlots = order.executedSlots ?? [];
      // 有区间时只保留落在执行日内的槽；无区间则整单已执行槽
      const slotsInRange =
        fromDay && toDay
          ? executedSlots.filter((slot) => {
              const at = str(slot.recordedTime) || str(slot.planTime);
              if (!at) {
                return false;
              }
              const day = dayjs(at).isValid() ? dayjs(at).format('YYYY-MM-DD') : '';
              return Boolean(day) && day >= fromDay && day <= toDay;
            })
          : executedSlots;
      if (slotsInRange.length <= 0) {
        continue;
      }
      // 时间列优先展示区间内最近一次执行时刻
      const latestSlot = slotsInRange[slotsInRange.length - 1];
      const latestAt = str(latestSlot?.recordedTime) || str(latestSlot?.planTime);
      rows.push({
        id: listRowKey,
        bedNo: meta.bedNo,
        patientName: meta.patientName,
        title: str(order.adviceDisplayName) || '—',
        subtitle: therapyLabel,
        timeText: latestAt ? dayjs(latestAt).format('MM-DD HH:mm') : str(order.reqOpenedTime),
        statusCode: 'executable',
        statusText: '已执行',
        pageHint: `${slotsInRange.length} 次执行`,
        encounterId,
        patientId: str(order.patientId) || str(patient?.patientId),
        listRowKey,
        orderId: str(order.orderId),
        groupId: str(order.groupId),
      });
    }
  }
  return rows;
}

/** 加载临嘱/长嘱待打：只拉已执行列表（可按执行日区间收窄） */
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
  const executedRes = await httpListNurseStationOrderExecute({
    encounterIds,
    therapyType,
    executionDeadline,
    executeTab: 'executed',
  });
  const rangeTo =
    str(dateTo) || (executionDeadline ? dayjs(executionDeadline).format('YYYY-MM-DD') : '');
  return mapOrderExecuteToPending(
    executedRes.groups ?? [],
    patients,
    therapyType === 'temp' ? '临时' : '长期',
    str(dateFrom) || undefined,
    rangeTo || undefined,
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
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'executed' }),
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
    });
  }
  return rows;
}

/** 口服药贴：每位患者每条口服医嘱一张贴 */
async function loadOralMedPending(
  patients: NurseStationModulePatient[],
  executionDeadline: string,
): Promise<PrintPendingRow[]> {
  if (patients.length <= 0) {
    return [];
  }
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length <= 0) {
    return [];
  }

  const res = await httpPagePendingDispenseSummaryLines({
    encounterIds,
    medicineCategoryTab: 'west',
    therapyFilter: 'all',
    queryDeadline: executionDeadline,
    pageNo: 1,
    pageSize: 500,
  });

  const rows: PrintPendingRow[] = [];
  for (const group of res.patientGroups ?? []) {
    const encounterId = str(group.encounterId);
    const patient = patientByEncounter(patients, encounterId);
    const meta = {
      bedNo: str(group.bedDisplay) || bedNameOf(patient).bedNo,
      patientName: str(group.patientName) || bedNameOf(patient).patientName,
    };
    for (const order of group.orderRows ?? []) {
      if (!isOralMethodCode(order.methodCode)) {
        continue;
      }
      const medReqId = str(order.medReqId);
      const dose =
        str(order.dosageInstruction) ||
        [
          str(order.timePoints?.[0]?.quantity),
          str(order.timePoints?.[0]?.unitCodeText) || str(order.timePoints?.[0]?.unitCode),
          str(order.frequencyCodeText),
        ]
          .filter(Boolean)
          .join(' ');
      rows.push({
        id: `oral-${encounterId}-${medReqId || str(order.medicationName)}`,
        bedNo: meta.bedNo,
        patientName: meta.patientName,
        title: str(order.medicationName) || '口服药',
        subtitle: [str(order.specificationText), dose].filter(Boolean).join(' · '),
        timeText: str(order.timePoints?.[0]?.plannedDispenseTime),
        statusCode: 'pending',
        statusText: '待打',
        pageHint: '药贴',
        encounterId,
        patientId: str(group.patientId) || str(patient?.patientId),
        orderId: medReqId,
        groupId: str(order.groupId),
      });
    }
  }
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
 * categoryEnum：检查=23，检验=22。
 */
async function loadServiceApplyPending(
  patients: NurseStationModulePatient[],
  categoryCode: string,
  executionDeadline: string,
  pageHint: string,
): Promise<PrintPendingRow[]> {
  const encounterIds = patients
    .map((p) => preserveApiIdForRequest(p.id))
    .filter((id): id is string => Boolean(id));
  if (encounterIds.length <= 0) {
    return [];
  }
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'executed' }),
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

/** 护理记录：选中患者即可打（区间内无记录时仍出空表纸单） */
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
  const rowCount = res.rows?.length ?? 0;
  const meta = bedNameOf(patient);
  return [
    {
      id: `nursing-${encounterId}`,
      bedNo: meta.bedNo,
      patientName: meta.patientName,
      title: '一般护理记录单',
      subtitle: `${dateFrom} ~ ${dateTo}`,
      timeText: dayjs().format('YYYY-MM-DD'),
      statusCode: 'pending',
      statusText: rowCount > 0 ? '待打' : '无记录',
      pageHint: rowCount > 0 ? `${rowCount} 行` : '空表',
      encounterId,
      patientId,
    },
  ];
}

/** 体温单：打印中心暂不拉数（后续纯前端打印） */
async function loadTempSheetPending(
  _patients: NurseStationModulePatient[],
): Promise<PrintPendingRow[]> {
  return [];
}

/**
 * 按单据类型拉取真实待打清单。
 * 无业务数据时返回空数组（页面展示空态，不抛错）。
 */
export async function loadPrintPendingRows(
  query: LoadPrintPendingQuery,
): Promise<PrintPendingRow[]> {
  const { docType, patients, dateFrom, dateTo, executionDeadline } = query;

  try {
    if (docType === 'patrol_card') {
      return await loadPatrolPending(patients, executionDeadline);
    }
    if (docType === 'wristband') {
      return buildWristbandPending(patients);
    }
    if (docType === 'fee_list') {
      return buildFeeListPending(patients, dateFrom, dateTo);
    }
    if (docType === 'temp_order') {
      return await loadOrderPending(patients, 'temp', executionDeadline, dateFrom, dateTo);
    }
    if (docType === 'long_order') {
      return await loadOrderPending(patients, 'long', executionDeadline, dateFrom, dateTo);
    }
    if (docType === 'infusion_label') {
      // dateFrom 即查询日（单日，与 dateTo 相同）
      return await loadInfusionPending(patients, dateFrom || dateTo);
    }
    if (docType === 'oral_med_label') {
      return await loadOralMedPending(patients, executionDeadline);
    }
    if (docType === 'exam_apply') {
      return await loadServiceApplyPending(
        patients,
        SERVICE_CATEGORY_TEST,
        executionDeadline,
        '检查',
      );
    }
    if (docType === 'lab_apply') {
      return await loadServiceApplyPending(
        patients,
        SERVICE_CATEGORY_PROOF,
        executionDeadline,
        '检验',
      );
    }
    if (docType === 'dispense_summary') {
      return await loadSummarySlipPending('dispense_summary', dateFrom, dateTo);
    }
    if (docType === 'dispense_sheet') {
      return await loadSummarySlipPending('dispense_sheet', dateFrom, dateTo);
    }
    if (docType === 'nursing_record') {
      return await loadNursingPending(patients, dateFrom, dateTo);
    }
    if (docType === 'temp_sheet') {
      return await loadTempSheetPending(patients);
    }
    return [];
  } catch (error) {
    // 无数据/业务空结果不应打断页面；仅记录后回空列表
    console.warn('[print-center] 加载待打列表失败，按空列表处理', docType, error);
    if (docType === 'infusion_label') {
      // 输液贴依赖医嘱执行双 Tab；失败时给出提示，避免误以为「无医嘱」
      console.error('[print-center] 输液贴待打列表加载异常', error);
    }
    return [];
  }
}
