import type {
  ListPatientTraceAssignmentGroupsRequest,
  ListPatientTraceAssignmentGroupsResponse,
  PagePatientsAssignedTraceAssignmentRequest,
  PagePatientsAssignedTraceAssignmentResponse,
  PagePatientsPendingTraceAssignmentRequest,
  PagePatientsPendingTraceAssignmentResponse,
  PatientTraceAssignmentGroupDto,
  PatientTraceAssignmentQueueRowDto,
  ResolveTraceCodesForPatientRequest,
  ResolveTraceCodesForPatientResponse,
  RevisePatientTraceAssignmentRequest,
  RevisePatientTraceAssignmentResponse,
  SavePatientTraceAssignmentRequest,
  SavePatientTraceAssignmentResponse,
} from './inpatient-nurse-station-trace-assignment.types';

const MOCK_DELAY_MS = 120;

/** 模拟接口延迟 */
function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

/** 待分配患者队列假数据 */
const MOCK_QUEUE: PatientTraceAssignmentQueueRowDto[] = [
  {
    encounterId: '9100000000000001001',
    encounterBusNo: 'ZY202607150001',
    patientId: '9100000000000002001',
    patientName: '王小明',
    bedName: '03床',
    pendingGroupCount: 2,
  },
  {
    encounterId: '9100000000000001002',
    encounterBusNo: 'ZY202607150008',
    patientId: '9100000000000002002',
    patientName: '李秀英',
    bedName: '12床',
    pendingGroupCount: 1,
  },
  {
    encounterId: '9100000000000001003',
    encounterBusNo: 'ZY202607140022',
    patientId: '9100000000000002003',
    patientName: '赵建国',
    bedName: '05床',
    pendingGroupCount: 3,
  },
];

