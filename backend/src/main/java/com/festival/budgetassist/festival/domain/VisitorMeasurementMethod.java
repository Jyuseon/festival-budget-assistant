package com.festival.budgetassist.festival.domain;

import java.util.Optional;

/**
 * 방문객수 계측 방법 (원본 엑셀 AD열, "01. 계측" 형식).
 *
 * <p>'응답 보기' 시트의 계측방법 옵션(티켓판매/유인계측/주차면수/무인계측/통신데이터등/기타/없음)과
 * 실제 AD열 코드 체계가 다르다. 실제 데이터는 01.계측 / 02.추정 / 03.미집계 / 04.기타 / 99.무응답
 * 5개 코드만 사용하므로, 이 enum은 실제 코드를 기준으로 한다.</p>
 *
 * <p>2차 기능(방문객 수 관련 API/알고리즘 반영)에서 사용할 필드이며, 1차 Import 단계에서는
 * 값만 적재하고 예산 추정 알고리즘/사용자 API 응답에는 사용하지 않는다.</p>
 */
public enum VisitorMeasurementMethod {

    MEASURED("계측"),
    ESTIMATED("추정"),
    NOT_TALLIED("미집계"),
    OTHER("기타"),
    NO_RESPONSE("무응답");

    private final String displayName;

    VisitorMeasurementMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<VisitorMeasurementMethod> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String withoutPrefix = rawValue.trim().replaceFirst("^\\d{1,2}\\.\\s*", "");
        for (VisitorMeasurementMethod method : values()) {
            if (method.displayName.equals(withoutPrefix)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }
}