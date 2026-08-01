import type {
  AdviceDrugItem,
  AdviceEditForm,
  AdviceProductOption,
  AdviceRowItem,
  InpatientAdviceTherapyType,
  ProductQueryParams,
} from '../services/advice.types';

import type {
  DoctorStationAdviceResourceKeyDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type {
  InpatientDoctorStationAdviceCatalogRowDto,
  InpatientDoctorStationAdviceDraftLineRequest,
  InpatientDoctorStationAdviceRowDto,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';
import type { DrugGroupDisplayItem } from '#/utils/doctor-station/drug-group-table';

import {
  computed,
  inject,
  nextTick,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { formatDateTime } from '@vben/utils';

import dayjs from 'dayjs';
import { ElMessage, ElTable } from 'element-plus';

import {
  httpCancelStopLongTermInpatientDoctorStationAdvice,
  httpChangeInpatientDoctorStationAdviceBusinessTime,
  httpAppendSkinTestCompanionForLongTermMedication,
  httpInitInpatientDoctorStationAdvice,
  httpListInpatientDoctorStationAdvice,
  httpPageInpatientDoctorStationAdviceCatalog,
  httpSaveInpatientDoctorStationAdviceDraftBatch,
  httpSignInpatientDoctorStationAdviceBatch,
  httpStopLongTermInpatientDoctorStationAdvice,
  httpUpdateInpatientDoctorStationMedicationAdviceGroups,
  httpWithdrawInpatientDoctorStationAdviceBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station/clinical-reception-context';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { isEncounterClinicalOrderMutationBlocked } from '#/utils/inpatient/encounter-clinical-order-gate';
import {
  validateChangeOpenedBusinessTime,
  validateChangeStopBusinessTime,
} from '#/utils/inpatient/encounter-order-time-bounds';

import { useInpatientDoctorPatientStore } from '../../doctorPatientStore';
import { createAdviceDrugItem, inferDoseBandConvertK } from '../services/advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
  prepickedKeyFromListRow,
} from '../services/advice-edit-hydrate';
import {
  defaultContractSelection,
  defaultMedicationRateCode,
  firstTerminologyCode,
} from '../services/advice-format';
import {
  flattenAdviceRowsForResourceOps,
  getAdviceStatusTagType,
  mapCatalogRowToOption,
  mergeMedicationAdviceRowsForListDisplay,
} from '../services/advice-list';
import { buildAdviceDraftLines } from '../services/advice-medication-draft';
import {
  inferAdviceLineEditorKind,
  isEntrustAdviceDrugItem,
  isInpatientDischargeMedicationTherapy,
  productTableNameForAdviceRow,
  resourceKeyFromRow,
  rowLooksCancelStopLongTermEligible,
  rowLooksAppendSkinTestEligible,
  rowLooksDeletable,
  rowLooksDraft,
  rowLooksDraftForSign,
  rowLooksStopLongTermEligible,
  rowLooksWithdrawable,
  therapyDraftFieldsFromAdviceRow,
  validateWesternEditLineItem,
} from '../services/advice-row';
import {
  ADVICE_ESC_OVERLAY_SELECTOR,
  ADVICE_PRODUCT_COLUMNS,
  INPATIENT_ADVICE_CATALOG_TABLE_NAMES,
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATEGORY_CODE,
  WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
} from '../services/advice-tab.constants';
import { loadEntrustCatalogOption } from '../services/inpatient-advice-entrust';
import { calcAdviceTotalFee } from './use-advice-drug-list';

export interface UseInpatientAdviceTabProps {
  encounterId?: string;
  patientId?: string;
}

/** 住院医生站「开立医嘱」页签：与门诊同构的独立实现，对接住院医嘱开立应用服务 */
export function useInpatientAdviceTab(props: UseInpatientAdviceTabProps) {
  const authStore = useAuthStore();
  const doctorPatientStore = useInpatientDoctorPatientStore();
  const ensureClinicalReception = inject(ENSURE_CLINICAL_RECEPTION_KEY, undefined);

  /** 待结算/已结算/已离院：仅禁用开立医嘱写按钮（列表照常加载，写拦截由后端 EX-IP-ORD-107） */
  const adviceWriteDisabled = computed(() =>
    isEncounterClinicalOrderMutationBlocked(doctorPatientStore.selectedPatient?.statusEnum),
  );

  const medicationRateOptions = ref<TerminologyOptionDto[]>([]);
  const medicationRouteOptions = ref<TerminologyOptionDto[]>([]);
  const feeNatureOptions = ref<TerminologyOptionDto[]>([]);

  const adviceTableRef = ref<InstanceType<typeof ElTable>>();
  const tableSelection = ref<AdviceRowItem[]>([]);

  const rows = ref<AdviceRowItem[]>([]);
  /** 医嘱主表列表拉取中 */
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

  /** 顶栏表单 / 新建行：频次按治疗类型默认（临时→立即） */
  function terminologyDefaults(therapy?: string) {
    return {
      frequency: defaultMedicationRateCode(medicationRateOptions.value, therapy),
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
    return validateWesternEditLineItem(item, item.therapyEnum);
  }

  /** 再增一行药品前的门禁（住院开立不绑诊断，无额外门禁；保留函数签名兼容） */
  function addDrugGuardWestern(_ctx?: { drugItems: AdviceDrugItem[]; isTcm: boolean }) {
    return true as const;
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
        endTime: '',
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
    if (ensureClinicalReception) {
      const ok = await ensureClinicalReception();
      if (!ok) return;
    }
    editingAdviceResourceType.value = '';
    editorInitialGroup.value = [];
    showEditor.value = true;
    editorForm.value = createEditForm();
    void nextTick(() => {
      bumpOrderSelectExpand();
    });
  };

  /** 快捷栏「嘱托医嘱」：关键字「嘱托」选品，恰好一条后打开编辑卡 */
  const handleAddEntrust = async () => {
    if (adviceWriteDisabled.value) return;
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('当前编辑卡片有未保存数据，请先保存或关闭');
      return;
    }
    if (ensureClinicalReception) {
      const ok = await ensureClinicalReception();
      if (!ok) return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const orgRaw = authStore.getSelectedOrganization();
    const organizationUnitId =
      orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
        ? String(orgRaw)
        : undefined;
    let loadResult;
    try {
      loadResult = await loadEntrustCatalogOption({
        encounterId,
        patientId,
        organizationUnitId,
      });
    } catch {
      ElMessage.error('加载嘱托目录失败，请稍后重试');
      return;
    }
    if (!loadResult.ok) {
      if (loadResult.reason === 'MULTIPLE') {
        ElMessage.warning('检索到多条嘱托项目，无法开立，请联系管理员核对目录');
      } else {
        ElMessage.warning('未找到嘱托项目，请联系管理员配置');
      }
      return;
    }
    const catalogOpt = loadResult.option;
    const t = terminologyDefaults();
    const def = defaultContractSelectionRef.value;
    const catalogRate = String(catalogOpt.rateCode ?? '').trim();
    const uCode = String(catalogOpt.defaultUnitCode ?? '').trim() || '次';
    const uName = String(catalogOpt.defaultUnitName ?? '').trim() || uCode;
    const orgId = String(catalogOpt.defaultPerformOrgId ?? '').trim();
    const orgName = String(catalogOpt.defaultPerformOrgName ?? '').trim();
    const seedBase = createAdviceDrugItem(
      {
        selectedId: String(catalogOpt.id ?? '').trim(),
        selectedDrugName: catalogOpt.name || INPATIENT_ENTRUST_ACTIVITY_NAME,
        selectedDrugSpec: catalogOpt.spec || '',
        selectedDrugPrice: catalogOpt.price ?? '',
        productTableName:
          String(catalogOpt.productTableName ?? '').trim() || 'wkf_activity_definition',
        frequency: catalogRate || t.frequency,
        therapyEnum: 'LONG_TERM',
        serviceQuantity: 1,
        totalAmount: 1,
        totalAmountUnit: uCode,
        performOrgId: orgId || undefined,
        performOrgName: orgName || undefined,
        contractId: def.contractId || undefined,
        feeNature: def.feeNature,
        diagnosis: '',
        groupId: String(Date.now()),
        expanded: true,
      },
      {
        feeNatureOptions: feeNatureOptions.value,
        medicationRateOptions: medicationRateOptions.value,
        medicationRouteOptions: medicationRouteOptions.value,
      },
    );
    const seed: AdviceDrugItem = {
      ...seedBase,
      catalogCategoryCode:
        String(catalogOpt.categoryCode ?? '').trim() || INPATIENT_ENTRUST_CATEGORY_CODE,
      totalUnitSalesCode: uCode,
      totalUnitSalesName: uName,
      entrustContent: undefined,
      expanded: true,
    };
    editingAdviceResourceType.value = 'SERVICE';
    editorInitialGroup.value = [seed];
    showEditor.value = true;
    editorForm.value = createEditForm();
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
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpSignInpatientDoctorStationAdviceBatch({
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
    if (targets.length < flatSel.length) {
      ElMessage.warning('已跳过不可签退行');
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpWithdrawInpatientDoctorStationAdviceBatch({
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

  /** 停嘱：仅长期且已签发医嘱（IF-IP-DOR-20） */
  const handleStopLongTerm = async () => {
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
    const stoppable = flatSel.filter((r) => rowLooksStopLongTermEligible(r));
    const lines = stoppable
      .map((r) => {
        const requestId = String(r.requestId ?? '').trim();
        const productTableName = productTableNameForAdviceRow(r);
        if (!requestId || !productTableName) return null;
        return { requestId, productTableName };
      })
      .filter(Boolean) as Array<{ productTableName: string; requestId: string }>;
    if (lines.length === 0) {
      ElMessage.warning('请勾选至少一条可停嘱的长期医嘱');
      return;
    }
    if (lines.length < flatSel.length) {
      ElMessage.warning('已跳过不可停嘱行');
    }
    // 停嘱默认当前时刻：须晚于开立、不早于入院、不晚于出院（缺字段不拦主路径）
    const selectedPatient = doctorPatientStore.selectedPatient;
    const admissionTime = String(
      selectedPatient?.startTime ?? selectedPatient?.admissionTime ?? '',
    ).trim();
    const dischargeTime = String(selectedPatient?.endTime ?? '').trim();
    const stopNow = dayjs().format('YYYY-MM-DD HH:mm:ss');
    for (const row of stoppable) {
      // 仅用接口原始开立时刻，避免列表展示分钟文案丢秒导致误拦
      const openedRaw = String(row.openedTimeRaw ?? '').trim();
      const stopErr = validateChangeStopBusinessTime(stopNow, {
        admissionTime,
        dischargeTime,
        openedTime: openedRaw || undefined,
      });
      if (stopErr) {
        ElMessage.warning(stopErr);
        return;
      }
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpStopLongTermInpatientDoctorStationAdvice({
          encounterId,
          patientId,
          lines,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '停嘱失败');
          return;
        }
        ElMessage.success(String(res.message || '').trim() || '停嘱成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '停嘱失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 取消停嘱：仅长期且已停嘱医嘱（IF-IP-DOR-25） */
  const handleCancelStopLongTerm = async () => {
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
    const cancellable = flatSel.filter((r) => rowLooksCancelStopLongTermEligible(r));
    const lines = cancellable
      .map((r) => {
        const requestId = String(r.requestId ?? '').trim();
        const productTableName = productTableNameForAdviceRow(r);
        if (!requestId || !productTableName) return null;
        return { requestId, productTableName };
      })
      .filter(Boolean) as Array<{ productTableName: string; requestId: string }>;
    if (lines.length === 0) {
      ElMessage.warning('请勾选至少一条可取消停嘱的长期医嘱');
      return;
    }
    if (lines.length < flatSel.length) {
      ElMessage.warning('已跳过不可取消停嘱行');
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpCancelStopLongTermInpatientDoctorStationAdvice({
          encounterId,
          patientId,
          lines,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '取消停嘱失败');
          return;
        }
        ElMessage.success(String(res.message || '').trim() || '取消停嘱成功');
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '取消停嘱失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 追加皮试：长期且已勾皮试的药品（IF-IP-DOR-27，based_on→该药） */
  const handleAppendSkinTest = async () => {
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
    // 仅长期皮试药可追加 companion「皮试检查」
    const appendable = flatSel.filter((r) => rowLooksAppendSkinTestEligible(r));
    const medicationRequestIds = appendable
      .map((r) => String(r.requestId ?? '').trim())
      .filter(Boolean);
    if (medicationRequestIds.length === 0) {
      ElMessage.warning('请勾选至少一条长期皮试药');
      return;
    }
    if (medicationRequestIds.length < flatSel.length) {
      ElMessage.warning('已跳过不可追加皮试行');
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpAppendSkinTestCompanionForLongTermMedication({
          encounterId,
          patientId,
          medicationRequestIds,
        });
        if (res?.success === false) {
          ElMessage.error(String(res.message || '').trim() || '追加皮试失败');
          return;
        }
        ElMessage.success(
          String(res.message || '').trim() || '已追加皮试检查，请签发后通知护士执行',
        );
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '追加皮试失败';
        ElMessage.error(msg);
      }
    });
  };

  /** 起止时间就地改时（点时间展开 DatePicker；精确到分钟） */
  const inlineBusinessTimeEdit = reactive({
    rowKey: '',
    timeKind: 'OPENED' as 'OPENED' | 'STOP',
    title: '',
    /** DatePicker 值：YYYY-MM-DD HH:mm */
    value: '',
    requestId: '',
    productTableName: '',
  });

  /** 就地 DatePicker 实例（进入编辑后自动展开面板） */
  const inlineBusinessTimePickerRef = ref<null | { handleOpen?: () => void }>(null);

  /** 绑定就地 DatePicker 实例 */
  const bindInlineBusinessTimePickerRef = (el: unknown) => {
    inlineBusinessTimePickerRef.value = el ? (el as { handleOpen?: () => void }) : null;
  };

  /** 退出就地改时 */
  const cancelInlineBusinessTimeEdit = () => {
    inlineBusinessTimeEdit.rowKey = '';
    inlineBusinessTimeEdit.value = '';
    inlineBusinessTimeEdit.requestId = '';
    inlineBusinessTimeEdit.productTableName = '';
    inlineBusinessTimePickerRef.value = null;
  };

  /** 当前行某类时间是否处于就地编辑 */
  const isInlineBusinessTimeEditing = (
    row: AdviceRowItem,
    timeKind: 'OPENED' | 'STOP',
  ): boolean => {
    return (
      Boolean(inlineBusinessTimeEdit.rowKey) &&
      inlineBusinessTimeEdit.rowKey === row.key &&
      inlineBusinessTimeEdit.timeKind === timeKind
    );
  };

  /** 将界面输入规范为后端 LocalDateTime（yyyy-MM-dd'T'HH:mm:ss） */
  const normalizeBusinessTimeInput = (raw: string): null | string => {
    const s = String(raw ?? '')
      .trim()
      .replaceAll('/', '-');
    if (!s) {
      return null;
    }
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(s)) {
      if (s.length === 16) {
        return `${s}:00`;
      }
      return s;
    }
    const matched = s.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})(?::(\d{2}))?$/);
    if (!matched) {
      return null;
    }
    const sec = matched[3] || '00';
    return `${matched[1]}T${matched[2]}:${sec}`;
  };

  /** 原始/展示时间 → DatePicker 用 YYYY-MM-DD HH:mm */
  const toBusinessTimePickerValue = (raw?: string, displayFallback?: string): string => {
    const fromRaw = String(raw ?? '').trim();
    let source = '';
    if (fromRaw) {
      const formatted = formatDateTime(fromRaw);
      if (formatted) {
        source = formatted;
      }
    }
    if (!source) {
      source = String(displayFallback ?? '').trim();
    }
    const matched = source.match(/^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2})/);
    if (matched) {
      return String(matched[1] ?? '').replaceAll('T', ' ');
    }
    return source;
  };

  /** 当前行是否可点选改开立时间 */
  const canEditOpenedBusinessTime = (row: AdviceRowItem): boolean => {
    if (adviceWriteDisabled.value || row.isEditor) {
      return false;
    }
    return Boolean(String(row.requestId ?? '').trim() && productTableNameForAdviceRow(row));
  };

  /** 当前行是否可点选改停嘱时间（仅已停嘱长期） */
  const canEditStopBusinessTime = (row: AdviceRowItem): boolean => {
    if (adviceWriteDisabled.value) {
      return false;
    }
    return rowLooksCancelStopLongTermEligible(row);
  };

  /**
   * 点起止时间就地改时（IF-IP-DOR-26）。
   * OPENED：可定位医嘱行；STOP：仅已停嘱长期行。
   */
  const beginInlineBusinessTimeEdit = async (timeKind: 'OPENED' | 'STOP', row: AdviceRowItem) => {
    if (showEditor.value && editorDirty.value) {
      ElMessage.warning('请先保存或关闭当前编辑卡片');
      return;
    }
    if (timeKind === 'STOP') {
      if (!canEditStopBusinessTime(row)) {
        return;
      }
    } else if (!canEditOpenedBusinessTime(row)) {
      return;
    }
    const requestId = String(row.requestId ?? '').trim();
    const productTableName = productTableNameForAdviceRow(row);
    if (!requestId || !productTableName) {
      ElMessage.warning('医嘱主键或产品表名缺失');
      return;
    }
    inlineBusinessTimeEdit.rowKey = row.key;
    inlineBusinessTimeEdit.timeKind = timeKind;
    inlineBusinessTimeEdit.title = timeKind === 'STOP' ? '改停嘱时间' : '改开立时间';
    inlineBusinessTimeEdit.requestId = requestId;
    inlineBusinessTimeEdit.productTableName = productTableName;
    inlineBusinessTimeEdit.value =
      timeKind === 'STOP'
        ? toBusinessTimePickerValue(row.stopTimeRaw, row.endTime)
        : toBusinessTimePickerValue(row.openedTimeRaw, row.startTime);
    await nextTick();
    inlineBusinessTimePickerRef.value?.handleOpen?.();
  };

  /** 确认提交就地改开立/停嘱时间 */
  const confirmInlineBusinessTime = async () => {
    if (!inlineBusinessTimeEdit.rowKey) {
      return;
    }
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      cancelInlineBusinessTimeEdit();
      return;
    }
    const title = inlineBusinessTimeEdit.title || '改时间';
    const newTime = normalizeBusinessTimeInput(inlineBusinessTimeEdit.value);
    if (!newTime) {
      ElMessage.warning('请选择有效时间');
      return;
    }
    // 开立/停嘱相对入院·出院·彼此交叉校验（仅前端提示）
    const selected = doctorPatientStore.selectedPatient;
    const admissionTime = String(selected?.startTime ?? selected?.admissionTime ?? '').trim();
    const dischargeTime = String(selected?.endTime ?? '').trim();
    const editRow =
      rows.value.find((r) => r.key === inlineBusinessTimeEdit.rowKey) ??
      flattenAdviceRowsForResourceOps(rows.value).find(
        (r) => r.key === inlineBusinessTimeEdit.rowKey,
      );
    if (inlineBusinessTimeEdit.timeKind === 'OPENED') {
      const openedErr = validateChangeOpenedBusinessTime(newTime, {
        admissionTime,
        dischargeTime,
        // 仅已停嘱的原始 stop_time；未停嘱不校开立↔停嘱
        stopTime: String(editRow?.stopTimeRaw ?? '').trim() || undefined,
      });
      if (openedErr) {
        ElMessage.warning(openedErr);
        return;
      }
    } else {
      const stopErr = validateChangeStopBusinessTime(newTime, {
        admissionTime,
        dischargeTime,
        openedTime: String(editRow?.openedTimeRaw ?? '').trim() || undefined,
      });
      if (stopErr) {
        ElMessage.warning(stopErr);
        return;
      }
    }
    // 先快照再退出编辑，避免面板关闭事件清空提交参数
    const requestId = inlineBusinessTimeEdit.requestId;
    const productTableName = inlineBusinessTimeEdit.productTableName;
    const timeKind = inlineBusinessTimeEdit.timeKind;
    cancelInlineBusinessTimeEdit();
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpChangeInpatientDoctorStationAdviceBusinessTime({
          encounterId,
          patientId,
          requestId,
          productTableName,
          timeKind,
          newTime,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || `${title}失败`);
          return;
        }
        ElMessage.success(String(res.message || '').trim() || `${title}成功`);
        clearAdviceTableSelection();
        await loadAdviceList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : `${title}失败`;
        ElMessage.error(msg);
      }
    });
  };

  /** DatePicker 面板关闭：未提交则退出编辑态 */
  const handleInlineBusinessTimeVisible = (open: boolean) => {
    if (open) {
      return;
    }
    void nextTick(() => {
      if (!inlineBusinessTimeEdit.rowKey) {
        return;
      }
      cancelInlineBusinessTimeEdit();
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
    const lines: InpatientDoctorStationAdviceDraftLineRequest[] = [];
    let skippedMissingTherapy = 0;
    for (const row of deletable) {
      const therapyFields = therapyDraftFieldsFromAdviceRow(row);
      if (!therapyFields) {
        skippedMissingTherapy += 1;
        continue;
      }
      const rid = String(row.requestId ?? '').trim();
      const ptn = productTableNameForAdviceRow(row);
      lines.push({
        lineOp: 'DELETE',
        requestId: rid,
        ...therapyFields,
        ...(ptn ? { productTableName: ptn } : {}),
      });
    }
    if (lines.length === 0) {
      if (skippedMissingTherapy > 0) {
        ElMessage.warning('无法删除：所选医嘱缺少长期/临时类型');
      } else {
        ElMessage.warning('请勾选至少一条可删除的草稿医嘱');
      }
      return;
    }
    if (lines.length < flatSelDel.length) {
      const parts: string[] = ['已跳过不可删除行（仅草稿可删）'];
      if (skippedMissingTherapy > 0) {
        parts.push('已跳过缺少长期/临时类型的医嘱');
      }
      ElMessage.warning(parts.join('；'));
    } else if (skippedMissingTherapy > 0) {
      ElMessage.warning('已跳过缺少长期/临时类型的医嘱');
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpSaveInpatientDoctorStationAdviceDraftBatch({
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
    if (withGroup.length < flatUg.length) {
      ElMessage.warning('已跳过非药品、非草稿或无组号的行');
    }
    const clearGroupIds = withGroup.map((r) => String(r.requestId).trim());
    try {
      const res = await httpUpdateInpatientDoctorStationMedicationAdviceGroups({
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
    if (meds.length < flatAg.length) {
      ElMessage.warning('已跳过非药品或非草稿的行');
    }
    /** 每组并组操作使用新的毫秒时间戳，保证与近期其他并组组号不冲突 */
    const groupIdRaw = String(Date.now());
    try {
      const res = await httpUpdateInpatientDoctorStationMedicationAdviceGroups({
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

  /**
   * 列表治疗类型筛选：空=全部；LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION。
   * 传给 IF-IP-DOR list 的 therapyTypeCode。
   */
  const listTherapyFilter = ref<'' | InpatientAdviceTherapyType>('');

  /** 切换列表长临/出院带药筛选并重新拉数 */
  function setListTherapyFilter(code: '' | InpatientAdviceTherapyType) {
    if (listTherapyFilter.value === code) return;
    listTherapyFilter.value = code;
    void loadAdviceList();
  }

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
      const therapyTypeCode = String(listTherapyFilter.value ?? '').trim() || undefined;
      const res = await httpListInpatientDoctorStationAdvice({
        encounterId,
        patientId,
        ...(therapyTypeCode ? { therapyTypeCode } : {}),
      });
      const { content: list } = parsePagedRowsFromApiData<InpatientDoctorStationAdviceRowDto>(res);
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
      const res = await httpInitInpatientDoctorStationAdvice({ encounterId, patientId });
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
      const catalogTables =
        params.productTableNames && params.productTableNames.length > 0
          ? params.productTableNames.filter((t) =>
              (INPATIENT_ADVICE_CATALOG_TABLE_NAMES as readonly string[]).includes(t),
            )
          : [...INPATIENT_ADVICE_CATALOG_TABLE_NAMES];
      const res = await httpPageInpatientDoctorStationAdviceCatalog({
        encounterId,
        patientId,
        organizationUnitId,
        keyword: params.keyword?.trim() || undefined,
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        productTableNames: catalogTables,
        excludeMedicationCategoryCodes: [
          ...WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
        ],
      });
      const { content: rawRows, totalCount } =
        parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
      const content = rawRows.map((item) => mapCatalogRowToOption(item));
      return { content, totalElements: totalCount };
    } catch {
      return { content: [] as AdviceProductOption[], totalElements: 0 };
    }
  };

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
      const res = await httpPageInpatientDoctorStationAdviceCatalog({
        encounterId,
        patientId,
        organizationUnitId,
        pageNo: 1,
        pageSize: Math.max(keys.length, 20),
        prepickedProductKeys: keys,
        productTableNames: [...INPATIENT_ADVICE_CATALOG_TABLE_NAMES],
        excludeMedicationCategoryCodes: [
          ...WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
        ],
      });
      const { content: rawRows } =
        parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
      return rawRows.map((item) => mapCatalogRowToOption(item));
    } catch {
      return [];
    }
  }

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
      const therapyCode = String(row.therapyTypeCode ?? seed.therapyEnum ?? '')
        .trim()
        .toUpperCase();
      const merged = catalog
        ? mergeCatalogIntoAdviceDrugItemForEdit({ ...base, ...seed }, catalog, {
            resourceType: row.resourceType,
            therapyTypeCode: row.therapyTypeCode,
            totalUnitDefaultKind: therapyCode === 'TEMPORARY' ? 'PACKAGE' : 'SALES',
          })
        : seed;
      // 临时：后端可能清空 rate_code，回填时默认「立即」
      const frequency =
        String(merged.frequency ?? seed.frequency ?? base.frequency ?? '').trim() ||
        defaultMedicationRateCode(ctx.medicationRateOptions, therapyCode);
      return inferDoseBandConvertK({
        ...base,
        ...merged,
        frequency,
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
  const handleSaveGroup = async (groupItems: AdviceDrugItem[]) => {
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
        if (isInpatientDischargeMedicationTherapy(item.therapyEnum)) {
          ElMessage.warning(`第${n}条医嘱：出院带药仅允许开立药品`);
        } else if (isEntrustAdviceDrugItem(item)) {
          ElMessage.warning(`第${n}条嘱托医嘱：请填写频次与嘱托内容`);
        } else {
          const longTerm =
            String(item.therapyEnum ?? '')
              .trim()
              .toUpperCase() === 'LONG_TERM';
          ElMessage.warning(
            longTerm
              ? `第${n}条诊疗医嘱：请完善数量、频次与执行科室`
              : `第${n}条诊疗医嘱：请完善数量、执行次数与执行科室`,
          );
        }
      } else if (kind === 'DEVICE') {
        ElMessage.warning(`第${n}条耗材医嘱：数量须大于 0，并须填写单位`);
      } else {
        const longTerm =
          String(item.therapyEnum ?? '')
            .trim()
            .toUpperCase() === 'LONG_TERM';
        ElMessage.warning(
          longTerm
            ? `第${n}条药品医嘱：请完善费用性质、用法、频次、单次用量等必填项`
            : `第${n}条药品医嘱：请完善费用性质、用法、频次、单次用量、总量与单位等必填项`,
        );
      }
      return;
    }
    let lines: InpatientDoctorStationAdviceDraftLineRequest[];
    try {
      lines = buildAdviceDraftLines(groupItems);
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '请求组包失败');
      return;
    }
    await withAdviceMutationLoading(async () => {
      try {
        const res = await httpSaveInpatientDoctorStationAdviceDraftBatch({
          encounterId,
          patientId,
          lines,
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

  /** 双击草稿行进入编辑；申请单行由工作区拦截后走弹窗 */
  const handleRowDblclick = async (row: AdviceRowItem) => {
    if (adviceWriteDisabled.value) return;
    if (row.isEditor) return;
    if (String(row.applyFormNo ?? '').trim()) {
      // 申请单编辑入口在 workspace（弹窗回显），此处不进入药品编辑卡
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

  /** 按申请单号聚组当前列表行（供弹窗编辑回显） */
  function buildServiceApplyEditGroup(row: AdviceRowItem): null | {
    applyFormNo: string;
    lines: AdviceRowItem[];
  } {
    const applyFormNo = String(row.applyFormNo ?? '').trim();
    if (!applyFormNo) return null;
    const lines = flattenAdviceRowsForResourceOps(rows.value)
      .filter((item) => String(item.applyFormNo ?? '').trim() === applyFormNo)
      .sort((left, right) => (left.sortNumber ?? 0) - (right.sortNumber ?? 0));
    return {
      applyFormNo,
      lines: lines.length > 0 ? lines : [row],
    };
  }

  /** Tab 激活或切换患者后：并行拉列表与初始化下拉 */
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

  onMounted(() => {
    attachAdviceEscKeydown();
  });

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
    adviceWriteDisabled,
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
    encounterId: computed(() => props.encounterId ?? ''),
    getAdviceStatusTagType,
    getCellClassName,
    getTableRows,
    handleAdd,
    handleAddEntrust,
    handleCloseEditor,
    handleDelete,
    handleEditorDirtyChange,
    handleMedicationAssignGroup,
    handleMedicationUngroup,
    handleRowDblclick,
    buildServiceApplyEditGroup,
    handleSaveGroup,
    handleSign,
    handleSpanMethod,
    handleToolbarPlaceholder,
    handleWithdraw,
    handleStopLongTerm,
    handleCancelStopLongTerm,
    handleAppendSkinTest,
    canEditOpenedBusinessTime,
    canEditStopBusinessTime,
    beginInlineBusinessTimeEdit,
    bindInlineBusinessTimePickerRef,
    confirmInlineBusinessTime,
    handleInlineBusinessTimeVisible,
    inlineBusinessTimeEdit,
    isInlineBusinessTimeEditing,
    listTherapyFilter,
    loadAdviceInit,
    loadAdviceList,
    medicationRateOptions,
    setListTherapyFilter,
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
