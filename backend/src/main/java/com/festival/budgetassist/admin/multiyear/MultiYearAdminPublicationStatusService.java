package com.festival.budgetassist.admin.multiyear;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatus;
import com.festival.budgetassist.multiyear.domain.MultiYearDatasetPublicationStatusValue;
import com.festival.budgetassist.multiyear.repository.MultiYearDatasetPublicationStatusRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;

/**
 * 연도별 "다년도 개최계획 데이터셋 공개 완성" 상태({@link MultiYearDatasetPublicationStatusValue})를
 * 확인/설정하는 관리자 전용 서비스. {@link MultiYearAdminDatasetQueryService}(순수 조회 전용)와
 * 의도적으로 분리했다 - 이 서비스만 예외적으로 쓰기 메서드({@link #setStatus})를 갖는다. 운영자가
 * "지금 보유한 2026 파일이 실제 전체 공개 계획본"이라고 판단을 내린 뒤에만 수동으로
 * {@code PUBLISHED_PLAN_COMPLETE}로 표시하기 위한 것이며, 자동으로 표시하는 로직은 어디에도
 * 없다(사용자 요청: "자동으로 강제로 넣지는 말라").
 *
 * <p>{@link MultiYearDatasetPublicationStatusValue#PUBLISHED_PLAN_COMPLETE}는 "그 해 축제가 전부
 * 끝났다"가 아니라 "그 해 개최계획 데이터셋이 공개 기준으로 완성되어 같은 연도 안에서도 planning
 * reference로 쓸 수 있다"는 뜻이다(엔티티 Javadoc 참고) - 이 화면/API는 그 판단을 사람이 내리는
 * 곳일 뿐이다.</p>
 */
@Service
public class MultiYearAdminPublicationStatusService {

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearDatasetPublicationStatusRepository statusRepository;

    MultiYearAdminPublicationStatusService(MultiYearFestivalRecordRepository recordRepository,
                                            MultiYearDatasetPublicationStatusRepository statusRepository) {
        this.recordRepository = recordRepository;
        this.statusRepository = statusRepository;
    }

    public MultiYearAdminPublicationStatusResponse list() {
        List<Integer> years = recordRepository.findDistinctDatasetYears();
        Map<Integer, MultiYearDatasetPublicationStatus> byYear = statusRepository.findAll().stream()
                .collect(Collectors.toMap(MultiYearDatasetPublicationStatus::getDatasetYear, s -> s));

        List<MultiYearAdminPublicationStatusEntry> entries = years.stream()
                .map(year -> {
                    MultiYearDatasetPublicationStatus existing = byYear.get(year);
                    MultiYearDatasetPublicationStatusValue status = existing != null ? existing.getStatus() : MultiYearDatasetPublicationStatusValue.PARTIAL;
                    Instant publishedAt = existing != null ? existing.getPublishedAt() : null;
                    int recordCount = (int) recordRepository.countByDatasetYear(year);
                    return new MultiYearAdminPublicationStatusEntry(year, status, publishedAt, recordCount);
                })
                .toList();

        return new MultiYearAdminPublicationStatusResponse(entries);
    }

    /**
     * 연도 1개의 status를 upsert한다 - 이 서비스에서 유일하게 DB에 쓰는 메서드다. {@code
     * PUBLISHED_PLAN_COMPLETE}로 바꿀 때만 {@code publishedAt}을 현재 시각으로 기록하고,
     * {@code PARTIAL}로 되돌리면 {@code publishedAt}도 비운다.
     */
    public MultiYearAdminPublicationStatusEntry setStatus(int year, MultiYearDatasetPublicationStatusValue status) {
        MultiYearDatasetPublicationStatus entity = statusRepository.findByDatasetYear(year)
                .orElseGet(() -> MultiYearDatasetPublicationStatus.builder().datasetYear(year).build());
        entity.setStatus(status);
        entity.setPublishedAt(status == MultiYearDatasetPublicationStatusValue.PUBLISHED_PLAN_COMPLETE ? Instant.now() : null);
        MultiYearDatasetPublicationStatus saved = statusRepository.save(entity);

        int recordCount = (int) recordRepository.countByDatasetYear(year);
        return new MultiYearAdminPublicationStatusEntry(saved.getDatasetYear(), saved.getStatus(), saved.getPublishedAt(), recordCount);
    }
}