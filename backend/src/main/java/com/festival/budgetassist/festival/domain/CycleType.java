package com.festival.budgetassist.festival.domain;

import java.util.Optional;

/**
 * 개최 주기 4종 (원본 엑셀 T열, "01. 매년" 형식).
 *
 * <p>주의: '응답 보기' 시트에는 매년/격년/비정기/일회성 4개 옵션이 정의되어 있지만,
 * 실제 T열 데이터에는 '비정기'는 전혀 등장하지 않고 대신 '04. 최초'(36건)가 존재한다.
 * 따라서 이 enum은 '응답 보기' 시트가 아니라 실제 데이터에 등장하는 코드를 기준으로 한다.</p>
 */
public enum CycleType {

    ANNUAL("매년"),
    BIENNIAL("격년"),
    ONE_TIME("일회성"),
    FIRST_TIME("최초");

    private final String displayName;

    CycleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<CycleType> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String withoutPrefix = rawValue.trim().replaceFirst("^\\d{1,2}\\.\\s*", "");
        for (CycleType type : values()) {
            if (type.displayName.equals(withoutPrefix)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}