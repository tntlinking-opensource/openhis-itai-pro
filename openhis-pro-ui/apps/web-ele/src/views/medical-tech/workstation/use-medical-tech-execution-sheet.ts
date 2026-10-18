import type {
  MedicalTechBoundSupplyItemDto,
  MedicalTechCancelDeviceItemDto,
  MedicalTechCancelProcedureItemDto,
  MedicalTechEncounterScope,
  MedicalTechExecutingOrgOptionDto,
  MedicalTechExecutionLineDto,
  MedicalTechExecutionQueueFilter,
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
import {
  ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE,
  isEncounterClinicalOrderMutationBlocked,
} from '#/utils/inpatient/encounter-clinical-order-gate';

import {
  canCancelMedicalTechDeviceLine,
  canCancelMedicalTechServiceLine,
  canExecuteMedicalTechDeviceLine,
  canExecuteMedicalTechServiceLine,
} from './medical-tech-execution-selection';
import {
  buildMedicalTechDeviceLineKey,
  toMedicalTechBoundSupplyItems,
  toMedicalTechSupplyItems,
} from './medical-tech-patient-map';

export interface UseMedicalTechExecutionSheetOptions {
  encounterScope: Ref<MedicalTechEncounterScope>;
  /** 左侧队列执行态；装载执行单时同传，收窄右侧项目 */
  executionQueueFilter: Ref<MedicalTechExecutionQueueFilter>;
  selectedEncounterId: Ref<string>;
  selectedPatientSummary: Ref<MedicalTechPatientQueueItemDto | null>;
  reloadPatientQueue: () => Promise<void>;
  /** 执行/取消后刷新最近执行记录 */
  reloadProcedureRecords?: () => Promise<void>;
  /** 同患者再点 / 队列刷新时递增，强制重拉执行单 */
  contentReloadToken: Ref<number>;
}

/**
 * 医技执行单：诊疗/耗材明细、勾选与批执行/取消（壳层右栏与底栏共用）。
 */
export function useMedicalTechExecutionSheet(options: UseMedicalTechExecutionSheetOptions) {
  const {
    encounterScope,
    executionQueueFilter,
    selectedEncounterId,
    selectedPatientSummary,
    reloadPatientQueue,
    reloadProcedureRecords,
    contentReloadToken,
  } = options;

  const serviceLines = ref<MedicalTechExecutionLineDto[]>([]);
  const deviceLines = ref<MedicalTechExecutionLineDto[]>([]);
  /** 住院开立带出耗材预览（只读） */
  const boundSupplyItems = ref<MedicalTechBoundSupplyItemDto[]>([]);
  /** 共看组项目类型筛选项（CT/DR，按执行科室区分）；空则不展示 */
  const sharedExecutingOrgFilterOptions = ref<MedicalTechExecutingOrgOptionDto[]>([]);
  const selectedServiceRows = ref<MedicalTechExecutionLineDto[]>([]);
  /** 侧栏已勾选耗材卡片 id（requestId+dispenseId） */
  const selectedDeviceKeys = ref<string[]>([]);
  /** 诊疗表实例：清空勾选 */
  let clearTableSelectionFn: (() => void) | null = null;

  /** 耗材卡片：门诊=可执行发放行；住院=发放行 + 开立带出预览（只读） */
  const supplyItems = computed(() => {
    const fromDevice = toMedicalTechSupplyItems(deviceLines.value);
    if (encounterScope.value !== 'INPATIENT') {
      return fromDevice;
    }
    // 住院只读：去掉取消执行按钮，开立带出在前便于核对
    const readonlyDevice = fromDevice.map((item) => ({
      ...item,
      canCancelExecute: false,
    }));
    return [...toMedicalTechBoundSupplyItems(boundSupplyItems.value), ...readonlyDevice];
  });

  /** 已选诊疗数 */
  const selectedServiceCount = computed(() => selectedServiceRows.value.length);

  /** 已选耗材数 */
  const selectedDeviceCount = computed(() => selectedDeviceKeys.value.length);

  /** 按勾选 key 取耗材行 */
  const selectedDeviceRows = computed(() => {
    const keySet = new Set(selectedDeviceKeys.value);
    return deviceLines.value.filter((row) => keySet.has(buildMedicalTechDeviceLineKey(row)));
  });

  /** 已勾选且尚可执行的合计（底栏「批量执行」角标） */
  const totalSelectedCount = computed(() => {
    let count = 0;
    for (const row of selectedServiceRows.value) {
      if (canExecuteMedicalTechServiceLine(row)) {
        count += 1;
      }
    }
    for (const row of selectedDeviceRows.value) {
      if (canExecuteMedicalTechDeviceLine(row)) {
        count += 1;
      }
    }
    return count;
  });

  /**
   * 住院待结算/已结算/已离院：禁止医技执行与取消（对齐护士站/医生站）。
   */
  const hasEncounterClinicalOrderBlocked = computed(() => {
    if (encounterScope.value !== 'INPATIENT') {
      return false;
    }
    return isEncounterClinicalOrderMutationBlocked(selectedPatientSummary.value?.statusEnum);
  });

  /** 待结算轨拦截文案（底栏上方黄条） */
  const clinicalOrderBlockedMessage = ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE;

  /** 是否可点批量执行：未拦且勾选中至少一行尚可执行 */
  const canExecuteSelected = computed(
    () => !hasEncounterClinicalOrderBlocked.value && totalSelectedCount.value > 0,
  );

  /** 是否可点批量取消：未拦且勾选中至少一行净剩可取消 */
  const canCancelSelected = computed(() => {
    if (hasEncounterClinicalOrderBlocked.value) {
      return false;
    }
    for (const row of selectedServiceRows.value) {
      if (canCancelMedicalTechServiceLine(row)) {
        return true;
      }
    }
    for (const row of selectedDeviceRows.value) {
      if (canCancelMedicalTechDeviceLine(row)) {
        return true;
      }
    }
    return false;
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
        boundSupplyItems.value = data.boundSupplyItems ?? [];
        sharedExecutingOrgFilterOptions.value = data.sharedExecutingOrgFilterOptions ?? [];
        // 门诊：耗材按状态默认勾选；住院耗材只读展示，不参与勾选执行
        if (encounterScope.value === 'OUTPATIENT') {
          selectedDeviceKeys.value = collectDefaultSelectedDeviceKeys(nextDeviceLines);
        } else {
          selectedDeviceKeys.value = [];
        }
        // 诊疗表默认勾选由 execution-panel 按执行态监听 serviceLines 后勾选
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
   * 门诊耗材默认勾选：优先待发放；无待发时勾可取消发放行（与诊疗默认态策略一致）。
   *
   * @param rows 本页耗材执行行
   * @return 默认勾选的卡片 id
   */
  function collectDefaultSelectedDeviceKeys(rows: MedicalTechExecutionLineDto[]): string[] {
    const pendingKeys: string[] = [];
    for (const row of rows) {
      if (row.selectable === false) {
        continue;
      }
      if (!canExecuteMedicalTechDeviceLine(row)) {
        continue;
      }
      pendingKeys.push(buildMedicalTechDeviceLineKey(row));
    }
    // 仍有待发放 → 默认勾待发放，便于批量执行
    if (pendingKeys.length > 0) {
      return pendingKeys;
    }
    const cancelKeys: string[] = [];
    for (const row of rows) {
      if (row.selectable === false) {
        continue;
      }
      if (!canCancelMedicalTechDeviceLine(row)) {
        continue;
      }
      cancelKeys.push(buildMedicalTechDeviceLineKey(row));
    }
    // 全部已发放 → 默认勾可取消行，便于取消执行
    return cancelKeys;
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
      boundSupplyItems.value = [];
      sharedExecutingOrgFilterOptions.value = [];
      selectedServiceRows.value = [];
      selectedDeviceKeys.value = [];
      return;
    }
    try {
      // 与左侧待执行/已执行同码，后端按 BR-MT-EXE-023 收窄右侧项目
      await runLoadSheet({
        encounterId,
        encounterScope: encounterScope.value,
        executionQueueFilter: executionQueueFilter.value,
      });
    } catch {
      serviceLines.value = [];
      deviceLines.value = [];
      boundSupplyItems.value = [];
      sharedExecutingOrgFilterOptions.value = [];
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
    // 住院待结算轨：前端二次拦截，避免误点底栏
    if (hasEncounterClinicalOrderBlocked.value) {
      ElMessage.warning(
        `${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（不可执行或取消执行）`,
      );
      return;
    }

    const serviceItems: MedicalTechPerformItemDto[] = [];
    for (const row of selectedServiceRows.value) {
      // 满额已执行行可勾选取消，但不进入批量执行载荷
      if (!canExecuteMedicalTechServiceLine(row)) {
        continue;
      }
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) {
        continue;
      }
      serviceItems.push({
        requestId,
        requestTable: row.requestTable,
      });
    }

    const deviceItems: MedicalTechPerformItemDto[] = [];
    for (const row of selectedDeviceRows.value) {
      // 已发放可勾选取消，随批执行仅收待发放
      if (!canExecuteMedicalTechDeviceLine(row)) {
        continue;
      }
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) {
        continue;
      }
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
    // 住院待结算轨：前端二次拦截
    if (hasEncounterClinicalOrderBlocked.value) {
      ElMessage.warning(
        `${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（不可执行或取消执行）`,
      );
      return;
    }

    // 仅保留净剩可取消的勾选行，避免待执行行误入取消路径
    const cancelServiceRows = serviceRows.filter((row) => canCancelMedicalTechServiceLine(row));
    const cancelDeviceRows = deviceRows.filter((row) => canCancelMedicalTechDeviceLine(row));
    if (cancelServiceRows.length === 0 && cancelDeviceRows.length === 0) {
      ElMessage.warning('请勾选可取消的诊疗或耗材行');
      return;
    }

    try {
      await ElMessageBox.confirm('确认取消所选执行？', '取消执行', { type: 'warning' });
    } catch {
      return;
    }

    const procedureItems: MedicalTechCancelProcedureItemDto[] = [];
    for (const row of cancelServiceRows) {
      const requestId = preserveApiIdForRequest(row.requestId);
      if (!requestId) {
        continue;
      }
      try {
        const net = await runListNetRemaining({
          encounterId,
          encounterScope: encounterScope.value,
          requestId,
          requestTable: row.requestTable,
        });
        for (const record of net?.records ?? []) {
          const procedureId = preserveApiIdForRequest(record.procedureId);
          if (!procedureId) {
            continue;
          }
          procedureItems.push({ procedureId });
        }
      } catch {
        ElMessage.error(`查询「${row.itemName || requestId}」可取消记录失败`);
        return;
      }
    }

    const deviceItems: MedicalTechCancelDeviceItemDto[] = [];
    for (const row of cancelDeviceRows) {
      const dispenseId = preserveApiIdForRequest(row.dispenseId);
      if (!dispenseId) {
        continue;
      }
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

  // 选中就诊变化，或同患者再点/队列刷新令牌递增：加载或清空执行单
  watch(
    [selectedEncounterId, contentReloadToken],
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
    sharedExecutingOrgFilterOptions,
    selectedDeviceKeys,
    supplyItems,
    sheetLoading,
    selectedServiceCount,
    selectedDeviceCount,
    totalSelectedCount,
    hasEncounterClinicalOrderBlocked,
    clinicalOrderBlockedMessage,
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
