import type {
  MedicalTechEncounterScope,
  MedicalTechExecutionQueueFilter,
  MedicalTechInpatientStayFilter,
  MedicalTechPatientQueueItemDto,
  PageMedicalTechPatientQueueRequest,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { Ref } from 'vue';

import { ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { httpPageMedicalTechPatientQueue } from '#/api/medical-tech/medical-tech-execution.http';
import {
  formatLocalDateYmd,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { isExpectedEmptyResponseError } from '#/utils/http/expected-empty-response-errors';
import { extractRequestErrorMessage } from '#/utils/http/stale-cross-role-error';

/** 后端单页上限 100，按日期拉全量时逐页拼完 */
const QUEUE_FETCH_PAGE_SIZE = 100;
/** 防止 total 异常时死循环 */
const QUEUE_FETCH_MAX_PAGES = 30;

export interface UseMedicalTechQueueOptions {
  encounterScope: Ref<MedicalTechEncounterScope>;
  selectedEncounterId: Ref<string>;
  selectedPatientSummary: Ref<MedicalTechPatientQueueItemDto | null>;
  /** 强制重载右侧页签（执行单/费用清单/计费）；同患者再点或刷新时调用 */
  bumpContentReload: () => void;
}

/**
 * 医技工作站壳层患者队列：按日期一次拉全量，不在侧栏翻页。
 * 非 TECH 科室由全局拦截器静默，列表保持空。
 */
export function useMedicalTechQueue(options: UseMedicalTechQueueOptions) {
  const { encounterScope, selectedEncounterId, selectedPatientSummary, bumpContentReload } =
    options;

  const queueKeyword = ref('');
  // 默认当天：对齐门诊护士站，避免无日期时扫出全库数百条
  const todayYmd = formatLocalDateYmd();
  const queueDateRange = ref<[string, string] | null>([todayYmd, todayYmd]);
  const pendingDeviceOnly = ref(false);
  /** 队列执行态：默认待执行（门诊看项目+耗材，住院仅项目） */
  const executionQueueFilter = ref<MedicalTechExecutionQueueFilter>('PENDING');
  /** 住院在院态：默认在院（与历史队列同口径） */
  const inpatientStayFilter = ref<MedicalTechInpatientStayFilter>('IN_HOSPITAL');
  const queueRows = ref<MedicalTechPatientQueueItemDto[]>([]);
  const queueLoading = ref(false);
  /**
   * 队列拉取代数：门诊/住院切换与刷新可能并发，只采纳最后一次结果，
   * 避免慢请求晚到把当前列表覆盖成空。
   */
  let queueFetchGeneration = 0;

  /** 按当前筛选拉取队列；失败保留已有列表，禁止「一刷全没」。 */
  async function loadPatientQueue() {
    const generation = ++queueFetchGeneration;
    // 住院不传就诊开始时间（在院跨日）；仅门诊按日期窗收窄
    const isOutpatient = encounterScope.value === 'OUTPATIENT';
    const outpatientDateTimes = isOutpatient
      ? resolveOutpatientQueueDateTimes(queueDateRange.value)
      : undefined;
    if (isOutpatient && (!outpatientDateTimes?.startDate || !outpatientDateTimes.endDate)) {
      ElMessage.warning('请选择有效的就诊日期范围');
      if (generation === queueFetchGeneration) {
        queueLoading.value = false;
      }
      return;
    }
    // 在院态筛选仅住院提交；门诊不传
    const stayFilter = isOutpatient ? undefined : inpatientStayFilter.value;
    queueLoading.value = true;
    try {
      const rows = await fetchQueueForDate({
        encounterScope: encounterScope.value,
        keyword: queueKeyword.value.trim() || undefined,
        pendingDeviceOnly: pendingDeviceOnly.value || undefined,
        executionQueueFilter: executionQueueFilter.value,
        inpatientStayFilter: stayFilter,
        startDate: outpatientDateTimes?.startDate,
        endDate: outpatientDateTimes?.endDate,
      });
      // 已有更新的拉取：丢弃本趟结果，避免门诊空窗覆盖住院队列
      if (generation !== queueFetchGeneration) {
        return;
      }
      queueRows.value = rows;
      // 划价/冲销后重拉：同步刷新当前选中行余额等读模型（找不到则保留选中，避免刷新误清）
      const selectedId = preserveApiIdForRequest(selectedEncounterId.value);
      if (selectedId) {
        const refreshed = queueRows.value.find(
          (row) => (preserveApiIdForRequest(row.encounterId) ?? '') === selectedId,
        );
        if (refreshed) {
          selectedPatientSummary.value = refreshed;
        }
      }
    } catch (error) {
      if (generation !== queueFetchGeneration) {
        return;
      }
      // 失败保留原列表。非 TECH 也要明确提示（顶栏科室名可能与会话不一致，静默会导致「一刷全没」）
      if (isExpectedEmptyResponseError(error)) {
        ElMessage.warning(
          extractRequestErrorMessage(error) || '当前登录科室不是医技科，请切换到检验科等医技科室后重试',
        );
        return;
      }
      ElMessage.error('加载患者队列失败，请稍后重试');
    } finally {
      if (generation === queueFetchGeneration) {
        queueLoading.value = false;
      }
    }
  }

  /** 写入共享选中就诊与患者摘要 */
  function applySelectedPatient(row: MedicalTechPatientQueueItemDto | null) {
    selectedPatientSummary.value = row;
    if (!row) {
      selectedEncounterId.value = '';
      return;
    }
    const id = preserveApiIdForRequest(row.encounterId) ?? '';
    selectedEncounterId.value = id;
  }

  /** 点选队列患者：写入共享上下文；再点同一患者仍强制重载右侧（避免 id 不变 watch 不触发） */
  function handleSelectPatient(payload: { id: string }) {
    const id = preserveApiIdForRequest(payload.id);
    if (!id) {
      ElMessage.warning('就诊主键无效');
      return;
    }
    // 在当前页队列中定位行，供信息条与费用清单回显
    const matched = queueRows.value.find(
      (row) => (preserveApiIdForRequest(row.encounterId) ?? '') === id,
    );
    if (!matched) {
      ElMessage.warning('未找到选中患者');
      return;
    }
    const samePatient = selectedEncounterId.value === id;
    applySelectedPatient(matched);
    // 同患者再点：selectedEncounterId 不变，须 bump 令牌驱动右侧重拉
    if (samePatient) {
      bumpContentReload();
    }
  }

  /** 关键字回车/清空：按当前日期重查全部队列 */
  function handleQueueKeywordSearch() {
    void loadPatientQueue();
  }

  /** 日期变更：按新日期重查全部队列 */
  function handleQueueDateRangeChange(range: [string, string] | null) {
    queueDateRange.value = range;
    void loadPatientQueue();
  }

  /** 仅待发耗材开关变更：按当前日期重查 */
  function handlePendingDeviceOnlyChange(_checked: boolean) {
    void loadPatientQueue();
  }

  /**
   * 待执行/已执行分桶变更：清空选中后重查，避免旧患者落在另一桶仍高亮。
   */
  function handleExecutionQueueFilterChange(filter: MedicalTechExecutionQueueFilter) {
    if (filter === executionQueueFilter.value) {
      return;
    }
    executionQueueFilter.value = filter;
    applySelectedPatient(null);
    void loadPatientQueue();
  }

  /** 住院在院态筛选变更：值未变则忽略（避免刷新重渲染误触发 change 清空选中） */
  function handleInpatientStayFilterChange(filter: MedicalTechInpatientStayFilter) {
    if (filter === inpatientStayFilter.value) {
      return;
    }
    inpatientStayFilter.value = filter;
    applySelectedPatient(null);
    void loadPatientQueue();
  }

  /** 队列刷新按钮：重查队列，若已选患者则同步强制重载右侧执行/费用/计费 */
  async function handleQueueRefresh() {
    await loadPatientQueue();
    // 刷新后 id 未变，须 bump 才能驱动右侧重拉（对齐护士站刷新仍拉处置单）
    if (preserveApiIdForRequest(selectedEncounterId.value)) {
      bumpContentReload();
    }
  }

  /** 清空选中并重查（就诊类别切换时） */
  function resetSelectionAndReloadQueue() {
    applySelectedPatient(null);
    void loadPatientQueue();
  }

  // 就诊类别变化：清空选中、复位在院筛并重查（执行态保持当前桶）
  watch(encounterScope, () => {
    inpatientStayFilter.value = 'IN_HOSPITAL';
    resetSelectionAndReloadQueue();
  });

  return {
    queueKeyword,
    queueDateRange,
    pendingDeviceOnly,
    executionQueueFilter,
    inpatientStayFilter,
    queueRows,
    queueLoading,
    loadPatientQueue,
    handleSelectPatient,
    handleQueueKeywordSearch,
    handleQueueDateRangeChange,
    handlePendingDeviceOnlyChange,
    handleExecutionQueueFilterChange,
    handleInpatientStayFilterChange,
    handleQueueRefresh,
    resetSelectionAndReloadQueue,
  };
}

/**
 * 门诊队列日历日 → 接口闭区间时刻（起日 00:00:00～止日 23:59:59）。
 * 只传 YYYY-MM-DD 会被后端当成当天 00:00:00，挂号时刻全部被滤掉。
 */
function resolveOutpatientQueueDateTimes(range: [string, string] | null): {
  endDate?: string;
  startDate?: string;
} {
  if (!range || range.length !== 2) {
    return {};
  }
  const startBound = getLocalDayRangeDateTimeStrings(String(range[0] ?? '').trim());
  const endBound = getLocalDayRangeDateTimeStrings(String(range[1] ?? '').trim());
  if (!startBound.startDate || !endBound.endDate) {
    return {};
  }
  return {
    startDate: startBound.startDate,
    endDate: endBound.endDate,
  };
}

/** 按日期条件逐页拼出全部队列行（单页上限 100） */
async function fetchQueueForDate(
  filter: Omit<PageMedicalTechPatientQueueRequest, 'pageNo' | 'pageSize'>,
): Promise<MedicalTechPatientQueueItemDto[]> {
  const rows: MedicalTechPatientQueueItemDto[] = [];
  let pageNo = 1;
  let total = Number.POSITIVE_INFINITY;
  while (pageNo <= QUEUE_FETCH_MAX_PAGES && rows.length < total) {
    const page = await httpPageMedicalTechPatientQueue({
      ...filter,
      pageNo,
      pageSize: QUEUE_FETCH_PAGE_SIZE,
    });
    const content = page.content ?? [];
    // 本页追加到日期结果，空页即停
    rows.push(...content);
    total = page.totalCount ?? rows.length;
    if (content.length === 0) {
      break;
    }
    pageNo += 1;
  }
  return rows;
}
