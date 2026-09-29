import type {
  MedicalTechEncounterScope,
  MedicalTechInpatientStayFilter,
  MedicalTechPatientQueueItemDto,
  PageMedicalTechPatientQueueRequest,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { Ref } from 'vue';

import { ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { httpPageMedicalTechPatientQueue } from '#/api/medical-tech/medical-tech-execution.http';
import { useRequest } from '#/hooks/use-request';
import { formatLocalDateYmd } from '#/utils/format/date-range-quick';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 后端单页上限 100，按日期拉全量时逐页拼完 */
const QUEUE_FETCH_PAGE_SIZE = 100;
/** 防止 total 异常时死循环 */
const QUEUE_FETCH_MAX_PAGES = 30;

export interface UseMedicalTechQueueOptions {
  encounterScope: Ref<MedicalTechEncounterScope>;
  selectedEncounterId: Ref<string>;
  selectedPatientSummary: Ref<MedicalTechPatientQueueItemDto | null>;
}

/**
 * 医技工作站壳层患者队列：按日期一次拉全量，不在侧栏翻页。
 * 非 TECH 科室由全局拦截器静默，列表保持空。
 */
export function useMedicalTechQueue(options: UseMedicalTechQueueOptions) {
  const { encounterScope, selectedEncounterId, selectedPatientSummary } = options;

  const queueKeyword = ref('');
  // 默认当天：对齐门诊护士站，避免无日期时扫出全库数百条
  const todayYmd = formatLocalDateYmd();
  const queueDateRange = ref<[string, string] | null>([todayYmd, todayYmd]);
  const pendingDeviceOnly = ref(false);
  /** 住院在院态：默认在院（与历史队列同口径） */
  const inpatientStayFilter = ref<MedicalTechInpatientStayFilter>('IN_HOSPITAL');
  const queueRows = ref<MedicalTechPatientQueueItemDto[]>([]);

  const { run: runLoadQueue, loading: queueLoading } = useRequest(fetchQueueForDate, {
    manual: true,
    // 非 TECH：全局拦截器静默，列表保持空（页面骨架仍展示）
    showErrorMessage: false,
    onSuccess: (rows) => {
      queueRows.value = rows;
    },
  });

  /** 组装队列查询请求并拉取当日全部；非 TECH 等空态静默清空列表 */
  async function loadPatientQueue() {
    // 住院不传就诊开始时间（在院跨日）；仅门诊按日期窗收窄
    const isOutpatient = encounterScope.value === 'OUTPATIENT';
    const startDate = isOutpatient ? queueDateRange.value?.[0] : undefined;
    const endDate = isOutpatient ? queueDateRange.value?.[1] : undefined;
    // 在院态筛选仅住院提交；门诊不传
    const stayFilter = isOutpatient ? undefined : inpatientStayFilter.value;
    try {
      await runLoadQueue({
        encounterScope: encounterScope.value,
        keyword: queueKeyword.value.trim() || undefined,
        pendingDeviceOnly: pendingDeviceOnly.value || undefined,
        inpatientStayFilter: stayFilter,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      });
      // 划价/冲销后重拉：同步刷新当前选中行余额等读模型
      const selectedId = preserveApiIdForRequest(selectedEncounterId.value);
      if (selectedId) {
        const refreshed = queueRows.value.find(
          (row) => (preserveApiIdForRequest(row.encounterId) ?? '') === selectedId,
        );
        if (refreshed) {
          selectedPatientSummary.value = refreshed;
        }
      }
    } catch {
      // 门禁/空态已由全局拦截器静默；列表保持空
      queueRows.value = [];
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

  /** 点选队列患者：写入共享上下文供执行/费用/计费页签共用 */
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
    applySelectedPatient(matched);
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

  /** 住院在院态筛选变更：清空选中并重查 */
  function handleInpatientStayFilterChange(filter: MedicalTechInpatientStayFilter) {
    inpatientStayFilter.value = filter;
    applySelectedPatient(null);
    void loadPatientQueue();
  }

  /** 队列刷新按钮：按当前筛选重查全部 */
  function handleQueueRefresh() {
    void loadPatientQueue();
  }

  /** 清空选中并重查（就诊类别切换时） */
  function resetSelectionAndReloadQueue() {
    applySelectedPatient(null);
    void loadPatientQueue();
  }

  // 就诊类别变化：清空选中、复位在院筛并重查
  watch(encounterScope, () => {
    inpatientStayFilter.value = 'IN_HOSPITAL';
    resetSelectionAndReloadQueue();
  });

  return {
    queueKeyword,
    queueDateRange,
    pendingDeviceOnly,
    inpatientStayFilter,
    queueRows,
    queueLoading,
    loadPatientQueue,
    handleSelectPatient,
    handleQueueKeywordSearch,
    handleQueueDateRangeChange,
    handlePendingDeviceOnlyChange,
    handleInpatientStayFilterChange,
    handleQueueRefresh,
    resetSelectionAndReloadQueue,
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
