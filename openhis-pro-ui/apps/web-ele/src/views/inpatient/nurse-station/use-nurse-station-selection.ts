import type { ComputedRef, Ref } from 'vue';

import type { PatientItem, SelectionPolicy } from './types';

import { computed, ref, watch } from 'vue';

interface UseNurseStationSelectionOptions {
  patients: Ref<PatientItem[]>;
  filteredPatients: ComputedRef<PatientItem[]>;
}

/** 维护护士站单选/多选策略与主患者上下文。 */
export function useNurseStationSelection(options: UseNurseStationSelectionOptions) {
  const { filteredPatients, patients } = options;

  /** 当前页签对应的患者选择策略。 */
  const selectionPolicy = ref<SelectionPolicy>('single');
  /** 当前批量选中的患者 id 集合（单选时最多保留 1 个）。 */
  const selectedPatientIds = ref<Set<string>>(new Set());
  /** 右侧业务区主操作患者 id（用于单选页保持上下文）。 */
  const activePatientId = ref<null | string>(null);
  /** 最近一次交互患者 id（多选收敛到单选时作为兜底优先级）。 */
  const lastTouchedPatientId = ref<null | string>(null);

  /**
   * 将选中 id 集合同步回患者卡片 selected 展示态。
   * 不在此剔除「当前列表没有」的选中 id：队列多页逐步回写时名单未齐，
   * 误删会导致刷新丢选中并自动落到第一人。
   */
  function syncPatientSelectedFlags() {
    // 将 store 态映射为卡片态，避免模板层维护另一套真值来源。
    for (const item of patients.value) {
      item.selected = selectedPatientIds.value.has(item.id);
    }
  }

  /**
   * 队列完整加载后剔除已不在名单中的选中；主患者失效时清空，禁止回落到第一人。
   */
  function pruneStaleSelection() {
    const validIds = new Set(patients.value.map((item) => item.id));
    for (const id of [...selectedPatientIds.value]) {
      if (!validIds.has(id)) {
        selectedPatientIds.value.delete(id);
      }
    }
    if (activePatientId.value && !validIds.has(activePatientId.value)) {
      activePatientId.value = [...selectedPatientIds.value][0] ?? null;
    }
    syncPatientSelectedFlags();
  }

  /** 队列重载（如入出转分床后）替换 patients 时，按 selectedPatientIds 恢复勾选展示。 */
  watch(
    () => patients.value,
    () => {
      syncPatientSelectedFlags();
    },
  );

  /** 初始化选中：仅保留已有勾选，禁止默认落到列表第一人。 */
  function initSelectionState() {
    // 兼容 mock 数据里预置的 selected 字段；队列接口映射恒为 false。
    const preselectedIds = patients.value.filter((item) => item.selected).map((item) => item.id);
    selectedPatientIds.value = new Set(preselectedIds);
    activePatientId.value = preselectedIds[0] ?? null;
    syncPatientSelectedFlags();
  }

  /** 设置主操作患者（列表点击、床位图点击等入口统一走此方法）。 */
  function setActivePatient(id: string) {
    activePatientId.value = id;
    lastTouchedPatientId.value = id;
    if (selectionPolicy.value === 'single') {
      selectedPatientIds.value = new Set([id]);
    } else {
      selectedPatientIds.value.add(id);
    }
    syncPatientSelectedFlags();
  }

  /** 根据选择策略收敛当前选中集合，保证单选/多选切换稳定。 */
  function normalizeSelectionAfterPolicyChange(
    policy: SelectionPolicy,
    onWarning: (msg: string) => void,
  ) {
    if (policy === 'multiple') {
      if (activePatientId.value) {
        selectedPatientIds.value.add(activePatientId.value);
      }
      syncPatientSelectedFlags();
      return;
    }
    if (selectedPatientIds.value.size > 1) {
      // 从多选切到单选时，存在多名患者选中则清空，避免误在错误患者上继续操作。
      selectedPatientIds.value.clear();
      activePatientId.value = null;
      onWarning('当前页不支持多选患者，请重新选择');
      syncPatientSelectedFlags();
      return;
    }
    const selectedIdList = [...selectedPatientIds.value];
    // 仅在已有勾选内收敛；无人勾选时保持空，禁止补选第一人
    const keepId =
      (activePatientId.value && selectedPatientIds.value.has(activePatientId.value)
        ? activePatientId.value
        : null) ||
      (lastTouchedPatientId.value && selectedPatientIds.value.has(lastTouchedPatientId.value)
        ? lastTouchedPatientId.value
        : null) ||
      selectedIdList[0] ||
      null;
    selectedPatientIds.value = keepId ? new Set([keepId]) : new Set();
    activePatientId.value = keepId;
    syncPatientSelectedFlags();
  }

  /** 设置选择策略并触发收敛。 */
  function setSelectionPolicy(policy: SelectionPolicy, onWarning: (msg: string) => void) {
    if (selectionPolicy.value === policy) return;
    selectionPolicy.value = policy;
    normalizeSelectionAfterPolicyChange(policy, onWarning);
  }

  /** 点击卡片区域切换患者选中状态。 */
  function togglePatientCardSelection(id: string) {
    if (selectionPolicy.value === 'single') {
      setActivePatient(id);
      return;
    }
    lastTouchedPatientId.value = id;
    if (selectedPatientIds.value.has(id)) {
      selectedPatientIds.value.delete(id);
      if (activePatientId.value === id) {
        // 取消主患者时改指剩余勾选；无人勾选则清空，禁止回落第一人
        activePatientId.value = [...selectedPatientIds.value][0] ?? null;
      }
    } else {
      selectedPatientIds.value.add(id);
      activePatientId.value = id;
    }
    syncPatientSelectedFlags();
  }

  /** 勾选框切换患者选中。 */
  function togglePatientSelection(id: string, value: boolean) {
    if (selectionPolicy.value === 'single') {
      if (value) {
        setActivePatient(id);
        return;
      }
      selectedPatientIds.value.clear();
      activePatientId.value = null;
      syncPatientSelectedFlags();
      return;
    }
    lastTouchedPatientId.value = id;
    if (value) {
      selectedPatientIds.value.add(id);
      activePatientId.value = id;
    } else {
      selectedPatientIds.value.delete(id);
      if (activePatientId.value === id) {
        activePatientId.value = [...selectedPatientIds.value][0] ?? null;
      }
    }
    syncPatientSelectedFlags();
  }

  /** 多选收敛为只保留当前集合中的第一人（打印中心单人单据快捷纠正） */
  function keepFirstSelectedPatient() {
    const firstId =
      (activePatientId.value && selectedPatientIds.value.has(activePatientId.value)
        ? activePatientId.value
        : null) ||
      (lastTouchedPatientId.value && selectedPatientIds.value.has(lastTouchedPatientId.value)
        ? lastTouchedPatientId.value
        : null) ||
      [...selectedPatientIds.value][0] ||
      null;
    if (!firstId) {
      return;
    }
    selectedPatientIds.value = new Set([firstId]);
    activePatientId.value = firstId;
    syncPatientSelectedFlags();
  }

  /** 清空左侧患者勾选（打印中心范围纠正） */
  function clearSelectedPatients() {
    selectedPatientIds.value.clear();
    activePatientId.value = null;
    syncPatientSelectedFlags();
  }

  /** 当前筛选列表是否已全部勾选（全选复选框态；读卡片 selected 以保证响应式） */
  const allFilteredPatientsSelected = computed(() => {
    const list = filteredPatients.value;
    if (list.length === 0) return false;
    return list.every((item) => item.selected);
  });

  /** 当前筛选列表是否部分勾选（全选半选态） */
  const filteredPatientsIndeterminate = computed(() => {
    const list = filteredPatients.value;
    if (list.length === 0) return false;
    let selectedCount = 0;
    for (const item of list) {
      if (item.selected) {
        selectedCount += 1;
      }
    }
    return selectedCount > 0 && selectedCount < list.length;
  });

  /** 全选/取消全选当前筛选队列（多选页签）；单选页签忽略 */
  function toggleSelectAllFilteredPatients(value: boolean) {
    if (selectionPolicy.value === 'single') {
      return;
    }
    if (!value) {
      // 整表替换 Set，确保选中集合变化能触发右侧列表重拉
      selectedPatientIds.value = new Set();
      activePatientId.value = null;
      syncPatientSelectedFlags();
      return;
    }
    const nextIds = filteredPatients.value.map((item) => item.id);
    selectedPatientIds.value = new Set(nextIds);
    activePatientId.value =
      (activePatientId.value && selectedPatientIds.value.has(activePatientId.value)
        ? activePatientId.value
        : null) ||
      (lastTouchedPatientId.value && selectedPatientIds.value.has(lastTouchedPatientId.value)
        ? lastTouchedPatientId.value
        : null) ||
      nextIds[0] ||
      null;
    syncPatientSelectedFlags();
  }

  /** 患者列表面板上下文中的选择动作。 */
  const patientListPanelSelectionContext = {
    allFilteredPatientsSelected,
    filteredPatientsIndeterminate,
    selectionPolicy,
    togglePatientCardSelection,
    togglePatientSelection,
    toggleSelectAllFilteredPatients,
  };

  return {
    activePatientId,
    clearSelectedPatients,
    initSelectionState,
    keepFirstSelectedPatient,
    patientListPanelSelectionContext,
    pruneStaleSelection,
    selectedPatientIds,
    selectionPolicy,
    setActivePatient,
    setSelectionPolicy,
    syncPatientSelectedFlags,
    togglePatientCardSelection,
    togglePatientSelection,
    toggleSelectAllFilteredPatients,
  };
}
