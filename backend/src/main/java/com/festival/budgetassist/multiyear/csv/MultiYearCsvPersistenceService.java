package com.festival.budgetassist.multiyear.csv;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.multiyear.domain.MultiYearFestivalRecord;
import com.festival.budgetassist.multiyear.domain.MultiYearImportBatch;
import com.festival.budgetassist.multiyear.repository.MultiYearFestivalRecordRepository;
import com.festival.budgetassist.multiyear.repository.MultiYearImportBatchRepository;

/**
 * 실제 DB 쓰기를 담당하는 계층. {@link DatasetPersistenceService}(기존 2026 Excel Import)와
 * 동일한 원칙 - 파싱/검증/정규화는 전부 이 클래스 호출 이전에 메모리상에서 끝나 있어야 하고,
 * {@code @Transactional}은 여기서만 시작된다.
 */
@Service
class MultiYearCsvPersistenceService {

    private final MultiYearFestivalRecordRepository recordRepository;
    private final MultiYearImportBatchRepository batchRepository;

    MultiYearCsvPersistenceService(MultiYearFestivalRecordRepository recordRepository,
                                    MultiYearImportBatchRepository batchRepository) {
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * 이 CSV에 담긴 연도 전체를 하나의 트랜잭션으로 교체한다: (1) 연도별 기존 데이터 삭제
     * (2) 배치 기록 저장 (3) 신규 레코드 저장. 중간에 예외가 발생하면 전부 롤백된다.
     */
    @Transactional
    MultiYearImportBatch replaceAllYears(Map<Integer, List<MultiYearFestivalRecord>> recordsByYear, MultiYearImportBatch batchToSave) {
        recordsByYear.keySet().forEach(recordRepository::deleteByDatasetYear);

        MultiYearImportBatch savedBatch = batchRepository.save(batchToSave);
        recordsByYear.values().forEach(yearRecords -> {
            yearRecords.forEach(record -> record.setImportBatch(savedBatch));
            recordRepository.saveAll(yearRecords);
        });

        return savedBatch;
    }

    /** 구조적 검증 실패 등으로 Import를 진행하지 못했을 때, 시도 이력만 감사 로그로 남긴다. */
    @Transactional
    MultiYearImportBatch recordFailedAttempt(MultiYearImportBatch failedBatch) {
        return batchRepository.save(failedBatch);
    }
}