/** 按就诊拆分的药批汇总假数据（内存可变，供保存覆盖） */
const MOCK_GROUPS_BY_ENCOUNTER: Record<string, PatientTraceAssignmentGroupDto[]> = {
  '9100000000000001001': [
    {
      groupKey: 'g-1001-amx',
      summaryNo: 'HZ20260715001',
      medicationId: '1001',
      medicationName: '阿莫西林胶囊',
      specification: '0.25g×24粒',
      lotNumber: '20250101',
      dispenseQuantitySplit: 2,
      splitUnitCode: 'box',
      splitUnitCode_dictText: '盒',
      dispenseIds: ['9100000000000003001', '9100000000000003002'],
      assignedTraceNo: '',
      assignmentStatus: 'PENDING',
      assignmentStatus_enumText: '待分配',
      tracePoolPreview: '82345678901234567801 等 2 码',
      tracePoolCodes: ['82345678901234567801', '82345678901234567811'],
      maxTraceCodeCount: 2,
    },
    {
      groupKey: 'g-1001-ibu',
      summaryNo: 'HZ20260715001',
      medicationId: '1003',
      medicationName: '布洛芬缓释胶囊',
      specification: '0.3g×20粒',
      lotNumber: '20250308',
      dispenseQuantitySplit: 1,
      splitUnitCode: 'box',
      splitUnitCode_dictText: '盒',
      dispenseIds: ['9100000000000003003'],
      assignedTraceNo: '',
      assignmentStatus: 'PENDING',
      assignmentStatus_enumText: '待分配',
      tracePoolPreview: '82345678901234567803',
      tracePoolCodes: ['82345678901234567803'],
      maxTraceCodeCount: 1,
    },
  ],
  '9100000000000001002': [
    {
      groupKey: 'g-1002-cef',
      summaryNo: 'HZ20260715002',
      medicationId: '1002',
      medicationName: '头孢克肟分散片',
      specification: '0.1g×6片',
      lotNumber: '20250215',
      dispenseQuantitySplit: 1,
      splitUnitCode: 'box',
      splitUnitCode_dictText: '盒',
      dispenseIds: ['9100000000000003004'],
      assignedTraceNo: '',
      assignmentStatus: 'PENDING',
      assignmentStatus_enumText: '待分配',
      tracePoolPreview: '82345678901234567802',
      tracePoolCodes: ['82345678901234567802'],
      maxTraceCodeCount: 1,
    },
  ],
  '9100000000000001003': [
    {
      groupKey: 'g-1003-vit',
      summaryNo: 'HZ20260714003',
      medicationId: '1004',
      medicationName: '维生素C片',
      specification: '0.1g×100片',
      lotNumber: '20250401',
      dispenseQuantitySplit: 2,
      splitUnitCode: 'bottle',
      splitUnitCode_dictText: '瓶',
      dispenseIds: ['9100000000000003005', '9100000000000003006'],
      assignedTraceNo: '82345678901234567890,82345678901234567891',
      assignmentStatus: 'ASSIGNED',
      assignmentStatus_enumText: '已分配',
      tracePoolPreview: '82345678901234567890 等 2 码',
      tracePoolCodes: ['82345678901234567890', '82345678901234567891'],
      maxTraceCodeCount: 2,
    },
    {
      groupKey: 'g-1003-lor',
      summaryNo: 'HZ20260714003',
      medicationId: '2002',
      medicationName: '氯雷他定片',
      specification: '10mg×6片',
      lotNumber: '20250601',
      dispenseQuantitySplit: 1,
      splitUnitCode: 'box',
      splitUnitCode_dictText: '盒',
      dispenseIds: ['9100000000000003007'],
      assignedTraceNo: '',
      assignmentStatus: 'PENDING',
      assignmentStatus_enumText: '待分配',
      tracePoolPreview: '82345678901234567812',
      tracePoolCodes: ['82345678901234567812'],
      maxTraceCodeCount: 1,
    },
    {
      groupKey: 'g-1003-msm',
      summaryNo: 'HZ20260714004',
      medicationId: '2003',
      medicationName: '蒙脱石散',
      specification: '3g×10袋',
      lotNumber: '20250520',
      dispenseQuantitySplit: 1,
      splitUnitCode: 'box',
      splitUnitCode_dictText: '盒',
      dispenseIds: ['9100000000000003008'],
      assignedTraceNo: '',
      assignmentStatus: 'PENDING',
      assignmentStatus_enumText: '待分配',
      tracePoolPreview: '82345678901234567813',
      tracePoolCodes: ['82345678901234567813'],
      maxTraceCodeCount: 1,
    },
  ],
};

/** mock：分页待分配患者队列 */
export function mockPagePatientsPendingTraceAssignment(
  body: PagePatientsPendingTraceAssignmentRequest,
): Promise<PagePatientsPendingTraceAssignmentResponse> {
  const pageNo = body.pageNo ?? 1;
  const pageSize = body.pageSize ?? 20;
  const keyword = String(body.keyword ?? '')
    .trim()
    .toLowerCase();
  // 按姓名/床号/住院号过滤
  let rows = MOCK_QUEUE.filter((row) => {
    if (!keyword) {
      return true;
    }
    const hay =
      `${row.patientName ?? ''}${row.bedName ?? ''}${row.encounterBusNo ?? ''}`.toLowerCase();
    return hay.includes(keyword);
  });
  // 仅展示仍有待分配组的患者（与保存后内存态同步）
  rows = rows
    .map((row) => {
      const encounterId = String(row.encounterId ?? '');
      const groups = MOCK_GROUPS_BY_ENCOUNTER[encounterId] ?? [];
      const pendingGroupCount = groups.filter(
        (g) => g.assignmentStatus === 'PENDING' || !String(g.assignedTraceNo ?? '').trim(),
      ).length;
      return { ...row, pendingGroupCount };
    })
    .filter((row) => (row.pendingGroupCount ?? 0) > 0);

  const totalCount = rows.length;
  const start = Math.max(0, (pageNo - 1) * pageSize);
  const content = rows.slice(start, start + pageSize);
  return delay({ pageNo, pageSize, totalCount, content });
}

