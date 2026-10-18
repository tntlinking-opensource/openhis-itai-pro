import type { AdviceRowItem } from '../../advice/services/advice.types';

import type {
  DoctorStationAdviceDraftLineRequest,
  DoctorStationAdviceRowDto,
} from '#/api/doctor-station/advice.types';
import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';
import type { ServiceApplyDocumentGroup } from '../service-apply-doc-groups';
import type { ServiceApplyPatientSummary } from '../service-apply.types';

import { computed, onActivated, onDeactivated, ref, watch, type Ref } from 'vue';

import { ElMessage } from 'element-plus';

import { groupServiceApplyRowsByFormNo } from '../service-apply-doc-groups';

import {
  httpListDoctorStationAdvice,
  httpWithdrawDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import { useEnsureClinicalReception } from '#/components/doctor-station';
import {
  runOpDetailAuditGate,
  type DetailAuditRemindDialogExpose,
} from '#/chs/composables/use-detail-audit-gate';
import { pickFirstInsuranceContract } from '#/chs/utils/resolve-insurance-contract-bus-no';
import { notifyEncounterFeeRefresh } from '#/components/outpatient/encounter-fee-summary';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { printServiceApplyFromAdviceRows } from '#/utils/print/print-service-apply';

import { saveAdviceDraftBatchDirect } from '../../advice/services/advice-draft-save.service';
import {
  buildServiceApplyBoundConsumableDeleteLines,
  copyServiceApplyFormsAsNewDraft,
  expandServiceApplyResourceTargetsWithBinds,
  loadServiceApplyAdviceRowItems,
  signServiceApplyDraftAdviceRowsWithBind,
} from '../service-apply.service';
import {
  filterServiceApplyFormAdviceRows,
  flattenAdviceRowsForResourceOps,
  getAdviceChargeStatusTagType,
  getAdviceStatusTagType,
  mergeMedicationAdviceRowsForListDisplay,
} from '../../advice/services/advice-list';
import {
  productTableNameForAdviceRow,
  rowLooksDeletable,
  rowLooksDraft,
  rowLooksDraftForSign,
  rowLooksWithdrawable,
} from '../../advice/services/advice-row';

export interface UseServiceApplyTabProps {
  encounterId?: string;
  patientId?: string;
  serviceApplyPatient?: ServiceApplyPatientSummary;
  serviceApplyPrintMeta?: ServiceApplyPrintMeta;
  /** 门诊 / 住院：列表签发与绑定解析 HTTP */
  stationScope?: 'inpatient' | 'outpatient';
}

/** 门诊医生站「申请单」页签：检验/检查申请列表、签发/撤回/删除与打印 */
export function useServiceApplyTab(
  props: UseServiceApplyTabProps,
  options?: { detailAuditDialogRef?: Ref<DetailAuditRemindDialogExpose | null> },
) {
  const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();
  const detailAuditDialogRef = options?.detailAuditDialogRef;
  const rows = ref<AdviceRowItem[]>([]);
  /** 就诊全量医嘱（含绑定耗材），供申请单聚组展示子行 */
  const allAdviceRowsForBind = ref<AdviceRowItem[]>([]);
  const tableSelection = ref<AdviceRowItem[]>([]);
  const listLoading = ref(false);
  const mutationLoading = ref(false);
  const printLoading = ref(false);

  const tableLoading = computed(() => listLoading.value || mutationLoading.value);

  /** 按申请单号聚组后的单据列表（检验/检查同页） */
  const documentGroups = computed<ServiceApplyDocumentGroup[]>(() =>
    groupServiceApplyRowsByFormNo(rows.value, allAdviceRowsForBind.value),
  );

  /** 清空已选申请单 */
  function clearTableSelection() {
    tableSelection.value = [];
  }

  /** 判断整张申请单是否已全部选中 */
  function isDocumentGroupSelected(group: ServiceApplyDocumentGroup): boolean {
    if (group.lines.length === 0) {
      return false;
    }
    const selectedKeys = new Set(tableSelection.value.map((row) => row.key));
    return group.lines.every((line) => selectedKeys.has(line.key));
  }

  /** 切换整张申请单的选中状态 */
  function toggleDocumentGroupSelection(group: ServiceApplyDocumentGroup) {
    if (isDocumentGroupSelected(group)) {
      const removeKeys = new Set(group.lines.map((line) => line.key));
      tableSelection.value = tableSelection.value.filter((row) => !removeKeys.has(row.key));
      return;
    }
    const selectedKeys = new Set(tableSelection.value.map((row) => row.key));
    const next = [...tableSelection.value];
    for (const line of group.lines) {
      if (!selectedKeys.has(line.key)) {
        next.push(line);
      }
    }
    tableSelection.value = next;
  }

  async function withMutationLoading(fn: () => Promise<void>) {
    mutationLoading.value = true;
    try {
      await fn();
    } finally {
      mutationLoading.value = false;
    }
  }

  async function loadApplyFormList() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      rows.value = [];
      allAdviceRowsForBind.value = [];
      listLoading.value = false;
      return;
    }
    listLoading.value = true;
    try {
      const res = await httpListDoctorStationAdvice({
        encounterId,
        patientId,
        onlyMine: false,
      });
      const { content: list } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(res);
      const merged = mergeMedicationAdviceRowsForListDisplay(list);
      allAdviceRowsForBind.value = merged;
      rows.value = filterServiceApplyFormAdviceRows(merged);
      notifyEncounterFeeRefresh();
    } catch {
      rows.value = [];
      allAdviceRowsForBind.value = [];
    } finally {
      listLoading.value = false;
    }
  }

  async function handleSign() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    const draftRows = flattenAdviceRowsForResourceOps(rows.value).filter((r) =>
      rowLooksDraftForSign(r),
    );
    if (draftRows.length === 0) {
      ElMessage.warning('当前列表没有状态为 DRAFT 的申请单可签发');
      return;
    }
    const stationScope = props.stationScope ?? 'outpatient';
    await withMutationLoading(async () => {
      try {
        await signServiceApplyDraftAdviceRowsWithBind({
          encounterId,
          patientId,
          draftRows,
          stationScope: props.stationScope ?? 'outpatient',
          existingAdviceRows: allAdviceRowsForBind.value,
        });
        const insuranceContract = await pickFirstInsuranceContract(
          draftRows.map((r) => r.contractId),
        );
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
              const withdrawTargets = expandServiceApplyResourceTargetsWithBinds(
                draftRows,
                await loadServiceApplyAdviceRowItems({
                  encounterId,
                  patientId,
                  stationScope,
                }),
              );
              const withdrawRes = await httpWithdrawDoctorStationAdviceBatch({
                encounterId,
                patientId,
                targets: withdrawTargets,
              });
              if (withdrawRes.success === false) {
                ElMessage.error(
                  String(withdrawRes.message || '').trim() ||
                    '事前提醒要求返回修改，但自动签退失败，请手工签退',
                );
              } else {
                ElMessage.warning('已按事前提醒返回修改，本次签发已自动签退');
              }
              clearTableSelection();
              await loadApplyFormList();
              return;
            }
          } catch (auditError: unknown) {
            try {
              const withdrawTargets = expandServiceApplyResourceTargetsWithBinds(
                draftRows,
                await loadServiceApplyAdviceRowItems({
                  encounterId,
                  patientId,
                  stationScope,
                }),
              );
              await httpWithdrawDoctorStationAdviceBatch({
                encounterId,
                patientId,
                targets: withdrawTargets,
              });
            } catch {
              /* 签退失败时仍抛出审核错误 */
            }
            const msg = auditError instanceof Error ? auditError.message : '事前提醒失败';
            ElMessage.error(`${msg}；已尝试签退本次签发`);
            clearTableSelection();
            await loadApplyFormList();
            return;
          }
        }
        ElMessage.success('签发成功');
        clearTableSelection();
        await loadApplyFormList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签发失败';
        ElMessage.error(msg);
      }
    });
  }

  async function handleWithdraw() {
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
    const targets = expandServiceApplyResourceTargetsWithBinds(
      withdrawable,
      allAdviceRowsForBind.value,
    );
    if (targets.length === 0) {
      ElMessage.warning('请勾选至少一条已签发且可签退的申请单');
      return;
    }
    await withMutationLoading(async () => {
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
        clearTableSelection();
        await loadApplyFormList();
      } catch (error: unknown) {
        if (isStaleCrossRoleError(error)) {
          await loadApplyFormList();
          return;
        }
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '签退失败';
        ElMessage.error(msg);
      }
    });
  }

  /** 勾选申请单整张复制为当前就诊新草稿（与开立医嘱复制同一落库时机） */
  async function handleCopy() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const selectedKeys = new Set(tableSelection.value.map((row) => row.key));
    const forms = documentGroups.value
      .filter((group) => group.lines.some((line) => selectedKeys.has(line.key)))
      .map((group) => ({
        lines: group.lines.filter((line) => selectedKeys.has(line.key)),
        boundConsumablesByRequestId: group.boundConsumablesByRequestId,
      }));
    if (forms.length === 0) {
      ElMessage.warning('请先勾选要复制的申请单');
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    await withMutationLoading(async () => {
      try {
        await copyServiceApplyFormsAsNewDraft({
          encounterId,
          patientId,
          stationScope: props.stationScope ?? 'outpatient',
          forms,
        });
        ElMessage.success('已复制');
        clearTableSelection();
        await loadApplyFormList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '复制失败';
        ElMessage.error(msg);
      }
    });
  }

  async function handleDelete() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('缺少就诊或患者信息');
      return;
    }
    const receptionOk = await ensureReceptionBeforeClinicalWork();
    if (!receptionOk) return;
    const flatSel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const deletable = flatSel.filter((r) => rowLooksDeletable(r, rowLooksDraft));
    const lines: DoctorStationAdviceDraftLineRequest[] = deletable.map((row) => {
      const rid = String(row.requestId ?? '').trim();
      const ptn = productTableNameForAdviceRow(row);
      return {
        lineOp: 'DELETE',
        requestId: rid,
        ...(ptn ? { productTableName: ptn } : {}),
      };
    });
    const bindDeleteLines = buildServiceApplyBoundConsumableDeleteLines(
      deletable,
      allAdviceRowsForBind.value,
    );
    const allDeleteLines = [...lines, ...bindDeleteLines];
    if (allDeleteLines.length === 0) {
      ElMessage.warning('请勾选至少一条可删除的草稿申请单');
      return;
    }
    await withMutationLoading(async () => {
      try {
        const res = await saveAdviceDraftBatchDirect({
          encounterId,
          patientId,
          lines: allDeleteLines,
        });
        if (res.success === false) {
          ElMessage.error(String(res.message || '').trim() || '删除失败');
          return;
        }
        ElMessage.success('删除成功');
        clearTableSelection();
        await loadApplyFormList();
      } catch (error: unknown) {
        const msg =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: unknown }).message)
            : '删除失败';
        ElMessage.error(msg);
      }
    });
  }

  async function handlePrint() {
    const encounterId = props.encounterId?.trim() || '';
    const patientId = props.patientId?.trim() || '';
    if (!encounterId || !patientId) {
      ElMessage.warning('请先选择患者与就诊');
      return;
    }
    const patient = props.serviceApplyPatient;
    if (!patient) {
      ElMessage.warning('缺少患者信息，无法打印申请单');
      return;
    }
    const flatSel = flattenAdviceRowsForResourceOps(tableSelection.value);
    const printable = flatSel.filter(
      (row) => row.applyFormNo?.trim() && !rowLooksDraftForSign(row),
    );
    if (printable.length === 0) {
      ElMessage.warning('请勾选已签发的检验/检查申请单');
      return;
    }
    printLoading.value = true;
    try {
      const results = await printServiceApplyFromAdviceRows({
        encounterId,
        patientId,
        patient,
        rows: printable,
        printMeta: props.serviceApplyPrintMeta,
      });
      if (results.some((item) => item.errorCode === 'USER_CANCELLED')) {
        return;
      }
      if (results.length === 0) {
        ElMessage.warning('所选行无法识别为检验/检查申请单');
        return;
      }
      const successCount = results.filter((item) => item.ok).length;
      if (successCount > 0) {
        ElMessage.success(`已提交 ${successCount} 张申请单打印`);
      }
    } finally {
      printLoading.value = false;
    }
  }

  const isTabActive = ref(false);

  watch(
    () => [props.encounterId, props.patientId],
    () => {
      if (!isTabActive.value) return;
      clearTableSelection();
      void loadApplyFormList();
    },
  );

  onActivated(() => {
    isTabActive.value = true;
    void loadApplyFormList();
  });

  onDeactivated(() => {
    isTabActive.value = false;
  });

  async function reload() {
    await withMutationLoading(async () => {
      await loadApplyFormList();
    });
  }

  return {
    clearTableSelection,
    documentGroups,
    getAdviceChargeStatusTagType,
    getAdviceStatusTagType,
    handleCopy,
    handleDelete,
    handlePrint,
    handleSign,
    handleWithdraw,
    isDocumentGroupSelected,
    listLoading,
    mutationLoading,
    printLoading,
    reload,
    rows,
    tableLoading,
    tableSelection,
    toggleDocumentGroupSelection,
  };
}
