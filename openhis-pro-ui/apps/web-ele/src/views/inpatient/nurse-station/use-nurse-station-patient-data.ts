import type { NurseStationTaskIncreaseHint } from './nurse-station-task-notify.types';
import type {
  PatientAdmissionFilter,
  PatientItem,
  PatientTaskFilter,
  RegionItem,
  WardItem,
} from './types';

import { computed, ref } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage } from 'element-plus';

import {
  httpLoadWardTree,
  httpPagePatientQueue,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 须 `#/` 导入，院点 overrides 才能替换配置 */
import { NURSE_STATION_QUEUE_POLL } from '#/views/inpatient/nurse-station/nurse-station-queue-poll.config';

import {
  applyNurseStationBedSortKeys,
  comparePatientItemsByBedNo,
} from './services/compare-nurse-station-bed-no';
import {
  isPatientAdmittedToday,
  mapQueueItemToPatientItem,
  mapTaskFiltersToApiFilter,
  matchPatientTaskFilters,
  shouldRequestPendingExecuteCount,
  mapWardTreeToRegionOptions,
  mapWardTreeToWardOptions,
} from './services/map-inpatient-nurse-station-api';

/**
 * 队列单页条数：侧栏需全量卡片，须多页拉齐；
 * 单页过大会被任务计数 enrich 拖死超时，保持 50。
 */
const QUEUE_PAGE_SIZE = 50;
/** 待入科就诊状态 code（与 DM-ADM-002 REGISTERED 一致） */
const ENCOUNTER_STATUS_REGISTERED = 'REGISTERED';

interface UseNurseStationPatientDataOptions {
  /** 队列完整落盘后回调（用于剔除失效选中，禁止刷新回落到第一人） */
  onPatientsSettled?: () => void;
}

/** 护士站患者基础数据：病区树、队列接口、筛选与列表统计。 */
export function useNurseStationPatientData(options: UseNurseStationPatientDataOptions = {}) {
  const { onPatientsSettled } = options;
  const regionOptions = ref<RegionItem[]>([]);
  const wardOptions = ref<WardItem[]>([]);
  const patients = ref<PatientItem[]>([]);
  const keyword = ref('');
  /** 任务多选；空=全部任务 */
  const statusFilters = ref<PatientTaskFilter[]>([]);
  /** 入科状态筛选（默认在科；切换会重拉队列，待入科避开 houseId 占床收窄）。 */
  const admissionFilter = ref<PatientAdmissionFilter>('in-ward');
  /** 仅看今日入院；与在科/任务筛选 AND，本地过滤不重拉队列。 */
  const todayAdmissionOnly = ref(false);
  const selectedRegionIds = ref<string[]>([]);
  const selectedWardIds = ref<string[]>([]);
  /** 病区/病房筛选区是否展开。 */
  const filterPanelExpanded = ref(true);
  const queueLoading = ref(false);
  const statusOptions: Array<{ label: string; value: PatientTaskFilter }> = [
    { label: '待校对', value: 'pending-audit' },
    { label: '待执行', value: 'pending-execute' },
  ];
  /** 在科 / 待入科：互斥；在科=队列内未清床且非 REGISTERED。 */
  const admissionOptions: Array<{ label: string; value: PatientAdmissionFilter }> = [
    { label: '在科', value: 'in-ward' },
    { label: '待入科', value: 'pending-admission' },
  ];
  const regionMap = computed(() => new Map(regionOptions.value.map((i) => [i.id, i.name])));
  /** 患者列表展示：病区 id 到名称的快速映射。 */
  const regionNameByIdRecord = computed(() => Object.fromEntries(regionMap.value));
  const wardMap = computed(() => new Map(wardOptions.value.map((i) => [i.id, i.name])));
  /** 床位图：病房 id → 名称（传给入出转模块）。 */
  const wardNameByIdRecord = computed(() => Object.fromEntries(wardMap.value));
  const availableWards = computed(() =>
    wardOptions.value.filter((item) => selectedRegionIds.value.includes(item.regionId)),
  );
  /** 床位图展示的病房范围（未选手动病房时用当前病区下全部病房）。 */
  const bedMapWardIds = computed(() =>
    selectedWardIds.value.length > 0
      ? selectedWardIds.value
      : availableWards.value.map((w) => w.id),
  );
  const totalPatientCount = computed(() => filteredPatients.value.length);
  const pendingAuditCount = computed(() =>
    filteredPatients.value.reduce((sum, item) => sum + item.pendingAudit, 0),
  );
  const pendingExecuteCount = computed(() =>
    filteredPatients.value.reduce((sum, item) => sum + item.pendingExecute, 0),
  );

  const filteredPatients = computed(() => {
    const regionIds = selectedRegionIds.value;
    const wardIds = selectedWardIds.value;
    const admission = admissionFilter.value;
    const taskFilters = statusFilters.value;
    const todayOnly = todayAdmissionOnly.value;
    const list = patients.value.filter((item) => {
      if (!item.regionId) {
        return false;
      }
      // 待入科：仅 REGISTERED（未分床）
      if (admission === 'pending-admission' && item.statusEnum !== ENCOUNTER_STATUS_REGISTERED) {
        return false;
      }
      // 在科：未清床且已入科轨（排除 REGISTERED；COMPLETED 待清床仍算在科）
      if (admission === 'in-ward' && item.statusEnum === ENCOUNTER_STATUS_REGISTERED) {
        return false;
      }
      if (todayOnly && !isPatientAdmittedToday(item)) {
        return false;
      }
      const matchRegion = regionIds.length === 0 || regionIds.includes(item.regionId);
      const matchWard = wardIds.length === 0 || !item.wardId || wardIds.includes(item.wardId);
      if (!matchRegion || !matchWard) {
        return false;
      }
      // 任务多选：空=全部；多项 OR（单选时后端已收窄，再滤一次无害）
      return matchPatientTaskFilters(item, taskFilters);
    });
    // 展示层按预计算床号键排序；不改 patients 源序，静默轮询 sameMembership 不变
    list.sort(comparePatientItemsByBedNo);
    return list;
  });

  /**
   * 解析队列请求的地理范围：单选病房优先，其次单选病区。
   * 待入科未分床：禁止传 houseId（后端按占床收窄会丢掉 REGISTERED），升到所属病区或管辖并集。
   */
  function resolveQueueLocationParams(): { houseId?: string; wardId?: string } {
    const pendingAdmission = admissionFilter.value === 'pending-admission';
    if (!pendingAdmission && selectedWardIds.value.length === 1) {
      return { houseId: selectedWardIds.value[0] };
    }
    if (pendingAdmission && selectedWardIds.value.length === 1) {
      const house = wardOptions.value.find((item) => item.id === selectedWardIds.value[0]);
      const regionId = (house?.regionId ?? '').trim();
      if (regionId) {
        return { wardId: regionId };
      }
    }
    if (selectedWardIds.value.length === 0 && selectedRegionIds.value.length === 1) {
      return { wardId: selectedRegionIds.value[0] };
    }
    return {};
  }

  /** 拉取病区病房树并初始化默认勾选（不拉队列）。 */
  async function loadWardTree() {
    const res = await httpLoadWardTree({});
    const wardNodes = res.wardNodes ?? [];
    regionOptions.value = mapWardTreeToRegionOptions(wardNodes);
    wardOptions.value = mapWardTreeToWardOptions(wardNodes);
    selectedRegionIds.value = regionOptions.value.map((item) => item.id);
    selectedWardIds.value = [];
  }

  /**
   * 按页拉取在院患者队列直至取完；每页回写以便首屏尽早可见。
   * 后续页失败时保留已拉到的行，避免整表空白。
   *
   * @param onPage 每页累计行回调（首屏渐进展示）
   * @param options.silentPoll 静默轮询：带静默头、失败不 toast、不渐进回写
   */
  async function fetchAllPatientQueueRows(
    onPage?: (accumulated: PatientItem[]) => void,
    options?: { silentPoll?: boolean },
  ): Promise<PatientItem[]> {
    const silentPoll = options?.silentPoll === true;
    const locationParams = resolveQueueLocationParams();
    const taskFilters = statusFilters.value;
    const taskFilter = mapTaskFiltersToApiFilter(taskFilters);
    // 首屏/手动刷新：仅在勾了待执行等筛选时算「执」（与后端默认一致，极轻）
    // 静默轮询：国基开启时带执计数，回填气泡；不挡首屏
    const needExecuteCount = silentPoll
      ? NURSE_STATION_QUEUE_POLL.enabled || shouldRequestPendingExecuteCount(taskFilters)
      : shouldRequestPendingExecuteCount(taskFilters);
    const requestBase = {
      ...locationParams,
      includePendingExecuteCount: needExecuteCount ? true : undefined,
      keyword: keyword.value.trim() || undefined,
      nurseStationTaskFilter: taskFilter,
      pageSize: QUEUE_PAGE_SIZE,
    };
    const pollOpts = silentPoll ? { silentPoll: true } : undefined;

    // 先拉首页拿 totalCount，并尽早回写首屏
    const firstRes = await httpPagePatientQueue({ ...requestBase, pageNo: 1 }, pollOpts);
    const firstParsed = parsePagedRowsFromApiData(firstRes);
    const rows: PatientItem[] = firstParsed.content.map((row) =>
      mapQueueItemToPatientItem(row, wardOptions.value),
    );
    const totalCount = firstParsed.totalCount;
    if (!silentPoll) {
      onPage?.(rows.slice());
    }
    if (rows.length >= totalCount || firstParsed.content.length === 0) {
      return rows;
    }

    // 其余页并行拉取（页序不变），缩短多页串行墙钟时间；结果口径不变
    const totalPages = Math.ceil(totalCount / QUEUE_PAGE_SIZE);
    const pageNos = Array.from({ length: totalPages - 1 }, (_, i) => i + 2);
    try {
      const restPages = await Promise.all(
        pageNos.map((pageNo) => httpPagePatientQueue({ ...requestBase, pageNo }, pollOpts)),
      );
      for (const res of restPages) {
        const parsed = parsePagedRowsFromApiData(res);
        for (const row of parsed.content) {
          rows.push(mapQueueItemToPatientItem(row, wardOptions.value));
        }
        if (!silentPoll) {
          onPage?.(rows.slice());
        }
      }
    } catch (error) {
      if (silentPoll) {
        throw error;
      }
      // 后续页失败保留已拉到的行
      ElMessage.warning('患者队列未全部加载，可点击刷新重试');
    }
    return rows;
  }

  /** 加载在院患者队列（带 loading；多页时首页回写后继续拉）。 */
  async function loadPatientQueue() {
    queueLoading.value = true;
    try {
      const rows = await fetchAllPatientQueueRows((accumulated) => {
        patients.value = accumulated;
      });
      patients.value = rows;
      onPatientsSettled?.();
    } catch {
      ElMessage.error('加载患者队列失败，请稍后重试');
    } finally {
      queueLoading.value = false;
    }
  }

  /** 对比轮询前后「校/执」上升，供浮层提示（含新入列且已有待办）。 */
  function collectTaskIncreases(
    prevById: Map<string, PatientItem>,
    nextRows: PatientItem[],
  ): NurseStationTaskIncreaseHint[] {
    const hints: NurseStationTaskIncreaseHint[] = [];
    for (const next of nextRows) {
      const prev = prevById.get(next.id);
      const prevAudit = prev?.pendingAudit ?? 0;
      const prevExecute = prev?.pendingExecute ?? 0;
      const auditIncreased = next.pendingAudit > prevAudit;
      const executeIncreased = next.pendingExecute > prevExecute;
      if (!auditIncreased && !executeIncreased) {
        continue;
      }
      hints.push({
        encounterId: next.id,
        patientName: next.name,
        bedNo: next.bedNo,
        auditIncreased,
        executeIncreased,
        pendingAudit: next.pendingAudit,
        pendingExecute: next.pendingExecute,
      });
    }
    return hints;
  }

  /**
   * 将静默轮询结果合并进现有队列：优先原地改「校/执」，避免右侧业务区因 patients 换引用误重拉。
   * 人选集合变化时重建列表，但复用已有患者对象引用。
   *
   * @returns 待校/待执数量上升的患者，供浮层提示
   */
  function applySilentPatientQueuePatch(nextRows: PatientItem[]): NurseStationTaskIncreaseHint[] {
    const prevList = patients.value;
    const prevById = new Map(prevList.map((item) => [item.id, item]));
    const increases = collectTaskIncreases(prevById, nextRows);
    const sameMembership =
      prevList.length === nextRows.length &&
      prevList.every((item, index) => item.id === nextRows[index]?.id);

    if (sameMembership) {
      for (let i = 0; i < nextRows.length; i += 1) {
        const prev = prevList[i];
        const next = nextRows[i];
        if (!prev || !next) {
          continue;
        }
        // 左侧气泡/卡片任务数；不碰 selected（由 selection 维护）
        prev.pendingAudit = next.pendingAudit;
        prev.pendingExecute = next.pendingExecute;
        prev.taskTag = next.taskTag;
        prev.riskText = next.riskText;
        prev.accountBalance = next.accountBalance;
        prev.accountBalanceDisplayMode = next.accountBalanceDisplayMode;
        prev.accountBalanceSummaryText = next.accountBalanceSummaryText;
        prev.accountBalanceSubText = next.accountBalanceSubText;
        prev.availableBalance = next.availableBalance;
        prev.pendingSettlementTotal = next.pendingSettlementTotal;
        prev.lowBalanceWarning = next.lowBalanceWarning;
        prev.statusEnum = next.statusEnum;
        prev.statusEnumText = next.statusEnumText;
        if (prev.bedNo !== next.bedNo) {
          prev.bedNo = next.bedNo;
          applyNurseStationBedSortKeys(prev);
        }
        prev.bedLocationId = next.bedLocationId;
        prev.level = next.level;
        prev.admissionTime = next.admissionTime;
        prev.startTime = next.startTime;
      }
      return increases;
    }

    patients.value = nextRows.map((row) => {
      const existing = prevById.get(row.id);
      if (!existing) {
        return row;
      }
      existing.pendingAudit = row.pendingAudit;
      existing.pendingExecute = row.pendingExecute;
      existing.taskTag = row.taskTag;
      existing.riskText = row.riskText;
      existing.accountBalance = row.accountBalance;
      existing.accountBalanceDisplayMode = row.accountBalanceDisplayMode;
      existing.accountBalanceSummaryText = row.accountBalanceSummaryText;
      existing.accountBalanceSubText = row.accountBalanceSubText;
      existing.availableBalance = row.availableBalance;
      existing.pendingSettlementTotal = row.pendingSettlementTotal;
      existing.lowBalanceWarning = row.lowBalanceWarning;
      existing.statusEnum = row.statusEnum;
      existing.statusEnumText = row.statusEnumText;
      if (existing.bedNo !== row.bedNo) {
        existing.bedNo = row.bedNo;
        applyNurseStationBedSortKeys(existing);
      }
      existing.bedLocationId = row.bedLocationId;
      existing.level = row.level;
      existing.admissionTime = row.admissionTime;
      existing.startTime = row.startTime;
      existing.name = row.name;
      existing.diagnosis = row.diagnosis;
      existing.wardId = row.wardId;
      existing.regionId = row.regionId;
      existing.houseName = row.houseName;
      existing.wardName = row.wardName;
      return existing;
    });
    return increases;
  }

  /**
   * 静默重拉当前筛选下的队列（无 loading、无 toast）。
   * 选中 id 由 selection 侧 sync 保留；右侧列表不因校/执变化而重拉。
   *
   * @returns 待校/待执上升提示；失败或跳过时为空数组
   */
  async function silentReloadPatientQueue(): Promise<NurseStationTaskIncreaseHint[]> {
    if (queueLoading.value) {
      return [];
    }
    try {
      const rows = await fetchAllPatientQueueRows(undefined, { silentPoll: true });
      const increases = applySilentPatientQueuePatch(rows);
      onPatientsSettled?.();
      return increases;
    } catch {
      // 轮询失败静默忽略，保留上一帧队列与选中
      return [];
    }
  }

  const debouncedLoadPatientQueue = useDebounceFn(() => {
    void loadPatientQueue();
  }, 350);

  /** 首次进入工作台：先树后队列（各一次请求）。 */
  async function bootstrapPatientWorkbench() {
    queueLoading.value = true;
    try {
      await loadWardTree();
      await loadPatientQueue();
    } catch {
      ElMessage.error('加载护士站患者数据失败，请稍后重试');
      patients.value = [];
    } finally {
      queueLoading.value = false;
    }
  }

  /** 病区变更后，清理无效病房选中项。 */
  function syncWardSelectionByRegion() {
    const validWardIds = new Set(availableWards.value.map((item) => item.id));
    selectedWardIds.value = selectedWardIds.value.filter((id) => validWardIds.has(id));
  }

  /** 搜索词变更（防抖后拉队列）。 */
  function changeKeyword(value: string) {
    keyword.value = value;
    debouncedLoadPatientQueue();
  }

  /** 任务多选变更（空=全部任务）。 */
  function changeStatusFilters(value: PatientTaskFilter[]) {
    statusFilters.value = Array.isArray(value) ? value : [];
    void loadPatientQueue();
  }

  /**
   * 入科状态筛选变更后重拉队列。
   * 待入科须避开 houseId 占床收窄（见 resolveQueueLocationParams），不能只做本地过滤。
   */
  function changeAdmissionFilter(value: PatientAdmissionFilter) {
    admissionFilter.value = value;
    void loadPatientQueue();
  }

  /** 切换今日入院筛选（入院时刻已在队列行上，无需重拉）。 */
  function changeTodayAdmissionOnly(value: boolean) {
    todayAdmissionOnly.value = value === true;
  }

  /** 病区勾选变更。 */
  function changeRegionIds(value: string[]) {
    selectedRegionIds.value = value;
    syncWardSelectionByRegion();
    void loadPatientQueue();
  }

  /** 病房勾选变更。 */
  function changeWardIds(value: string[]) {
    selectedWardIds.value = value;
    void loadPatientQueue();
  }

  /** 重置搜索/状态筛选并拉取队列；保留当前病区、病房勾选。 */
  async function refreshPatientFilters() {
    keyword.value = '';
    statusFilters.value = [];
    admissionFilter.value = 'in-ward';
    todayAdmissionOnly.value = false;
    await loadPatientQueue();
  }

  /** 切换病区/病房筛选区展开或收起。 */
  function toggleFilterPanel() {
    filterPanelExpanded.value = !filterPanelExpanded.value;
  }

  /** 患者列表面板上下文中的数据与筛选动作。 */
  const patientListPanelDataContext = {
    admissionFilter,
    admissionOptions,
    availableWards,
    changeAdmissionFilter,
    changeKeyword,
    changeRegionIds,
    changeStatusFilters,
    changeTodayAdmissionOnly,
    changeWardIds,
    filterPanelExpanded,
    filteredPatients,
    keyword,
    pendingAuditCount,
    pendingExecuteCount,
    queueLoading,
    refreshPatientFilters,
    regionNameByIdRecord,
    regionOptions,
    selectedRegionIds,
    selectedWardIds,
    statusFilters,
    statusOptions,
    todayAdmissionOnly,
    toggleFilterPanel,
    totalPatientCount,
    wardNameByIdRecord,
  };

  return {
    admissionFilter,
    admissionOptions,
    availableWards,
    bedMapWardIds,
    bootstrapPatientWorkbench,
    changeAdmissionFilter,
    changeKeyword,
    changeRegionIds,
    changeStatusFilters,
    changeTodayAdmissionOnly,
    changeWardIds,
    filterPanelExpanded,
    filteredPatients,
    keyword,
    loadPatientQueue,
    patients,
    pendingAuditCount,
    pendingExecuteCount,
    patientListPanelDataContext,
    queueLoading,
    silentReloadPatientQueue,
    regionNameByIdRecord,
    regionOptions,
    refreshPatientFilters,
    selectedRegionIds,
    selectedWardIds,
    statusFilters,
    statusOptions,
    todayAdmissionOnly,
    syncWardSelectionByRegion,
    totalPatientCount,
    toggleFilterPanel,
    wardMap,
    wardNameByIdRecord,
    wardOptions,
  };
}