/** mock：分页已分配可改码患者队列 */
export function mockPagePatientsAssignedTraceAssignment(
  body: PagePatientsAssignedTraceAssignmentRequest,
): Promise<PagePatientsAssignedTraceAssignmentResponse> {
  const pageNo = body.pageNo ?? 1;
  const pageSize = body.pageSize ?? 20;
  const keyword = String(body.keyword ?? '')
    .trim()
    .toLowerCase();
  let rows = MOCK_QUEUE.filter((row) => {
    if (!keyword) {
      return true;
    }
    const hay =
      `${row.patientName ?? ''}${row.bedName ?? ''}${row.encounterBusNo ?? ''}`.toLowerCase();
    return hay.includes(keyword);
  });
  // 仅展示仍有已分配组的患者
  rows = rows
    .map((row) => {
      const encounterId = String(row.encounterId ?? '');
      const groups = MOCK_GROUPS_BY_ENCOUNTER[encounterId] ?? [];
      const assignedGroupCount = groups.filter(
        (g) => g.assignmentStatus === 'ASSIGNED' && String(g.assignedTraceNo ?? '').trim(),
      ).length;
      return { ...row, pendingGroupCount: undefined, assignedGroupCount };
    })
    .filter((row) => ((row as { assignedGroupCount?: number }).assignedGroupCount ?? 0) > 0);

  const totalCount = rows.length;
  const start = Math.max(0, (pageNo - 1) * pageSize);
  const content = rows.slice(start, start + pageSize).map((row) => ({
    encounterId: row.encounterId,
    encounterBusNo: row.encounterBusNo,
    patientId: row.patientId,
    patientName: row.patientName,
    bedName: row.bedName,
    assignedGroupCount: (row as { assignedGroupCount?: number }).assignedGroupCount,
  }));
  return delay({ pageNo, pageSize, totalCount, content });
}

/** 默认药批汇总模板（左侧选真实患者时 encounterId 不在预置表也能演示） */
const DEFAULT_MOCK_GROUPS: PatientTraceAssignmentGroupDto[] =
  MOCK_GROUPS_BY_ENCOUNTER['9100000000000001001'] ?? [];

/** 按就诊取组；未知 encounterId 时克隆默认组写入内存，保证解析/保存可续用 */
function ensureMockGroups(encounterId: string): PatientTraceAssignmentGroupDto[] {
  if (!encounterId) {
    return [];
  }
  const existing = MOCK_GROUPS_BY_ENCOUNTER[encounterId];
  if (existing) {
    return existing;
  }
  // 左侧点选患者的真实 encounterId 与预置队列 ID 不一致时，落一份独立副本
  const cloned = DEFAULT_MOCK_GROUPS.map((group) => ({
    ...group,
    groupKey: `${group.groupKey}-${encounterId}`,
    dispenseIds: (group.dispenseIds ?? []).map((id) => `${id}-${encounterId}`),
    assignedTraceNo: '',
    assignmentStatus: 'PENDING',
    assignmentStatus_enumText: '待分配',
  }));
  MOCK_GROUPS_BY_ENCOUNTER[encounterId] = cloned;
  return cloned;
}

/** mock：患者药批汇总组 */
export function mockListPatientTraceAssignmentGroups(
  body: ListPatientTraceAssignmentGroupsRequest,
): Promise<ListPatientTraceAssignmentGroupsResponse> {
  const encounterId = String(body.encounterId ?? '').trim();
  const groups = ensureMockGroups(encounterId);
  return delay({ groups: groups.map((g) => ({ ...g })) });
}

