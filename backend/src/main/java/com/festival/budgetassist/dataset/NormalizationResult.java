package com.festival.budgetassist.dataset;

import java.util.List;

import com.festival.budgetassist.festival.domain.FestivalRecord;

/**
 * 한 행에 대한 정규화 결과.
 *
 * <p>{@code errors}가 비어있지 않으면 이 행은 필수 항목(지역/축제유형/장소유형/개최주기/축제명
 * 코드)을 인식하지 못한 것이며, {@code record}는 null이다. 이런 행이 하나라도 있으면 전체
 * Import가 중단된다. {@code warnings}는 Import를 막지 않는 부수적인 데이터 품질 이슈다
 * (예산 합계 불일치, 방문객수 미인식 값 등).</p>
 */
record NormalizationResult(FestivalRecord record, List<String> errors, List<RowWarning> warnings) {

    boolean hasErrors() {
        return !errors.isEmpty();
    }
}