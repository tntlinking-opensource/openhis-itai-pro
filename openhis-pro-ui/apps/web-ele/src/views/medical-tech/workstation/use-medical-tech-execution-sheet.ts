import type {
  MedicalTechCancelDeviceItemDto,
  MedicalTechCancelProcedureItemDto,
  MedicalTechEncounterScope,
  MedicalTechExecutionLineDto,
  MedicalTechPatientQueueItemDto,
  MedicalTechPerformItemDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { Ref } from 'vue';

import { computed, nextTick, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpBatchCancelMedicalTechExecution,
  httpBatchPerformMedicalTechExecution,
  httpListMedicalTechNetRemainingProcedures,
  httpLoadMedicalTechExecutionSheet,
} from '#/api/medical-tech/medical-tech-execution.http';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { canSelectSupplyForBatchExecute } from '#/utils/nurse-station/nurse-execute-status';

import {
  buildMedicalTechDeviceLineKey,
  resolveMedicalTechSupplyStatusCode,
  toMedicalTechSupplyItems,
} from './medical-tech-patient-map';

export interface UseMedicalTechExecutionSheetOptions {
  encounterScope: Ref<MedicalTechEncounterScope>;
  selectedEncounterId: Ref<string>;
  selectedPatientSummary: Ref<MedicalTechPatientQueueItemDto | null>;
  reloadPatientQueue: () => Promise<void>;
  /** 执行/取消后刷新最近执行记录 */
  reloadProcedureRecords?: () => Promise<void>;
}

/**
 * 医技执行单：诊疗/耗材明细、勾选与批执行/取消（壳层右栏与底栏共用）。
 */
export function useMedicalTechExecutionSheet(options: UseMedicalTechExecutionSheetOptions) {
  const {
    encounterScope,
    selectedEncounterId,
    selectedPatientSummary,
    reloadPatientQueue,
    reloadProcedureRecords,
  } = options;

  const serviceLines = ref<MedicalTechExecutionLineDto[]>([]);
  const deviceLines = ref<MedicalTechExecutionLineDto[]>([]);
  const selectedServiceRows = ref<MedicalTechExecutionLineDto[]>([]);
  /** 侧栏已勾选耗材卡片 id（requestId+dispenseId） */
  const selectedDeviceKeys = ref<string[]>([]);
  /** 诊疗表实例：清空勾选 */
  let clearTableSelectionFn: (() => void) | null = null;

  /** 侧栏耗材卡片列表 */
  const supplyItems = computed(() => toMedicalTechSupplyItems(deviceLines.value));

  /** 已选诊疗数 */
  const selectedServiceCount = computed(() => selectedServiceRows.value.length);

  /** 已选耗材数 */
  const selectedDeviceCount = computed(() => selectedDeviceKeys.value.length);

  /** 批量执行可选合计 */
  const totalSelectedCount = computed(() => selectedServiceCount.value + selectedDeviceCount.value);

  /** 是否可点批量执行 */
  const canExecuteSelected = computed(() => totalSelectedCount.value > 0);

  /** 是否可点批量取消 */
  const canCancelSelected = computed(
    () => selectedServiceRows.value.length > 0 || selectedDeviceKeys.value.length > 0,
  );

  /** 按勾选 key 取耗材行 */
  const selectedDeviceRows = computed(() => {
    const keySet = new Set(selectedDeviceKeys.value);
    return deviceLines.value.filter((row) => keySet.has(buildMedicalTechDeviceLineKey(row)));
  });

  const { run: runLoadSheet, loading: sheetLoading } = useRequest(
    httpLoadMedicalTechExecutionSheet,
    {
      manual: true,
      showErrorMessage: false,
      onSuccess: (data) => {
        const nextServiceLines = data.serviceLines ?? [];
        const nextDeviceLines = data.deviceLines ?? [];
        serviceLines.value = nextServiceLines;
        deviceLines.value = nextDeviceLines;
        // 门诊：侧栏可勾选耗材默认全选；住院隐藏耗材区，清空勾选
        if (encounterScope.value === 'OUTPATIENT') {
          selectedDeviceKeys.value = collectDefaultSelectedDeviceKeys(nextDeviceLines);
        } else {
          selectedDeviceKeys.value = [];
        }
        // 诊疗表默认全选由 execution-panel 监听 serviceLines 后勾选
        selectedServiceRows.value = [];
      },
    },
  );

  const { run: runPerform, loading: performLoading } = useRequest(
    httpBatchPerformMedicalTechExecution,
    {
      manual: true,
      onSuccess: (data) => {
        ElMessage.success(data.message || `执行成功 ${data.successCount ?? 0} 条`);
        void refreshSheetAndQueue();
      },
    },
  );

  const { run: runCancel, loading: cancelLoading } = useRequest(
    httpBatchCancelMedicalTechExecution,
    {
      manual: true,
      onSuccess: (data) => {
        ElMessage.success(data.message || `取消成功 ${data.successCount ?? 0} 条`);
        void refreshSheetAndQueue();
      },
    },
  );

  const { run: runListNetRemaining } = useRequest(httpListMedicalTechNetRemainingProcedures, {
    manual: true,
    showErrorMessage: false,
  });

  /** 注册诊疗表清空勾选回调（由中栏表格挂载时注入） */
  function registerClearTableSelection(fn: (() => void) | null) {
    clearTableSelectionFn = fn;
  }

  /**
   * 门诊耗材默认全选：后端 selectable + 侧栏「待发放」可勾口径（与 SupplyItemCard 一致）。
   *
   * @param rows 本页耗材执行行
   * @return 默认勾选的卡片 id
   */
  function collectDefaultSelectedDeviceKeys(rows: MedicalTechExecutionLineDto[]): string[] {
    const keys: string[] = [];
    for (const row of rows) {
      if (row.selectable === false) {
        continue;
      }
      const statusCode = resolveMedicalTechSupplyStatusCode(row.dispenseStatusEnum);
      if (!canSelectSupplyForBatchExecute(statusCode)) {
        continue;
      }
      keys.push(buildMedicalTechDeviceLineKey(row));
    }
    return keys;
  }

  /** 清空诊疗表勾选 */
  async function clearServiceTableSelection() {
    await nextTick();
    clearTableSelectionFn?.();
  }

  /** 按当前就诊加载执行单；失败时清空明细 */
  async function loadExecutionSheet() {
    const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
    if (!encounterId) {
      serviceLines.value = [];
      deviceLines.value = [];
      selectedServiceRows.value = [];
      selectedDeviceKeys.value = [];
      return;
    }
    try {
      await runLoadSheet({
        encounterId,
        encounterScope: encounterScope.value,
      });
    } catch {
      serviceLines.value = [];
      deviceLines.value = [];
      selectedServiceRows.value = [];
      selectedDeviceKeys.value = [];
    }
  }

  /** 执行/取消后刷新执行单、队列与执行记录 */
  async function refreshSheetAndQueue() {
    const tasks: Array<Promise<void>> = [loadExecutionSheet(), reloadPatientQueue()];
    if (reloadProcedureRecords) {
      tasks.push(reloadProcedureRecords());
    }
    await Promise.all(tasks);
  }

  /** 一键清空勾选 */
  async function handleClearSelection() {
    selectedDeviceKeys.value = [];
    await clearServiceTableSelection();
    selectedServiceRows.value = [];
  }

  /** 诊疗行多选变更 */
  function handleServiceSelectionChange(rows: MedicalTechExecutionLineDto[]) {
    selectedServiceRows.value = rows;
  }

  /** 侧栏耗材勾选变更 */
  function handleSupplyChange(id: string, checked: boolean) {
    if (checked) {
      if (!selectedDeviceKeys.value.includes(id)) {
        selectedDeviceKeys.value = [...selectedDeviceKeys.value, id];
      }
      return;
    }
    selectedDeviceKeys.value = selectedDeviceKeys.value.filter((key) => key !== id);
  }

  /** 批量执行 */
  async function handlePerform() {
    const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
    if (!encounterId) {
      ElMessage.warning('请先选择患者');
      return;
    }

    const serviceItems: MedicalTechPerformItemDto[] = [];
    for (const row of selectedServiceRows.value) {
      if (row.selectable === false) continue;
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) continue;
      serviceItems.push({
        requestId,
        requestTable: row.requestTable,
      });
    }

    const deviceItems: MedicalTechPerformItemDto[] = [];
    for (const row of selectedDeviceRows.value) {
      if (row.selectable === false) continue;
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) continue;
      deviceItems.push({
        requestId,
        requestTable: row.requestTable,
        dispenseId: preserveApiIdForRequest(row.dispenseId) ?? null,
      });
    }

    if (serviceItems.length === 0 && deviceItems.length === 0) {
      ElMessage.warning('请勾选可执行的诊疗或耗材行');
      return;
    }

    await runPerform({
      encounterId,
      encounterScope: encounterScope.value,
      serviceItems,
      deviceItems,
    });
  }

  /**
   * 提交取消：诊疗走净剩 procedureId；耗材走 dispenseId。
   */
  async function submitCancel(
    serviceRows: MedicalTechExecutionLineDto[],
    deviceRows: MedicalTechExecutionLineDto[],
  ) {
    const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
    if (!encounterId) {
      ElMessage.warning('请先选择患者');
      return;
    }

    if (serviceRows.length === 0 && deviceRows.length === 0) {
      ElMessage.warning('请勾选要取消的诊疗或耗材行');
      return;
    }

    try {
      await ElMessageBox.confirm('确认取消所选执行？', '取消执行', { type: 'warning' });
    } catch {
      return;
    }

    const procedureItems: MedicalTechCancelProcedureItemDto[] = [];
    for (const row of serviceRows) {
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) continue;
      try {
        const net = await runListNetRemaining({
          encounterId,
          encounterScope: encounterScope.value,
          requestId,
          requestTable: row.requestTable,
        });
        for (const record of net?.records ?? []) {
          const procedureId = preserveApiIdForRequest(record.procedureId);
          if (!procedureId) continue;
          procedureItems.push({ procedureId });
        }
      } catch {
        ElMessage.error(`查询「${row.itemName || requestId}」可取消记录失败`);
        return;
      }
    }

    const deviceItems: MedicalTechCancelDeviceItemDto[] = [];
    for (const row of deviceRows) {
      const dispenseId = preserveApiIdForRequest(row.dispenseId);
      if (!dispenseId) continue;
      deviceItems.push({ dispenseId });
    }

    if (procedureItems.length === 0 && deviceItems.length === 0) {
      ElMessage.warning('所选行无可取消的执行记录或发放行');
      return;
    }

    await runCancel({
      encounterId,
      encounterScope: encounterScope.value,
      procedureItems,
      deviceItems,
    });
  }

  /** 页脚批量取消 */
  async function handleCancel() {
    await submitCancel(selectedServiceRows.value, selectedDeviceRows.value);
  }

  /** 侧栏卡片取消：仅取消该发放行 */
  async function handleCancelSupply(id: string) {
    const row = deviceLines.value.find((item) => buildMedicalTechDeviceLineKey(item) === id);
    if (!row) {
      ElMessage.warning('未找到对应耗材行');
      return;
    }
    await submitCancel([], [row]);
  }

  // 壳层选中就诊变化：加载或清空执行单
  watch(
    selectedEncounterId,
    () => {
      void loadExecutionSheet();
    },
    { immediate: true },
  );

  // 未选患者时不保留勾选态
  watch(selectedPatientSummary, (summary) => {
    if (!summary) {
      selectedServiceRows.value = [];
      selectedDeviceKeys.value = [];
    }
  });

  return {
    serviceLines,
    deviceLines,
    selectedDeviceKeys,
    supplyItems,
    sheetLoading,
    selectedServiceCount,
    selectedDeviceCount,
    totalSelectedCount,
    canExecuteSelected,
    canCancelSelected,
    performLoading,
    cancelLoading,
    registerClearTableSelection,
    handleServiceSelectionChange,
    handleSupplyChange,
    handleClearSelection,
    handlePerform,
    handleCancel,
    handleCancelSupply,
  };
}