/** mock：批量解析追溯码（按码池命中汇总组） */
export function mockResolveTraceCodesForPatient(
  body: ResolveTraceCodesForPatientRequest,
): Promise<ResolveTraceCodesForPatientResponse> {
  const encounterId = String(body.encounterId ?? '').trim();
  const groups = ensureMockGroups(encounterId);
  const keySet = new Set((body.groupKeys ?? []).map((k) => String(k).trim()).filter(Boolean));
  const targetGroups = groups.filter((g) => keySet.has(String(g.groupKey ?? '').trim()));

  const matches: ResolveTraceCodesForPatientResponse['matches'] = [];
  const failItems: ResolveTraceCodesForPatientResponse['failItems'] = [];

  for (const code of body.traceCodes ?? []) {
    const trimmed = String(code ?? '').trim();
    if (!/^\d{20}$/.test(trimmed)) {
      failItems.push({ traceCode: trimmed, reasonCode: 'INVALID' });
      continue;
    }
    // 在目标汇总组码池中查找
    const hit = targetGroups.find((g) => (g.tracePoolCodes ?? []).includes(trimmed));
    if (!hit?.groupKey) {
      failItems.push({ traceCode: trimmed, reasonCode: 'NOT_IN_POOL' });
      continue;
    }
    const existing = matches.find((m) => m.groupKey === hit.groupKey);
    if (existing) {
      existing.matchedTraceCodes = [...(existing.matchedTraceCodes ?? []), trimmed];
    } else {
      matches.push({
        groupKey: hit.groupKey,
        matchedTraceCodes: [trimmed],
        medicationId: hit.medicationId,
        lotNumber: hit.lotNumber,
      });
    }
  }

  return delay({ matches, failItems });
}

/** mock：保存分配（写回内存组态） */
export function mockSavePatientTraceAssignment(
  body: SavePatientTraceAssignmentRequest,
): Promise<SavePatientTraceAssignmentResponse> {
  const encounterId = String(body.encounterId ?? '').trim();
  const groups = ensureMockGroups(encounterId);
  if (groups.length === 0) {
    return delay({ savedGroupCount: 0 });
  }
  let savedGroupCount = 0;
  for (const submit of body.groups ?? []) {
    const key = String(submit.groupKey ?? '').trim();
    const idx = groups.findIndex((g) => String(g.groupKey ?? '').trim() === key);
    if (idx < 0) {
      continue;
    }
    const prev = groups[idx];
    if (!prev) {
      continue;
    }
    const traceNo = String(submit.traceNo ?? '').trim();
    groups[idx] = {
      ...prev,
      assignedTraceNo: traceNo,
      assignmentStatus: traceNo ? 'ASSIGNED' : 'PENDING',
      assignmentStatus_enumText: traceNo ? '已分配' : '待分配',
    };
    savedGroupCount += 1;
  }
  return delay({ savedGroupCount });
}

/** mock：更正已分配追溯码 */
export function mockRevisePatientTraceAssignment(
  body: RevisePatientTraceAssignmentRequest,
): Promise<RevisePatientTraceAssignmentResponse> {
  const encounterId = String(body.encounterId ?? '').trim();
  const groups = ensureMockGroups(encounterId);
  if (groups.length === 0) {
    return delay({ revisedGroupCount: 0 });
  }
  let revisedGroupCount = 0;
  for (const submit of body.groups ?? []) {
    const key = String(submit.groupKey ?? '').trim();
    const idx = groups.findIndex((g) => String(g.groupKey ?? '').trim() === key);
    if (idx < 0) {
      continue;
    }
    const prev = groups[idx];
    if (!prev) {
      continue;
    }
    const traceNo = String(submit.traceNo ?? '').trim();
    if (!traceNo) {
      continue;
    }
    groups[idx] = {
      ...prev,
      assignedTraceNo: traceNo,
      assignmentStatus: 'ASSIGNED',
      assignmentStatus_enumText: '已分配',
    };
    revisedGroupCount += 1;
  }
  return delay({ revisedGroupCount });
}
