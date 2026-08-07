package com.festival.budgetassist.estimate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.FestivalType;
import com.festival.budgetassist.festival.domain.Region;
import com.festival.budgetassist.festival.domain.VenueType;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;

/**
 * GET /api/v1/metadata 계산. 지역/유형/장소유형은 enum에서, 시군구 목록은 실제 데이터에서
 * 뽑아낸다 - 프론트가 선택지를 하드코딩하지 않도록 하기 위함이다.
 */
@Service
class MetadataService {

    private static final int DURATION_MINIMUM = 2;
    private static final int DURATION_MAXIMUM_RECOMMENDED = 180;

    private final FestivalRecordRepository festivalRecordRepository;

    MetadataService(FestivalRecordRepository festivalRecordRepository) {
        this.festivalRecordRepository = festivalRecordRepository;
    }

    MetadataResponse getMetadata() {
        int datasetYear = festivalRecordRepository.findMaxDatasetYear().orElse(0);

        List<FestivalRecord> records = datasetYear == 0
                ? List.of()
                : festivalRecordRepository.findByDatasetYear(datasetYear);

        Map<String, List<String>> districtsByRegion = new TreeMap<>(records.stream()
                .filter(r -> r.getAdministrativeDistrict() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getRegion().name(),
                        Collectors.mapping(FestivalRecord::getAdministrativeDistrict, Collectors.toCollection(TreeSet::new))
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))));

        return new MetadataResponse(
                Arrays.stream(Region.values()).map(r -> new CodeName(r.name(), r.getDisplayName())).toList(),
                Arrays.stream(FestivalType.values()).map(t -> new CodeName(t.name(), t.getDisplayName())).toList(),
                Arrays.stream(VenueType.values()).map(v -> new CodeName(v.name(), v.getDisplayName())).toList(),
                districtsByRegion,
                new DurationMeta(DURATION_MINIMUM, DURATION_MAXIMUM_RECOMMENDED),
                datasetYear
        );
    }
}