package com.festival.budgetassist.estimate;

import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/metadata 응답. 프론트엔드는 지역/유형/장소유형/시군구 선택지를
 * 하드코딩하지 않고 전부 이 응답에서 만든다.
 */
public record MetadataResponse(
        List<CodeName> regions,
        List<CodeName> festivalTypes,
        List<CodeName> venueTypes,
        /** 광역지역 code -> 그 지역에서 실제 관측된 기초자치단체명 목록(선택 입력용). */
        Map<String, List<String>> districtsByRegion,
        DurationMeta duration,
        int datasetYear
) {
}