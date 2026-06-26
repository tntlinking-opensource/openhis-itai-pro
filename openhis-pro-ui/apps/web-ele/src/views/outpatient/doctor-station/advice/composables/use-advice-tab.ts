import type {
  AdviceDrugItem,
  AdviceEditForm,
  AdviceProductOption,
  AdviceRowItem,
  ProductQueryParams,
} from '../services/advice.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceResourceKeyDto,
  DoctorStationAdviceRowDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { DrugGroupDisplayItem } from '#/utils/doctor-station/drug-group-table';

import { computed, nextTick, onActivated, onDeactivated, onUnmounted, ref, watch } from 'vue';

import { ElMessage, ElTable } from 'element-plus';

import {
  httpInitDoctorStationAdvice,
  httpListDoctorStationAdvice,
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationAdviceDraftBatch,
  httpSignDoctorStationAdviceBatch,
  httpUpdateDoctorStationMedicationAdviceGroups,
  httpWithdrawDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { saveAdviceDraftBatchWithBindResolution } from '../services/advice-draft-bind.service';
import { createAdviceDrugItem, inferDoseBandConvertK } from '../services/advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
  prepickedKeyFromListRow,
} from '../services/advice-edit-hydrate';
import { defaultContractSelection, firstTerminologyCode } from '../services/advice-format';
import {
  flattenAdviceRowsForResourceOps,
  getAdviceStatusTagType,
  mapCatalogRowToOption,
  mergeMedicationAdviceRowsForListDisplay,
} from '../services/advice-list';
import { buildAdviceDraftLines } from '../services/advice-medication-draft';
import {
  inferAdviceLineEditorKind,
  productTableNameForAdviceRow,
  resourceKeyFromRow,
  rowLooksDeletable,
  rowLooksDraft,
  rowLooksDraftForSign,
  rowLooksWithdrawable,
  validateWesternEditLineItem,
} from '../services/advice-row';
import {
  ADVICE_ESC_OVERLAY_SELECTOR,
  ADVICE_PRODUCT_COLUMNS,
  WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
} from '../services/advice-tab.constants';
import { calcAdviceTotalFee } from './use-advice-drug-list';

export interface UseAdviceTabProps {
  encounterId?: string;
  patientId?: string;
}

/**
 * 门诊医生站「开立医嘱」页签：列表、内嵌编辑卡、中药弹窗、签发/撤回/删除。
 */
