/** 前后端共享的请求体转换，避免页面直接拼接后端 DTO。 */
export function buildHealthRecordPayload({ subjectUserId, memberId, metric, value, unit, recordedDate, recordedTime, note }) {
  return {
    subjectUserId: subjectUserId ?? null,
    memberId: memberId ?? null,
    metric,
    value,
    unit,
    recordedDate,
    recordedTime,
    note,
  };
}

export function buildMedicationPayload({ memberId, name, dosage, unit, instruction, frequency, inventory, times }) {
  return {
    ...(memberId != null ? { memberId } : {}),
    name,
    dosage,
    unit,
    instruction,
    frequency,
    inventory,
    times: Array.isArray(times) ? times.filter(Boolean).join(',') : times,
  };
}

export function buildConsultationPayload(user, now = new Date(), member = null) {
  const subject = member || user;
  return {
    title: `在线问诊 - ${subject?.name || '本人'} - ${now.toLocaleDateString('zh-CN')}`,
    subjectUserId: member?.subjectUserId ?? null,
    memberId: member?.kind === 'account' ? null : (member?.memberId ?? member?.id ?? null),
    patientData: JSON.stringify({
      name: subject?.name || '',
      age: subject?.age ?? null,
      gender: subject?.gender || '',
      relation: member?.relation || '本人',
      patientId: member?.id ?? user?.id ?? user?.patientId ?? null,
    }),
  };
}
