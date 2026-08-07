package com.festival.budgetassist.festival.domain;

import java.util.Optional;

/**
 * 축제 유형 5종 (원본 엑셀 F열, "01. 문화예술" 형식).
 */
public enum FestivalType {

    CULTURE_ART("문화예술"),
    NATURE_ECOLOGY("자연생태"),
    COMMUNITY("주민화합"),
    TRADITION_HISTORY("전통역사"),
    LOCAL_SPECIALTY("지역특산물");

    private final String displayName;

    FestivalType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<FestivalType> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String withoutPrefix = rawValue.trim().replaceFirst("^\\d{1,2}\\.\\s*", "");
        for (FestivalType type : values()) {
            if (type.displayName.equals(withoutPrefix)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}