package com.festival.budgetassist.admin;

import java.util.List;

/**
 * GET /api/v1/admin/datasets/latest/sample
 *
 * <p>{@code loadedColumns}/{@code excludedColumns}는 데이터에서 계산한 값이 아니라
 * Phase 2 Import 설계상 고정된 사실이다({@link AdminColumnCatalog} 참고) - 실제 코드가
 * 어떤 열을 읽는지와 100% 일치하도록 그 목록 자체를 코드 상수로 관리한다.</p>
 */
public record AdminDatasetSampleResponse(
        boolean available,
        List<String> loadedColumns,
        List<String> excludedColumns,
        String personalInfoStatusLabel,
        List<SampleRow> sampleRows
) {
    static AdminDatasetSampleResponse unavailable() {
        return new AdminDatasetSampleResponse(false, AdminColumnCatalog.LOADED_COLUMNS, AdminColumnCatalog.EXCLUDED_COLUMNS,
                AdminColumnCatalog.PERSONAL_INFO_STATUS_LABEL, List.of());
    }
}