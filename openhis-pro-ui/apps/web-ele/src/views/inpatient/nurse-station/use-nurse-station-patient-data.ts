import type { PatientItem, PatientStatusFilter, RegionItem, WardItem } from './types';

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

const QUEUE_PAGE_SIZE = 50;

/** 护士站患者基础数据：病区树、队列接口、筛选与列表统计。 */
export function useNurseStationPatientData() {
  const regionOptions = ref<RegionItem[]>([]);
  const wardOptions = ref<WardItem[]>([]);
  const patients = ref<PatientItem[]>([]);
  const keyword = ref('');
  const statusFilter = ref<PatientStatusFilter>('all');
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
    return patients.value.filter((item) => {
      if (!item.regionId) {
        return false;
      }
      const matchRegion = regionIds.length === 0 || regionIds.includes(item.regionId);
      const matchWard = wardIds.length === 0 || !item.wardId || wardIds.includes(item.wardId);
      return matchRegion && matchWard;
    });
  });

  /** 解析队列请求的地理范围：单选病房优先，其次单选病区。 */
  function resolveQueueLocationParams(): { houseId?: string; wardId?: string } {
    if (selectedWardIds.value.length === 1) {
      return { houseId: selectedWardIds.value[0] };
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

  /** 按页拉取在院患者队列直至取完当前筛选条件下的全部行。 */
  async function fetchAllPatientQueueRows() {
    const locationParams = resolveQueueLocationParams();
    const taskFilter = mapStatusFilterToTaskFilter(statusFilter.value);
    const rows: PatientItem[] = [];
    let pageNo = 1;
    let totalCount = 0;
    while (true) {
      const res = await httpPagePatientQueue({
        ...locationParams,
        keyword: keyword.value.trim() || undefined,
        nurseStationTaskFilter: taskFilter,
        pageNo,
        pageSize: QUEUE_PAGE_SIZE,
      });
      const parsed = parsePagedRowsFromApiData(res);
      for (const row of parsed.content) {
        rows.push(mapQueueItemToPatientItem(row, wardOptions.value));
      }
      totalCount = parsed.totalCount;
      if (rows.length >= totalCount || parsed.content.length === 0) {
        break;
      }
      pageNo += 1;
    }
    return rows;
  }

  /** 加载在院患者队列。 */
  async function loadPatientQueue() {
    queueLoading.value = true;
    try {
      const rows = await fetchAllPatientQueueRows();
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
    await loadPatientQueue();
  }

  /** 切换病区/病房筛选区展开或收起。 */
  function toggleFilterPanel() {
    filterPanelExpanded.value = !filterPanelExpanded.value;
  }

  /** 患者列表面板上下文中的数据与筛选动作。 */
  const patientListPanelDataContext = {
    availableWards,
    changeKeyword,
    changeRegionIds,
    changeStatusFilter,
    changeWardIds,
    filterPanelExpanded,
    filteredPatients,
    keyword,
    pendingAuditCount,
    pendingExecuteCount,
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
    availableWards,
    bedMapWardIds,
    bootstrapPatientWorkbench,
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
