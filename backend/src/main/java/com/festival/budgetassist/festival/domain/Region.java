package com.festival.budgetassist.festival.domain;

/**
 * 광역자치단체 17개 코드.
 *
 * <p>원본 엑셀 '조사표' 시트의 C열(광역자치단체명)은 "01. 서울" 형식의 코드 접두사를 갖는다.
 * 이 순서(01~17)는 '총괄' 시트의 지역 컬럼 순서와도 일치함을 실제 파일로 확인했다.</p>
 */
public enum Region {

    SEOUL(1, "서울"),
    BUSAN(2, "부산"),
    DAEGU(3, "대구"),
    INCHEON(4, "인천"),
    GWANGJU(5, "광주"),
    DAEJEON(6, "대전"),
    ULSAN(7, "울산"),
    SEJONG(8, "세종"),
    GYEONGGI(9, "경기"),
    GANGWON(10, "강원"),
    CHUNGBUK(11, "충북"),
    CHUNGNAM(12, "충남"),
    JEONBUK(13, "전북"),
    JEONNAM(14, "전남"),
    GYEONGBUK(15, "경북"),
    GYEONGNAM(16, "경남"),
    JEJU(17, "제주");

    private final int sourceCode;
    private final String displayName;

    Region(int sourceCode, String displayName) {
        this.sourceCode = sourceCode;
        this.displayName = displayName;
    }

    public int getSourceCode() {
        return sourceCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 원본 표기(예: "01. 서울")를 정규화하여 매칭한다.
     * 접두사 숫자 또는 이름 어느 쪽으로도 매칭을 시도한다.
     *
     * @return 매칭되는 Region, 인식할 수 없으면 empty
     */
    public static java.util.Optional<Region> fromSourceValue(String rawValue) {
        if (rawValue == null) {
            return java.util.Optional.empty();
        }
        String trimmed = rawValue.trim();
        String withoutPrefix = trimmed.replaceFirst("^\\d{1,2}\\.\\s*", "");
        for (Region region : values()) {
            if (region.displayName.equals(withoutPrefix) || region.displayName.equals(trimmed)) {
                return java.util.Optional.of(region);
            }
        }
        return java.util.Optional.empty();
    }
}