export function useAdviceTab(props: UseAdviceTabProps) {
  const authStore = useAuthStore();
  const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

  const medicationRateOptions = ref<TerminologyOptionDto[]>([]);
  const medicationRouteOptions = ref<TerminologyOptionDto[]>([]);
  const feeNatureOptions = ref<TerminologyOptionDto[]>([]);

  const adviceTableRef = ref<InstanceType<typeof ElTable>>();
  const tableSelection = ref<AdviceRowItem[]>([]);

  const rows = ref<AdviceRowItem[]>([]);
  /** 医嘱主表列表拉取中（httpListDoctorStationAdvice） */
  const adviceListLoading = ref(false);
  /** 保存 / 签发 / 撤回 / 删除 / 刷新 等写操作进行中（含紧随的列表刷新） */
  const adviceMutationLoading = ref(false);
  const showEditor = ref(false);
  const editorDirty = ref(false);
  const editorInitialGroup = ref<AdviceDrugItem[]>([]);
  const editorOpening = ref(false);

  const productColumns = ADVICE_PRODUCT_COLUMNS;

  const defaultContractSelectionRef = computed(() =>
    defaultContractSelection(feeNatureOptions.value),
  );

  /** 表格遮罩：纯列表拉取或写操作任一进行中 */
  const adviceWorkspaceTableLoading = computed(
    () => adviceListLoading.value || adviceMutationLoading.value,
  );

  /** 顶栏表单 / 新建行：术语默认取 IF-DOR-18 接口首项 */
  function terminologyDefaults() {
    return {
      frequency: firstTerminologyCode(medicationRateOptions.value),
      usage: firstTerminologyCode(medicationRouteOptions.value),
    };
  }

  /** 新开西药编辑卡时顶栏表单初始状态 */
  const createEditForm = (): AdviceEditForm => {
    const def = defaultContractSelectionRef.value;
    return {
      orderType: '',
      selectedId: '',
      diagnosis: '',
      contractId: def.contractId || undefined,
      feeNature: def.feeNature,
      pharmacy: '',
      singleDose: undefined,
      frequency: terminologyDefaults().frequency,
      usage: terminologyDefaults().usage,
      medicationDays: undefined,
      totalAmount: undefined,
    };
  };

  const editorForm = ref<AdviceEditForm>(createEditForm());
  const editorPatientDiagnoses = ref<DiagnosisItem[]>([]);
  /** 双击进入编辑时带入的列表行资源类型；空表示新增西药医嘱卡片 */
  const editingAdviceResourceType = ref('');
  /** 递增后让编辑卡内当前展开行的选药下拉自动展开 */
  const orderSelectExpandNonce = ref(0);
  /** 递增后仅聚焦选药检索框；保存成功后配合 Enter 再展开下拉 */
  const orderSelectFocusNonce = ref(0);

  /** 暂存成功后保留编辑区时的空白药品行 */
  function createEmptyWesternDrugLine(): AdviceDrugItem {
    const t = terminologyDefaults();
    const def = defaultContractSelectionRef.value;
    return {
      orderType: '',
      selectedId: '',
      diagnosis: '',
      contractId: def.contractId || undefined,
      feeNature: def.feeNature,
      skinTest: false,
      pharmacy: '',
      singleDose: undefined,
      frequency: t.frequency,
      usage: t.usage,
      medicationDays: undefined,
      totalAmount: undefined,
      selectedDrugName: '',
      selectedDrugSpec: '',
      selectedDrugPrice: '',
      doseMainUnit: '',
      doseSubUnit: '',
      doseConvertRatio: undefined,
      totalAmountUnit: '',
      expanded: true,
      /** 保存后新开一行续录时使用新组，避免误入上一处方组号 */
      groupId: String(Date.now()),
    };
  }

  /** 驱动行内选药组件自动展开下拉 */
  function bumpOrderSelectExpand() {
    orderSelectExpandNonce.value += 1;
  }

  /** 仅聚焦选药检索输入 */
  function bumpOrderSelectFocusTrigger() {
    orderSelectFocusNonce.value += 1;
  }

  /** 西药编辑卡单行：药品 / 诊疗 / 耗材分支校验（与 advice-edit-card 一致） */
  function validateWesternRow(item: AdviceDrugItem) {
    return validateWesternEditLineItem(item);
  }

  /** 再增一行药品前的门禁 */
  function addDrugGuardWestern(ctx: { drugItems: AdviceDrugItem[]; isTcm: boolean }) {
    if (ctx.isTcm) return true;
    const last = ctx.drugItems[ctx.drugItems.length - 1];
    if (last && !last.diagnosis && editorPatientDiagnoses.value.length === 0) {
      return '请先开立诊断后再新增医嘱，可在诊断字段右侧点击“新增”直接开诊断';
    }
    return true;
  }

  const calcDrugLineTotalFee = calcAdviceTotalFee;

  /** 列表 `groupItems` 是否已是编辑卡可识别的 `AdviceDrugItem`（与 DrugGroupDisplayItem 区分） */
  function isAdviceDrugEditorLine(
    item: AdviceDrugItem | DrugGroupDisplayItem,
  ): item is AdviceDrugItem {
    return 'selectedId' in item;
  }

  /** 表格数据：编辑模式时在首行插入占位 editor 行 */
  const getTableRows = () => {
    if (!showEditor.value) return rows.value;
    return [
      {
        key: '__editor__',
        isEditor: true,
        status: '',
        type: '',
        content: '',
        startTime: '',
        extension: '',
        lineTotalAmount: '',
        diagnosisSummary: '',
        orderingDoctor: '',
        skinTestText: '',
      } as AdviceRowItem,
      ...rows.value,
    ];
  };

  /** Element 合并单元格：editor 行占满除选择列外的所有列 */
  const handleSpanMethod = ({ columnIndex, row }: { columnIndex: number; row: AdviceRowItem }) => {
    if (!row.isEditor) return undefined;
    if (columnIndex === 0) return { rowspan: 0, colspan: 0 };
    if (columnIndex === 1) return { rowspan: 1, colspan: 999 };
    return { rowspan: 0, colspan: 0 };
  };

  /** 编辑行隐藏列加 class */
  const getCellClassName = ({ columnIndex, row }: { columnIndex: number; row: AdviceRowItem }) => {
    if (row.isEditor && columnIndex === 1) return 'advice-editor-cell';
    return '';
  };

  /** 打开空白西药编辑卡片 */
  const handleAdd = async () => {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('当前编辑卡片有未保存数据，请先保存或关闭');
      return;
    }
    const ok = await ensureReceptionBeforeClinicalWork();
    if (!ok) return;
    editingAdviceResourceType.value = '';
    editorInitialGroup.value = [];
    showEditor.value = true;
    editorForm.value = createEditForm();
    void nextTick(() => {
      bumpOrderSelectExpand();
    });
  };

  /** 过滤虚拟 editor 行 */
  function onAdviceSelectionChange(selection: AdviceRowItem[]) {
    tableSelection.value = selection.filter((r) => !r.isEditor);
  }

  function clearAdviceTableSelection() {
    adviceTableRef.value?.clearSelection();
    tableSelection.value = [];
  }

  async function withAdviceMutationLoading(fn: () => Promise<void>) {
    adviceMutationLoading.value = true;
    try {
      await fn();
    } finally {
      adviceMutationLoading.value = false;
    }
  }

  /** 将列表中 DRAFT 态行批量提交签发 */
  const handleSign = async () => {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('请先保存或关闭当前编辑卡片');
      return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const draftRows = flattenAdviceRowsForResourceOps(rows.value).filter((r) =>
      rowLooksDraftForSign(r),
    );
    const targets = draftRows
      .map((r) => resourceKeyFromRow(r))
      .filter(Boolean) as DoctorStationAdviceResourceKeyDto[];
    if (targets.length === 0) {
      ElMessage.warning('当前列表没有状态为 DRAFT 的医嘱可签发');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpSignDoctorStationAdviceBatch({
          encounterId,
          patientId,
          signTargets: targets,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '签发失败');
          return;
        }
        ElMessage.success(String(res.message || '').trim() || '签发成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签发失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 将勾选行批量签退 */
  const handleWithdraw = async () => {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('请先保存或关闭当前编辑卡片');
      return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const flatSel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const withdrawable = flatSel.filter((r) => rowLooksWithdrawable(r, rowLooksDraft));
    const targets = withdrawable
      .map((r) => resourceKeyFromRow(r))
      .filter(Boolean) as DoctorStationAdviceResourceKeyDto[];
    if (targets.length === 0) {
      ElMessage.warning('请勾选至少一条已签发且可签退的医嘱');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpWithdrawDoctorStationAdviceBatch({
          encounterId,
          patientId,
          targets,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '签退失败');
          return;
        }
        ElMessage.success(String(res.message || '').trim() || '签退成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签退失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 将勾选草稿行以 DELETE 行方式提交暂存批次 */
  const handleDelete = async () => {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('请先保存或关闭当前编辑卡片');
      return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const flatSelDel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const deletable = flatSelDel.filter((r) => rowLooksDeletable(r, rowLooksDraft));
    const lines: DoctorStationAdviceDraftLineRequest[] = deletable.map((row) => {
      const rid = String(row.requestId ?? '').trim();
      const ptn = productTableNameForAdviceRow(row);
      return {
        lineOp: 'DELETE',
        requestId: rid,
        ...(ptn ? { productTableName: ptn } : {}),
      };
    });
    if (lines.length === 0) {
      ElMessage.warning('请勾选至少一条可删除的草稿医嘱');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpSaveDoctorStationAdviceDraftBatch({
          encounterId,
          patientId,
          lines,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '删除失败');
          return;
        }
        ElMessage.success('删除成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '删除失败';
        ElMessage.error(msg);
      }
    });
  };

  function handleToolbarPlaceholder(featureLabel: string) {
    ElMessage.info(`${featureLabel}功能开发中`);
  }

  /** 勾选行中：西药 + 草稿 + 有 `requestId`（IF-DOR-17 仅药品申请主键可操作组号）。 */
  function medicationDraftRowsWithRequestId(from: AdviceRowItem[]) {
    return from.filter((r) => {
      if (r.isEditor) return false;
      if (!rowLooksDraft(r)) return false;
      const rt = String(r.resourceType ?? '')
        .trim()
        .toUpperCase();
      if (rt !== 'MEDICATION') return false;
      return Boolean(String(r.requestId ?? '').trim());
    });
  }

  /** 签发/批量写前：编辑卡不能有未保存内容 */
  function assertEditorSavedForToolbarBatch(): boolean {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('请先保存或关闭当前编辑卡片');
      return false;
    }
    return true;
  }

  /** IF-DOR-17：拆组（`clearGroupIds` → domain `group_id=null`） */
  const handleMedicationUngroup = async () => {
    if (!assertEditorSavedForToolbarBatch()) return;
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const flatUg = flattenAdviceRowsForResourceOps(tableSelection.value);
    const meds = medicationDraftRowsWithRequestId(flatUg);
    const withGroup = meds.filter((r) => String(r.groupId ?? '').trim() !== '');
    if (withGroup.length === 0) {
      ElMessage.warning('请勾选已并组（带组号）的草稿药品医嘱');
      return;
    }
    const clearGroupIds = withGroup.map((r) => String(r.requestId).trim());
    try {
      const res = await httpUpdateDoctorStationMedicationAdviceGroups({
        encounterId,
        patientId,
        clearGroupIds,
      });
      if (res.success === false) {
        ElMessage.error(String(res.message || '').trim() || '拆组失败');
        return;
      }
      ElMessage.success(String(res.message || '').trim() || '拆组成功');
      clearAdviceTableSelection();
      await loadAdviceList();
    } catch (error: unknown) {
      const msg =
        error && typeof error === 'object' && 'message' in error
          ? String((error as { message?: unknown }).message)
          : '拆组失败';
      ElMessage.error(msg);
    }
  };

  /** IF-DOR-17：并组（`assignGroup` → 写入同一 `group_id`） */
  const handleMedicationAssignGroup = async () => {
    if (!assertEditorSavedForToolbarBatch()) return;
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const flatAg = flattenAdviceRowsForResourceOps(tableSelection.value);
    const meds = medicationDraftRowsWithRequestId(flatAg);
    if (meds.length === 0) {
      ElMessage.warning('请勾选至少一条草稿药品医嘱（需已暂存含 requestId）');
      return;
    }
    /** 每组并组操作使用新的毫秒时间戳，保证与近期其他并组组号不冲突 */
    const groupIdRaw = String(Date.now());
    try {
      const res = await httpUpdateDoctorStationMedicationAdviceGroups({
        encounterId,
        patientId,
        assignGroup: {
          groupId: groupIdRaw,
          medicationRequestIds: meds.map((r) => String(r.requestId).trim()),
        },
      });
      if (res.success === false) {
        ElMessage.error(String(res.message || '').trim() || '并组失败');
        return;
      }
      ElMessage.success(String(res.message || '').trim() || '并组成功');
      clearAdviceTableSelection();
      await loadAdviceList();
    } catch (error: unknown) {
      const msg =
        error && typeof error === 'object' && 'message' in error
          ? String((error as { message?: unknown }).message)
          : '并组失败';
      ElMessage.error(msg);
    }
  };

  /** 选中就诊变化时拉取医嘱主表 */
  async function loadAdviceList() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      rows.value = [];
      adviceListLoading.value = false;
      return;
    }
    adviceListLoading.value = true;
    try {
      const res = await httpListDoctorStationAdvice({
        encounterId,
        patientId,
        onlyMine: false,
      });
      const { content: list } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(res);
      rows.value = mergeMedicationAdviceRowsForListDisplay(list);
    } catch {
      rows.value = [];
    } finally {
      adviceListLoading.value = false;
    }
  }

  /** IF-DOR-18：术语下拉 */
  async function loadAdviceInit() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      medicationRateOptions.value = [];
      medicationRouteOptions.value = [];
      feeNatureOptions.value = [];
      return;
    }
    try {
      const res = await httpInitDoctorStationAdvice({ encounterId, patientId });
      medicationRateOptions.value = res.medicationRateOptions ?? [];
      medicationRouteOptions.value = res.medicationRouteOptions ?? [];
      feeNatureOptions.value = res.feeNatureOptions ?? [];
    } catch {
      medicationRateOptions.value = [];
      medicationRouteOptions.value = [];
      feeNatureOptions.value = [];
    }
  }

  /** 医嘱目录远程分页 */
  const queryProductOptions = async (params: ProductQueryParams) => {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      return { content: [] as AdviceProductOption[], totalElements: 0 };
    }
    const orgRaw = authStore.getSelectedOrganization();
    const organizationUnitId =
      orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
        ? String(orgRaw)
        : undefined;
    try {
      const res = await httpPageDoctorStationAdviceCatalog({
        encounterId,
        patientId,
        organizationUnitId,
        keyword: params.keyword?.trim() || undefined,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        productTableNames: params.productTableNames,
        excludeMedicationCategoryCodes: [
          ...WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
        ],
      });
      const { content: rawRows, totalCount } =
        parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
      const content = rawRows.map((item) => mapCatalogRowToOption(item));
      return { content, totalElements: totalCount };
    } catch {
      return { content: [] as AdviceProductOption[], totalElements: 0 };
    }
  };

  /** 按列表行 productDefinitionId 拉取目录基础信息（prepickedProductKeys） */
  async function fetchCatalogOptionsByPrepickedKeys(
    keys: PrepickedProductKeyDto[],
  ): Promise<AdviceProductOption[]> {
    if (keys.length === 0) return [];
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) return [];
    const orgRaw = authStore.getSelectedOrganization();
    const organizationUnitId =
      orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
        ? String(orgRaw)
        : undefined;
    try {
      const res = await httpPageDoctorStationAdviceCatalog({
        encounterId,
        patientId,
        organizationUnitId,
        pageNo: 1,
        pageSize: Math.max(keys.length, 20),
        prepickedProductKeys: keys,
      });
      const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
      return rawRows.map((item) => mapCatalogRowToOption(item));
    } catch {
      return [];
    }
  }

  /** 编辑回填：目录基础信息 + 列表行已开立字段（含单次剂量换算） */
  async function hydrateAdviceDrugItemsForEdit(rows: AdviceRowItem[]): Promise<AdviceDrugItem[]> {
    const keys = rows
      .map((row) => prepickedKeyFromListRow(row))
      .filter((k): k is PrepickedProductKeyDto => k !== null);
    const catalogList = await fetchCatalogOptionsByPrepickedKeys(keys);
    const catalogMap = new Map<string, AdviceProductOption>();
    for (const opt of catalogList) {
      const id = String(opt.productDefinitionId ?? '').trim();
      const tbl = String(opt.productTableName ?? '').trim();
      if (id && tbl) catalogMap.set(`${tbl}:${id}`, opt);
    }
    const ctx = {
      feeNatureOptions: feeNatureOptions.value,
      medicationRateOptions: medicationRateOptions.value,
      medicationRouteOptions: medicationRouteOptions.value,
    };

    return rows.map((row, index) => {
      const seed = buildAdviceDrugItemSeedFromListRow(row);
      const key = prepickedKeyFromListRow(row);
      const catalog = key
        ? catalogMap.get(`${key.productTableName}:${key.productDefinitionId}`)
        : undefined;
      const base = createAdviceDrugItem(seed, ctx);
      const merged = catalog
        ? mergeCatalogIntoAdviceDrugItemForEdit({ ...base, ...seed }, catalog, {
            resourceType: row.resourceType,
          })
        : seed;
      return inferDoseBandConvertK({
        ...base,
        ...merged,
        medicationRequestId:
          String(row.requestId ?? '').trim() || seed.medicationRequestId || undefined,
        expanded: index === 0,
      } as AdviceDrugItem);
    });
  }

  const handleCloseEditor = () => {
    showEditor.value = false;
    editorDirty.value = false;
    editingAdviceResourceType.value = '';
    editorInitialGroup.value = [];
    editorPatientDiagnoses.value = [];
  };

  function isInAdviceEscOverlay(target: EventTarget | null): boolean {
    const el = target instanceof HTMLElement ? target : null;
    return Boolean(el?.closest?.(ADVICE_ESC_OVERLAY_SELECTOR));
  }

  let adviceEscKeydownAttached = false;

  function onAdviceTabDocumentKeydown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return;
    if (!showEditor.value) return;
    if (isInAdviceEscOverlay(event.target)) return;
    if (isInAdviceEscOverlay(document.activeElement)) return;

    event.preventDefault();
    event.stopPropagation();
    handleCloseEditor();
  }

  function attachAdviceEscKeydown() {
    if (adviceEscKeydownAttached) return;
    document.addEventListener('keydown', onAdviceTabDocumentKeydown, true);
    adviceEscKeydownAttached = true;
  }

  function detachAdviceEscKeydown() {
    if (!adviceEscKeydownAttached) return;
    document.removeEventListener('keydown', onAdviceTabDocumentKeydown, true);
    adviceEscKeydownAttached = false;
  }

  /** 编辑卡「保存」 */
  const handleSaveGroup = async (
    groupItems: AdviceDrugItem[],
    encounterDiagnosisRows: EncounterDiagnosisRowDto[] = [],
  ) => {
    if (groupItems.length === 0) return;
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息，无法保存');
      return;
    }
    const invalidEntry = groupItems
      .map((item, i) => ({ item, i }))
      .find(({ item }) => !validateWesternRow(item));
    if (invalidEntry) {
      const { item, i } = invalidEntry;
      const n = i + 1;
      const kind = String(item.selectedId ?? '').trim()
        ? inferAdviceLineEditorKind(item)
        : 'MEDICATION';
      if (kind === 'SERVICE') {
        ElMessage.warning(`第${n}条诊疗医嘱：请完善数量、执行次数与执行科室`);
      } else if (kind === 'DEVICE') {
        ElMessage.warning(`第${n}条耗材医嘱：数量须大于 0，并须填写单位`);
      } else {
        ElMessage.warning(
          `第${n}条药品医嘱：请完善诊断、费用性质、用法、频次、单次用量、总量与单位等必填项`,
        );
      }
      return;
    }
    let lines: DoctorStationAdviceDraftLineRequest[];
    try {
      lines = buildAdviceDraftLines(groupItems, editorPatientDiagnoses.value, {
        encounterDiagnosisRows,
        existingAdviceRows: rows.value,
      });
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '请求组包失败');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await saveAdviceDraftBatchWithBindResolution({
          encounterId,
          patientId,
          lines,
          existingAdviceRows: rows.value,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '保存失败');
          return;
        }
        ElMessage.success('保存成功');
        await loadAdviceList();
        editingAdviceResourceType.value = '';
        editorDirty.value = false;
        editorInitialGroup.value = [createEmptyWesternDrugLine()];
        void nextTick(() => {
          void nextTick(() => {
            bumpOrderSelectFocusTrigger();
          });
        });
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '保存失败';
        ElMessage.error(msg);
      }
    });
  };

  const handleEditorDirtyChange = (dirty: boolean) => {
    editorDirty.value = dirty;
  };

  /** 双击草稿行进入编辑 */
  const handleRowDblclick = async (row: AdviceRowItem) => {
    if (row.isEditor) return;
    if (!rowLooksDraft(row)) {
      ElMessage.warning('仅草稿/待签发类医嘱允许编辑');
      return;
    }
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('卡片中有未保存数据，不允许编辑其他行');
      return;
    }
    editingAdviceResourceType.value = (row.resourceType || '').trim();
    const members = row.mergedAdviceRows;
    editorOpening.value = true;
    try {
      if (members !== undefined && members.length >= 2) {
        editorInitialGroup.value = await hydrateAdviceDrugItemsForEdit(members);
      } else if (
        row.groupItems &&
        row.groupItems.length > 0 &&
        row.groupItems[0] !== undefined &&
        isAdviceDrugEditorLine(row.groupItems[0])
      ) {
        const editItems = row.groupItems as AdviceDrugItem[];
        editorInitialGroup.value = editItems.map((item, index) => ({
          ...item,
          expanded: index === 0,
          medicationRequestId:
            item.medicationRequestId?.trim() || String(row.requestId ?? '').trim() || undefined,
        }));
      } else {
        editorInitialGroup.value = await hydrateAdviceDrugItemsForEdit([row]);
      }
      showEditor.value = true;
    } catch {
      ElMessage.error('加载医嘱编辑数据失败');
    } finally {
      editorOpening.value = false;
    }
  };

  /** Tab 激活或切换患者后：先接诊再拉列表 */
  async function loadAdviceTabData() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      rows.value = [];
      medicationRateOptions.value = [];
      medicationRouteOptions.value = [];
      feeNatureOptions.value = [];
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) {
      return;
    }
    await Promise.all([loadAdviceList(), loadAdviceInit()]);
  }

  /** KeepAlive 内仅激活页签拉数；换患者时由 watch 刷新当前可见 Tab */
  const isTabActive = ref(false);

  watch(
    () => [props.encounterId, props.patientId],
    () => {
      if (!isTabActive.value) return;
      if (showEditor.value) {
        showEditor.value = false;
        editorDirty.value = false;
        editingAdviceResourceType.value = '';
      }
      clearAdviceTableSelection();
      void loadAdviceTabData();
    },
  );

  onActivated(() => {
    isTabActive.value = true;
    attachAdviceEscKeydown();
    void loadAdviceTabData();
  });

  onDeactivated(() => {
    isTabActive.value = false;
    detachAdviceEscKeydown();
  });

  onUnmounted(() => {
    detachAdviceEscKeydown();
  });

  /** 对外：父级 KeepAlive 刷新 */
  async function reload() {
    await withAdviceMutationLoading(async () => {
      await loadAdviceTabData();
    });
  }

  return {
    adviceMutationLoading,
    adviceTableRef,
    adviceWorkspaceTableLoading,
    addDrugGuardWestern,
    bumpOrderSelectExpand,
    bumpOrderSelectFocusTrigger,
    calcDrugLineTotalFee,
    clearAdviceTableSelection,
    defaultContractSelectionRef,
    feeNatureOptions,
    editorDirty,
    editorForm,
    editorInitialGroup,
    editorOpening,
    editorPatientDiagnoses,
    encounterId: computed(() => props.encounterId ?? ''),
    getAdviceStatusTagType,
    getCellClassName,
    getTableRows,
    handleAdd,
    handleCloseEditor,
    handleDelete,
    handleEditorDirtyChange,
    handleMedicationAssignGroup,
    handleMedicationUngroup,
    handleRowDblclick,
    handleSaveGroup,
    handleSign,
    handleSpanMethod,
    handleToolbarPlaceholder,
    handleWithdraw,
    loadAdviceInit,
    loadAdviceList,
    medicationRateOptions,
    medicationRouteOptions,
    onAdviceSelectionChange,
    orderSelectExpandNonce,
    orderSelectFocusNonce,
    patientId: computed(() => props.patientId ?? ''),
    productColumns,
    queryProductOptions,
    reload,
    showEditor,
    validateWesternRow,
  };
}
