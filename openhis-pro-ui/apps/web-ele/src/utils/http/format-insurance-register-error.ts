/** 将医保挂号/2201 相关后端错误转为友好、可持久展示的用户文案。 */
export function formatInsuranceRegisterError(
  errorCode: string | undefined,
  rawMessage: string,
): null | { message: string; title: string } {
  const msg = String(rawMessage ?? '').trim();
  if (!msg) {
    return null;
  }

  const isRegisterInsurance =
    errorCode === 'INS-011' ||
    errorCode === 'INS-C03' ||
    errorCode === 'INS-009' ||
    errorCode === 'INS-015' ||
    errorCode === 'REG-VAL-002' ||
    errorCode === 'REG-VAL-003' ||
    msg.includes('2201') ||
    msg.includes('医保编码') ||
    msg.includes('yb_no') ||
    msg.includes('医师编码');

  if (!isRegisterInsurance) {
    return null;
  }

  if (errorCode === 'INS-011' || msg.includes('yb_no') || msg.includes('医保编码')) {
    if (msg.includes('医师') || msg.includes('接诊')) {
      const nameMatch = msg.match(/医师缺少医保编码\(yb_no\):\s*(.+)/);
      const doctorName = nameMatch?.[1]?.trim();
      const doctorHint = doctorName ? `（${doctorName}）` : '';
      return {
        title: '医保挂号：请先完善医师编码',
        message: [
          `当前所选接诊医生${doctorHint}尚未维护医保平台编码，无法完成医保挂号登记（2201）。`,
          '',
          '请按以下步骤处理：',
          '1. 打开【基础数据 → 人员管理】，找到该医生',
          '2. 填写「医保编码 / yb_no」并保存（需与长春市医保平台一致）',
          '3. 回到挂号页，重新选择该医生后再次预结算',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
    if (msg.includes('科室')) {
      return {
        title: '医保挂号：请先完善科室编码',
        message: [
          '当前挂号科室尚未维护医保平台编码，无法完成医保挂号登记（2201）。',
          '',
          '请按以下步骤处理：',
          '1. 打开【基础数据 → 科室/服务位置】维护该科室的「医保编码」',
          '2. 保存后回到挂号页重新预结算',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
    if (msg.includes('须选择接诊医师') || msg.includes('未指定')) {
      return {
        title: '医保挂号：请选择接诊医生',
        message: [
          '医保挂号登记需要指定一名接诊医师。',
          '',
          '请在挂号表单中选择「医生」后再点击预结算。',
          '',
          `技术详情：${msg}`,
        ].join('\n'),
      };
    }
  }

  if (msg.includes('医师编码') && (msg.includes('-') || msg.includes('校验不通过'))) {
    return {
      title: '医保挂号：医师编码无效',
      message: [
        '医保平台拒绝了本次挂号登记：上传的医师编码无效（可能未维护或填写的不是平台编码）。',
        '',
        '请确认挂号医生已在主数据中维护正确的「医保编码 / yb_no」，然后重试。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'REG-VAL-003') {
    return {
      title: '医保挂号预结算条件不满足',
      message: [
        msg || '当前无法完成医保挂号预结算。',
        '',
        '常见原因：未装配医保模块、费用性质非医保类、或未选择正确的医保费用性质。',
        '请核对费用性质与读卡信息后重试。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'INS-015' || msg.includes('重新展码') || msg.includes('刷新预结算')) {
    return {
      title: '医保电子凭证已失效',
      message: [
        '医保电子凭证可能已过期或平台要求重新展码。',
        '',
        '请先点击「重新扫医保码并刷新预结算」，成功后再确认缴费。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'REG-VAL-002') {
    return {
      title: '请先读卡获取医保人员信息',
      message: [
        '医保挂号须先读社保卡/医保码，获取人员编号（psn_no）后再预结算。',
        '',
        `技术详情：${msg}`,
      ].join('\n'),
    };
  }

  if (errorCode === 'INS-C03' || errorCode === 'INS-009' || msg.includes('2201')) {
    const clean = msg.split('>>>')[0]?.trim() || msg;
    return {
      title: '医保平台挂号登记失败',
      message: [
        clean,
        '',
        '常见原因：',
        '· 医师/科室医保编码未维护或填写错误',
        '· 当日医保签到（9001）失效，可尝试重新读卡签到',
        '· 医保专网或平台短暂异常',
        '',
        '若仍失败，请联系信息科或医保办，并提供上述技术详情。',
      ].join('\n'),
    };
  }

  return {
    title: '医保挂号遇到问题',
    message: [
      msg,
      '',
      '请核对读卡、费用性质、医生/科室医保编码后重试；仍无法解决请联系信息科。',
    ].join('\n'),
  };
}
