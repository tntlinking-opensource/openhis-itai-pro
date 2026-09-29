import type {
  MedicalTechEncounterScope,
  MedicalTechProcedureRecordDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { Ref } from 'vue';

import { computed, reactive, ref, watch } from 'vue';

import { httpPageMedicalTechProcedureRecords } from '#/api/medical-tech/medical-tech-execution.http';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

export interface UseMedicalTechProcedureRecordsOptions {
  encounterScope: Ref<MedicalTechEncounterScope>;
  selectedEncounterId: Ref<string>;
}

/**
 * 医技执行记录：侧栏最近 N 条 + 抽屉全量分页（IF-MT-EXE-03）。
 */
export function useMedicalTechProcedureRecords(options: UseMedicalTechProcedureRecordsOptions) {
  const { encounterScope, selectedEncounterId } = options;

  const recentRecords = ref<MedicalTechProcedureRecordDto[]>([]);
  const drawerOpen = ref(false);
  const drawerPage = reactive({ pageNo: 1, pageSize: 20 });
  const drawerRows = ref<MedicalTechProcedureRecordDto[]>([]);
  const drawerTotal = ref(0);

  /** 侧栏展示最近 5 条 */
  const recentPreview = computed(() => recentRecords.value.slice(0, 5));

  const { run: runPageRecords, loading: recordsLoading } = useRequest(
    httpPageMedicalTechProcedureRecords,
    {
      manual: true,
      showErrorMessage: false,
    },
  );

  /** 拉取最近记录（侧栏）；无就诊则清空 */
  async function loadRecentRecords() {
    const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
    if (!encounterId) {
      recentRecords.value = [];
      return;
    }
    try {
      const data = await runPageRecords({
        encounterId,
        encounterScope: encounterScope.value,
        pageNo: 1,
        pageSize: 5,
      });
      recentRecords.value = data.content ?? [];
    } catch {
      recentRecords.value = [];
    }
  }

  /** 拉取抽屉分页记录 */
  async function loadDrawerRecords() {
    const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
    if (!encounterId) {
      drawerRows.value = [];
      drawerTotal.value = 0;
      return;
    }
    try {
      const data = await runPageRecords({
        encounterId,
        encounterScope: encounterScope.value,
        pageNo: drawerPage.pageNo,
        pageSize: drawerPage.pageSize,
      });
      drawerRows.value = data.content ?? [];
      drawerTotal.value = data.totalCount ?? 0;
    } catch {
      drawerRows.value = [];
      drawerTotal.value = 0;
    }
  }

  /** 打开「查看全部」抽屉并回第 1 页 */
  function openRecordsDrawer() {
    drawerPage.pageNo = 1;
    drawerOpen.value = true;
    void loadDrawerRecords();
  }

  /** 抽屉翻页 */
  function handleDrawerPageChange(page: number) {
    drawerPage.pageNo = page;
    void loadDrawerRecords();
  }

  /** 执行/取消后刷新侧栏；抽屉开着则同步翻页数据 */
  async function reloadProcedureRecords() {
    await loadRecentRecords();
    if (drawerOpen.value) {
      await loadDrawerRecords();
    }
  }

  // 选中就诊变化：刷新最近记录；抽屉打开时重载
  watch(
    selectedEncounterId,
    () => {
      void loadRecentRecords();
      if (drawerOpen.value) {
        drawerPage.pageNo = 1;
        void loadDrawerRecords();
      }
    },
    { immediate: true },
  );

  return {
    recentPreview,
    recordsLoading,
    drawerOpen,
    drawerPage,
    drawerRows,
    drawerTotal,
    openRecordsDrawer,
    handleDrawerPageChange,
    reloadProcedureRecords,
  };
}
