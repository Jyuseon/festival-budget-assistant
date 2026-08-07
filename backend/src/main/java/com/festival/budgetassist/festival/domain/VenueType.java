package com.festival.budgetassist.festival.domain;

import java.util.Optional;

/**
 * 개최 장소 유형 6종 (원본 엑셀 H열, "02. 녹지형" 형식).
 *
 * <p>H열의 실제 헤더 라벨은 "축제 유형"으로 F열과 동일하게 표기되어 있으나(원본 파일 자체의
 * 라벨 표기 문제), 실제 값은 장소 유형 코드다.</p>
 */
public enum VenueType {

    VILLAGE("마을형"),
    GREEN("녹지형"),
    WATERFRONT("수변형"),
    INDEPENDENT("독립형"),
    OTHER("기타"),
    UNDECIDED("미정");

    private final String displayName;

    VenueType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<VenueType> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String withoutPrefix = rawValue.trim().replaceFirst("^\\d{1,2}\\.\\s*", "");
        for (VenueType type : values()) {
            if (type.displayName.equals(withoutPrefix)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}