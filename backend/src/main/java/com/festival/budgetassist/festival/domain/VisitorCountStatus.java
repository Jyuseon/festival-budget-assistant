package com.festival.budgetassist.festival.domain;

import java.util.Optional;

/**
 * 방문객수(AA 전년도 전체 / AB 내국인 / AC 외국인) 값이 숫자가 아닐 때의 상태.
 *
 * <p>세 컬럼 모두 독립적으로 이 네 값 중 하나를 가질 수 있다(실제 데이터로 확인).
 * AA에는 없던 '모름'이 AB(22건), AC(826건)에서 별도로 등장하므로 컬럼별로 상태를 따로 관리한다.
 * 숫자 값이 있으면 이 상태는 null이다(정상 케이스).</p>
 *
 * <p>2차 기능에서 사용할 필드이며, 1차 예산 추정 알고리즘/사용자 API 응답에는 사용하지 않는다.</p>
 */
public enum VisitorCountStatus {

    NOT_TALLIED("미집계"),
    FIRST_TIME_HELD("최초 개최"),
    RECENTLY_NOT_HELD("최근 미개최"),
    UNKNOWN("모름");

    private final String rawText;

    VisitorCountStatus(String rawText) {
        this.rawText = rawText;
    }

    public String getRawText() {
        return rawText;
    }

    public static Optional<VisitorCountStatus> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String trimmed = rawValue.trim();
        for (VisitorCountStatus status : values()) {
            if (status.rawText.equals(trimmed)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}