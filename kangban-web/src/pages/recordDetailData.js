export function safeParseJson(raw) {
  if (!raw || raw === 'null') return null;
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw;
  } catch {
    return null;
  }
}

function asTextList(value) {
  if (Array.isArray(value)) return value.filter(item => typeof item === 'string' && item.trim());
  return [];
}

export function mapRecordDetail(record) {
  const diagnosis = safeParseJson(record.diagnosisData);
  const medicationData = safeParseJson(record.medicationsData);
  const adviceData = safeParseJson(record.advicesData);
  const diagnosisItems = Array.isArray(diagnosis)
    ? diagnosis
    : diagnosis?.diagnoses || (diagnosis?.diagnosis || diagnosis?.['诊断结论']
      ? [{ name: diagnosis.diagnosis || diagnosis['诊断结论'], detail: diagnosis.findings || diagnosis['检查所见'] || '' }]
      : []);

  return {
    recordDate: record.recordDate || '',
    recordType: record.recordType || '',
    recordName: record.recordName || '',
    hospital: record.hospital || '',
    department: record.department || '',
    doctor: record.doctor || '',
    confidence: record.confidence ?? null,
    fileUrl: record.fileUrl || '',
    chiefComplaint: diagnosis?.chiefComplaint || diagnosis?.complaint || '',
    diagnoses: diagnosisItems,
    medications: Array.isArray(medicationData) ? medicationData : medicationData?.medications || [],
    advices: asTextList(adviceData?.advices || adviceData || diagnosis?.['建议']),
  };
}
