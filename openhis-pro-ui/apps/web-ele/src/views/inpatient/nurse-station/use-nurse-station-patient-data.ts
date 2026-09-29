import type {
  PatientAdmissionFilter,
  PatientItem,
  PatientStatusFilter,
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

import {
  mapQueueItemToPatientItem,
  mapStatusFilterToTaskFilter,
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

/** 护士站患者基础数据：病区树、队列接口、筛选与列表统计。 */
export function useNurseStationPatientData() {
  const regionOptions = ref<RegionItem[]>([]);
  const wardOptions = ref<WardItem[]>([]);
  const patients = ref<PatientItem[]>([]);
  const keyword = ref('');
  const statusFilter = ref<PatientStatusFilter>('all');
  /** 入科状态筛选（默认在科；切换会重拉队列，待入科避开 houseId 占床收窄）。 */
  const admissionFilter = ref<PatientAdmissionFilter>('in-ward');
  const selectedRegionIds = ref<string[]>([]);
  const selectedWardIds = ref<string[]>([]);
  /** 病区/病房筛选区是否展开。 */
  const filterPanelExpanded = ref(true);
  const queueLoading = ref(false);
  const statusOptions: Array<{ label: string; value: PatientStatusFilter }> = [
    { label: '全部任务', value: 'all' },
    { label: '待校对', value: 'pending-audit' },
    { label: '待执行', value: 'pending-execute' },
    { label: '已处理', value: 'processed' },
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
    return patients.value.filter((item) => {
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
      const matchRegion = regionIds.length === 0 || regionIds.includes(item.regionId);
      const matchWard = wardIds.length === 0 || !item.wardId || wardIds.includes(item.wardId);
      return matchRegion && matchWard;
    });
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
   */
  async function fetchAllPatientQueueRows(
    onPage?: (accumulated: PatientItem[]) => void,
  ): Promise<PatientItem[]> {
    const locationParams = resolveQueueLocationParams();
    const taskFilter = mapStatusFilterToTaskFilter(statusFilter.value);
    const rows: PatientItem[] = [];
    let pageNo = 1;
    let totalCount = 0;
    while (true) {
      let res: Awaited<ReturnType<typeof httpPagePatientQueue>>;
      try {
        res = await httpPagePatientQueue({
          ...locationParams,
          keyword: keyword.value.trim() || undefined,
          nurseStationTaskFilter: taskFilter,
          pageNo,
          pageSize: QUEUE_PAGE_SIZE,
        });
      } catch (error) {
        // 首页失败直接抛出；后续页失败则带着已有行返回，避免侧栏全空
        if (rows.length === 0) {
          throw error;
        }
        ElMessage.warning('患者队列未全部加载，可点击刷新重试');
        break;
      }
      const parsed = parsePagedRowsFromApiData(res);
      // 本页行映射为侧栏卡片模型后并入累计列表
      for (const row of parsed.content) {
        rows.push(mapQueueItemToPatientItem(row, wardOptions.value));
      }
      totalCount = parsed.totalCount;
      onPage?.(rows.slice());
      if (rows.length >= totalCount || parsed.content.length === 0) {
        break;
      }
      pageNo += 1;
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
    } catch {
      ElMessage.error('加载患者队列失败，请稍后重试');
    } finally {
      queueLoading.value = false;
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

  /** 任务状态筛选变更。 */
  function changeStatusFilter(value: PatientStatusFilter) {
    statusFilter.value = value;
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
    statusFilter.value = 'all';
    admissionFilter.value = 'in-ward';
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
    changeStatusFilter,
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
    statusFilter,
    statusOptions,
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
    changeStatusFilter,
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
    regionNameByIdRecord,
    regionOptions,
    refreshPatientFilters,
    selectedRegionIds,
    selectedWardIds,
    statusFilter,
    statusOptions,
    syncWardSelectionByRegion,
    totalPatientCount,
    toggleFilterPanel,
    wardMap,
    wardNameByIdRecord,
    wardOptions,
  };
}
