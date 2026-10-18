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
import type { DetailAuditRemindDialogExpose } from '#/chs/composables/use-detail-audit-gate';

import {
  computed,
  nextTick,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  ref,
  watch,
  type Ref,
} from 'vue';

import { ElMessage, ElTable } from 'element-plus';

import {
  httpInitDoctorStationAdvice,
  httpListDoctorStationAdvice,
  httpPageDoctorStationAdviceCatalog,
  httpUpdateDoctorStationMedicationAdviceGroups,
  httpWithdrawDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { useEnsureClinicalReception } from '#/components/doctor-station/use-ensure-clinical-reception';
import { mapEncounterRowToDiagnosisItem } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  clearEncounterDiagnosisSession,
  readEncounterDiagnosisSession,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-session';
import { fetchEncounterDiagnosisDisplayList } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import { runOpDetailAuditGate } from '#/chs/composables/use-detail-audit-gate';
import { pickFirstInsuranceContract } from '#/chs/utils/resolve-insurance-contract-bus-no';
import { getChargeTypeListApi } from '#/api/core/common';
import { useAuthStore } from '#/store';
import { notifyEncounterFeeRefresh } from '#/components/outpatient/encounter-fee-summary';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';

import { fetchAdviceCatalogRowsByPrepickedKeys } from '../services/advice-catalog-prepicked';
import {
  expandAdviceSignTargetsWithBoundDrafts,
  resolveAndPersistAdviceDraftBindsAtSign,
} from '../services/advice-draft-bind.service';
import {
  saveAdviceDraftBatchDirect,
  saveAdviceDraftBatchWithManualUsageBind,
} from '../services/advice-draft-save.service';
import { signDoctorStationAdviceBatchWithCrossLotPricingConfirm } from '../services/advice-cross-lot-pricing-confirm';
import { createAdviceDrugItem, inferDoseBandConvertK } from '../services/advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
  prepickedKeyFromListRow,
} from '../services/advice-edit-hydrate';
import {
  defaultContractSelection,
  enrichFeeNatureOptionsWithCategory,
  firstTerminologyCode,
  type FeeNatureOption,
} from '../services/advice-format';
import {
  enrichAdviceListFeeNatureText,
  excludeServiceApplyFormAdviceRows,
  flattenAdviceRowsForResourceOps,
  getAdviceStatusTagType,
  mapCatalogRowToOption,
  mergeMedicationAdviceRowsForListDisplay,
} from '../services/advice-list';
import {
  buildAdviceDraftDeleteLineFromDrugItem,
  buildAdviceDraftLines,
  collectRemovedAdviceDraftDeleteLines,
  validateMedicationAssignGroupRows,
} from '../services/advice-medication-draft';
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
import { ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES } from '#/views/outpatient/doctor-station/advice/constants/advice-catalog-exclude-service-apply-categories.config';

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
export function useAdviceTab(
  props: UseAdviceTabProps,
  options?: { detailAuditDialogRef?: Ref<DetailAuditRemindDialogExpose | null> },
) {
  const authStore = useAuthStore();
  const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();
  const detailAuditDialogRef = options?.detailAuditDialogRef;

  const medicationRateOptions = ref<TerminologyOptionDto[]>([]);
  const medicationRouteOptions = ref<TerminologyOptionDto[]>([]);
  /** 费用性质下拉（含 categoryEnum，供医保等级=自费时收窄） */
  const feeNatureOptions = ref<FeeNatureOption[]>([]);
  /**
   * 换患者/就诊时递增；并行 init/list 回写前比对，避免慢请求覆盖新患者费用性质。
   */
  let adviceLoadGeneration = 0;

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
      frequency: '',
      usage: terminologyDefaults().usage,
      medicationDays: undefined,
      totalAmount: undefined,
    };
  };

  const editorForm = ref<AdviceEditForm>(createEditForm());
  const editorPatientDiagnoses = ref<DiagnosisItem[]>([]);
  /** 列表「诊断」列慢病角标：与编辑卡无关，随就诊拉数 */
  const listDiagnosisItems = ref<DiagnosisItem[]>([]);
  /** 当前就诊开立会话内上次选中的诊断 ids（刷新页面后清空） */
  const lastSelectedEncounterDiagnosisIds = ref<string[]>(
    readEncounterDiagnosisSession(props.encounterId),
  );

  /** 列表优先用编辑卡已刷新诊断，否则用独立拉数结果 */
  const diagnosisItemsForList = computed(() => {
    if (editorPatientDiagnoses.value.length > 0) {
      return editorPatientDiagnoses.value;
    }
    return listDiagnosisItems.value;
  });

  /** 编辑卡诊断变更时同步列表角标数据 */
  watch(editorPatientDiagnoses, (items) => {
    if (items.length > 0) {
      listDiagnosisItems.value = items;
    }
  });
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
      frequency: '',
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
        chargeStatus: '',
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

  /** 签发前查询就诊诊断，未开立则拦截并提示 */
  async function ensureEncounterHasDiagnosisForSign(
    encounterId: string,
    patientId: string,
  ): Promise<boolean> {
    try {
      const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
      const list = res?.diagnosisList ?? res?.content ?? [];
      if (Array.isArray(list) && list.length > 0) {
        return true;
      }
      ElMessage.warning('当前患者尚未开立诊断，请先到「诊断」页签开立诊断后再签发医嘱');
      return false;
    } catch (error: unknown) {
      const msg =
        error && typeof error === 'object' && 'message' in error
          ? String((error as { message?: unknown }).message)
          : '';
      ElMessage.error(msg.trim() || '查询诊断信息失败，请稍后重试');
      return false;
    }
  }

  /** 将列表中 DRAFT 态行批量提交签发 */
  /**
   * 签发：有勾选则只签发勾选中的草稿；未勾选则签发当前列表全部草稿。
   */
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    // 勾选优先；未勾选回落全表草稿
    const flatSel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const useSelection = flatSel.length > 0;
    const candidateRows = useSelection ? flatSel : flattenAdviceRowsForResourceOps(rows.value);
    // 仅 DRAFT 可进签发目标
    const draftRows = candidateRows.filter((r) => rowLooksDraftForSign(r));
    const targets = draftRows
      .map((r) => resourceKeyFromRow(r))
      .filter(Boolean) as DoctorStationAdviceResourceKeyDto[];
    if (targets.length === 0) {
      ElMessage.warning(
        useSelection
          ? '勾选行中没有状态为 DRAFT 的医嘱可签发'
          : '当前列表没有状态为 DRAFT 的医嘱可签发',
      );
      return;
    }
    if (useSelection && targets.length < flatSel.length) {
      ElMessage.warning('已跳过不可签发行（仅草稿可签）');
    }
    const hasDiagnosis = await ensureEncounterHasDiagnosisForSign(encounterId, patientId);
    if (!hasDiagnosis) return;
    const insuranceContract = await pickFirstInsuranceContract(draftRows.map((r) => r.contractId));
    // 主目标 requestId 在绑落库前后不变；派生行签发前再扩
    const primaryTargets = targets;
    await withAdviceMutationLoading(async () => {
      try {
        // 签发前统一带出用法/诊疗绑定（弹窗可改量/删行；取消则不落派生，继续签主医嘱）
        const bindRes = await resolveAndPersistAdviceDraftBindsAtSign({
          encounterId,
          patientId,
          draftRows,
          existingAdviceRows: flattenAdviceRowsForResourceOps(rows.value),
        });
        if (bindRes.success === false) {
          ElMessage.error(String(bindRes.message || '').trim() || '绑定带出失败');
          return;
        }
        // 确认落派生后才刷新列表拿派生 requestId；取消带出无需刷新，直接签主目标
        const bindDialogCancelled =
          String(bindRes.message ?? '').trim() === 'BIND_DIALOG_CANCELLED';
        if (!bindDialogCancelled) {
          await loadAdviceList();
        }
        const signTargets = bindDialogCancelled
          ? primaryTargets
          : expandAdviceSignTargetsWithBoundDrafts(
              primaryTargets,
              flattenAdviceRowsForResourceOps(rows.value),
            );
        const res = await signDoctorStationAdviceBatchWithCrossLotPricingConfirm({
          encounterId,
          patientId,
          signTargets,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '签发失败');
          return;
        }
        if (insuranceContract && detailAuditDialogRef) {
          try {
            const { continued } = await runOpDetailAuditGate({
              params: {
                encounterId,
                contractId: insuranceContract.contractId,
                contractBusNo: insuranceContract.contractBusNo,
                trigScen: '2',
              },
              dialogRef: detailAuditDialogRef,
            });
            if (!continued) {
              const withdrawRes = await httpWithdrawDoctorStationAdviceBatch({
                encounterId,
                patientId,
                targets: signTargets,
              });
              if (withdrawRes.success === false) {
                ElMessage.error(
                  String(withdrawRes.message || '').trim() ||
                    '事前提醒要求返回修改，但自动签退失败，请手工签退',
                );
              } else {
                ElMessage.warning('已按事前提醒返回修改，本次签发已自动签退');
              }
              clearAdviceTableSelection();
              await loadAdviceList();
              return;
            }
          } catch (auditError: unknown) {
            try {
              await httpWithdrawDoctorStationAdviceBatch({
                encounterId,
                patientId,
                targets: signTargets,
              });
            } catch {
              /* 签退失败时仍抛出审核错误 */
            }
            const msg = auditError instanceof Error ? auditError.message : '事前提醒失败';
            ElMessage.error(`${msg}；已尝试签退本次签发`);
            clearAdviceTableSelection();
            await loadAdviceList();
            return;
          }
        }
        ElMessage.success(String(res.message || '').trim() || '签发成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        // 跨批次金额确认框取消：不提示失败
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签发失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 将勾选行批量签退（必须勾选） */
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    const flatSel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const withdrawable = flatSel.filter((r) => rowLooksWithdrawable(r, rowLooksDraft));
    const targets = withdrawable
      .map((r) => resourceKeyFromRow(r))
      .filter(Boolean) as DoctorStationAdviceResourceKeyDto[];
    if (targets.length === 0) {
      ElMessage.warning('请勾选至少一条已签发且可签退的医嘱');
      return;
    }
    if (targets.length < flatSel.length) {
      ElMessage.warning('已跳过不可签退行');
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
        if (isStaleCrossRoleError(error)) {
          await loadAdviceList();
          return;
        }
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签退失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 将勾选草稿行以 DELETE 行方式提交暂存批次（必须勾选） */
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
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
    if (lines.length < flatSelDel.length) {
      ElMessage.warning('已跳过不可删除行（仅草稿可删）');
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await saveAdviceDraftBatchDirect({
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    const flatAg = flattenAdviceRowsForResourceOps(tableSelection.value);
    const meds = medicationDraftRowsWithRequestId(flatAg);
    if (meds.length === 0) {
      ElMessage.warning('请勾选至少一条草稿药品医嘱（需已暂存含 requestId）');
      return;
    }
    // 并组门禁：用法/频次/用药天数/诊断一致
    const assignGroupError = validateMedicationAssignGroupRows(meds);
    if (assignGroupError) {
      ElMessage.warning(assignGroupError);
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

  /** 拉取当前就诊诊断，供列表诊断列慢病角标 */
  async function loadListDiagnosisItems(loadGeneration: number = adviceLoadGeneration) {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      if (loadGeneration === adviceLoadGeneration) {
        listDiagnosisItems.value = [];
      }
      return;
    }
    try {
      const displayList = await fetchEncounterDiagnosisDisplayList(
        { encounterId, patientId },
        'outpatient',
      );
      if (loadGeneration !== adviceLoadGeneration) {
        return;
      }
      listDiagnosisItems.value = displayList
        .map((row) => mapEncounterRowToDiagnosisItem(row))
        .filter((item): item is DiagnosisItem => item !== null);
    } catch {
      if (loadGeneration === adviceLoadGeneration) {
        listDiagnosisItems.value = [];
      }
    }
  }

  /** 选中就诊变化时拉取医嘱主表 */
  async function loadAdviceList() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      rows.value = [];
      listDiagnosisItems.value = [];
      adviceListLoading.value = false;
      return;
    }
    adviceListLoading.value = true;
    const loadGeneration = adviceLoadGeneration;
    try {
      const res = await httpListDoctorStationAdvice({
        encounterId,
        patientId,
        onlyMine: false,
      });
      if (loadGeneration !== adviceLoadGeneration) return;
      const { content: list } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(res);
      const merged = mergeMedicationAdviceRowsForListDisplay(list);
      // 检验/检查/治疗申请单仅在「申请单」页签展示
      const withoutApplyForms = excludeServiceApplyFormAdviceRows(merged);
      // 费用性质：接口文案优先；空时用 init 合同下拉按 contractId 补展示
      rows.value = enrichAdviceListFeeNatureText(withoutApplyForms, feeNatureOptions.value);
      notifyEncounterFeeRefresh();
    } catch {
      if (loadGeneration !== adviceLoadGeneration) return;
      rows.value = [];
    } finally {
      if (loadGeneration === adviceLoadGeneration) {
        adviceListLoading.value = false;
      }
    }
  }

  /** 换患者时立刻丢掉术语/费用性质，避免新开医嘱默认带上上个患者合同 */
  function clearAdviceInitOptions() {
    medicationRateOptions.value = [];
    medicationRouteOptions.value = [];
    feeNatureOptions.value = [];
  }

  /** IF-DOR-18：术语下拉；loadGeneration 缺省取当前代际（供外部 reload 暴露） */
  async function loadAdviceInit(loadGeneration: number = adviceLoadGeneration) {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      if (loadGeneration === adviceLoadGeneration) {
        clearAdviceInitOptions();
      }
      return;
    }
    try {
      const [res, chargeTypes] = await Promise.all([
        httpInitDoctorStationAdvice({ encounterId, patientId }),
        getChargeTypeListApi().catch(() => []),
      ]);
      // 慢请求：已换患者则丢弃，防止费用性质首项串到新就诊
      if (loadGeneration !== adviceLoadGeneration) return;
      medicationRateOptions.value = res.medicationRateOptions ?? [];
      medicationRouteOptions.value = res.medicationRouteOptions ?? [];
      // 补 categoryEnum：医保等级=自费时按 code 收窄可选合同
      feeNatureOptions.value = enrichFeeNatureOptionsWithCategory(
        res.feeNatureOptions ?? [],
        chargeTypes,
      );
    } catch {
      if (loadGeneration !== adviceLoadGeneration) return;
      clearAdviceInitOptions();
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
        // 院点可排除检验/检查/治疗，迫使仅走申请单（申请单选品不传）
        excludeCategoryCodes: [...ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES],
        positiveQuantityOnly: params.positiveQuantityOnly,
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
      const rawRows = await fetchAdviceCatalogRowsByPrepickedKeys(keys, {
        encounterId,
        patientId,
        organizationUnitId,
        excludeCategoryCodes: [...ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES],
      });
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

  /** 编辑卡「保存」：新建药品走用法绑定；纯编辑 UPDATE 不二次带出 */
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
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
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
        ElMessage.warning(
          `第${n}条诊疗医嘱：请完善费用性质、单次执行数量、单位、执行次数与执行科室（单位须来自目录包装单位）`,
        );
      } else if (kind === 'DEVICE') {
        ElMessage.warning(`第${n}条耗材医嘱：请完善费用性质、数量与单位`);
      } else {
        ElMessage.warning(
          `第${n}条药品医嘱：请完善诊断、费用性质、用法、频次、单次用量、总量与单位等必填项`,
        );
      }
      return;
    }
    let lines: DoctorStationAdviceDraftLineRequest[];
    try {
      const removedDeleteLines = collectRemovedAdviceDraftDeleteLines(
        editorInitialGroup.value,
        groupItems,
      );
      lines = [
        ...removedDeleteLines,
        ...buildAdviceDraftLines(groupItems, editorPatientDiagnoses.value, {
          encounterDiagnosisRows,
          existingAdviceRows: rows.value,
        }),
      ];
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '请求组包失败');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await saveAdviceDraftBatchWithManualUsageBind({
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
        // 皮试跨批确认框取消：不提示失败
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
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

  /** 编辑卡删除已保存行：立即以 DELETE 行提交暂存批次 */
  const handleDeleteDrugInEditor = async (index: number, item: AdviceDrugItem) => {
    const line = buildAdviceDraftDeleteLineFromDrugItem(item);
    if (!line) {
      return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息，无法删除');
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    const deletedRequestId = String(item.medicationRequestId ?? '').trim();
    await withAdviceMutationLoading(async () => {
      try {
        const res = await saveAdviceDraftBatchDirect({
          encounterId,
          patientId,
          lines: [line],
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '删除失败');
          return;
        }
        ElMessage.success('删除成功');
        const nextGroup = editorInitialGroup.value.filter(
          (row) => String(row.medicationRequestId ?? '').trim() !== deletedRequestId,
        );
        if (nextGroup.length === 0) {
          showEditor.value = false;
          editorDirty.value = false;
          editingAdviceResourceType.value = '';
          editorInitialGroup.value = [];
          editorPatientDiagnoses.value = [];
        } else {
          editorInitialGroup.value = nextGroup.map((row, rowIndex) => ({
            ...row,
            expanded: rowIndex === Math.min(index, nextGroup.length - 1),
          }));
          editorDirty.value = nextGroup.some((row) => Boolean(row.selectedId));
        }
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

  /** 双击草稿行进入编辑 */
  const handleRowDblclick = async (row: AdviceRowItem) => {
    if (row.isEditor) return;
    if (row.applyFormNo?.trim()) {
      ElMessage.info('检验/检查申请单请前往「申请单」页签修改');
      return;
    }
    if (!rowLooksDraft(row)) {
      ElMessage.warning('仅草稿/待签发类医嘱允许编辑');
      return;
    }
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('卡片中有未保存数据，不允许编辑其他行');
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
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

  /** Tab 激活或切换患者后拉列表（接诊仅在双击卡片或写操作前触发，不在此自动写库） */
  async function loadAdviceTabData() {
    // 新一轮加载：作废在途 init/list，并先清空费用性质，杜绝默认值串患者
    const loadGeneration = ++adviceLoadGeneration;
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      rows.value = [];
      listDiagnosisItems.value = [];
      clearAdviceInitOptions();
      return;
    }
    clearAdviceInitOptions();
    await Promise.all([
      loadAdviceList(),
      loadAdviceInit(loadGeneration),
      loadListDiagnosisItems(loadGeneration),
    ]);
    if (loadGeneration !== adviceLoadGeneration) return;
    // init/list 并行：列表先返回时 options 可能仍空，两边都完成后再补一次费用性质
    rows.value = enrichAdviceListFeeNatureText(rows.value, feeNatureOptions.value);
  }

  /** 换患者时关闭编辑卡并清选择（含非当前 Tab，避免 KeepAlive 脏态） */
  function resetAdviceEditorOnPatientChange() {
    if (showEditor.value) {
      showEditor.value = false;
      editorDirty.value = false;
      editingAdviceResourceType.value = '';
      editorInitialGroup.value = [];
    }
    clearAdviceTableSelection();
  }

  /** KeepAlive 内仅激活页签拉数；换患者时由 watch 刷新当前可见 Tab */
  const isTabActive = ref(false);

  watch(
    () => [props.encounterId, props.patientId],
    () => {
      // 换患者/就诊：清空会话诊断记忆（医嘱 / 申请单共用）
      clearEncounterDiagnosisSession();
      lastSelectedEncounterDiagnosisIds.value = [];
      // 无论是否当前 Tab：先作废在途请求并丢掉上个患者费用性质
      adviceLoadGeneration += 1;
      clearAdviceInitOptions();
      resetAdviceEditorOnPatientChange();
      rows.value = [];
      if (!isTabActive.value) return;
      void loadAdviceTabData();
    },
  );

  onMounted(() => {
    attachAdviceEscKeydown();
  });

  onActivated(() => {
    isTabActive.value = true;
    attachAdviceEscKeydown();
    // 申请单等其它页签可能已更新共用诊断记忆，回到医嘱时同步
    const fromSession = readEncounterDiagnosisSession(props.encounterId);
    if (fromSession.length > 0) {
      lastSelectedEncounterDiagnosisIds.value = fromSession;
    }
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
    diagnosisItemsForList,
    encounterId: computed(() => props.encounterId ?? ''),
    getAdviceStatusTagType,
    getCellClassName,
    getTableRows,
    handleAdd,
    handleCloseEditor,
    handleDelete,
    handleDeleteDrugInEditor,
    handleEditorDirtyChange,
    handleMedicationAssignGroup,
    handleMedicationUngroup,
    handleRowDblclick,
    handleSaveGroup,
    handleSign,
    handleSpanMethod,
    handleToolbarPlaceholder,
    handleWithdraw,
    lastSelectedEncounterDiagnosisIds,
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
    /** 列表勾选（排除编辑卡虚拟行）；复制/签发等共用 */
    tableSelection,
    validateWesternRow,
  };
}
