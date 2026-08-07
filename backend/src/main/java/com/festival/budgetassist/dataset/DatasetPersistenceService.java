package com.festival.budgetassist.dataset;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.festival.budgetassist.festival.domain.DatasetImportBatch;
import com.festival.budgetassist.festival.domain.FestivalRecord;
import com.festival.budgetassist.festival.domain.ImportWarning;
import com.festival.budgetassist.festival.repository.DatasetImportBatchRepository;
import com.festival.budgetassist.festival.repository.FestivalRecordRepository;
import com.festival.budgetassist.festival.repository.ImportWarningRepository;

/**
 * 실제 DB 쓰기를 담당하는 계층. 여기서만 트랜잭션이 시작된다 — 파싱/검증/정규화는
 * 전부 이 클래스 호출 이전에 메모리상에서 끝나 있어야 한다.
 *
 * <p>{@code @Transactional}은 스프링 프록시를 통해 적용되므로, 같은 클래스 내부에서
 * self-invocation 하면 안 된다(적용 안 됨). 그래서 {@link FestivalExcelImporter}와
 * 별도 빈으로 분리했다.</p>
 */
@Service
class DatasetPersistenceService {

    private final FestivalRecordRepository festivalRecordRepository;
    private final DatasetImportBatchRepository datasetImportBatchRepository;
    private final ImportWarningRepository importWarningRepository;

    DatasetPersistenceService(FestivalRecordRepository festivalRecordRepository,
                               DatasetImportBatchRepository datasetImportBatchRepository,
                               ImportWarningRepository importWarningRepository) {
        this.festivalRecordRepository = festivalRecordRepository;
        this.datasetImportBatchRepository = datasetImportBatchRepository;
        this.importWarningRepository = importWarningRepository;
    }

    /**
     * 연도 단위 전체 교체. 하나의 트랜잭션으로 (1) 기존 연도 데이터 삭제 (2) 배치 기록 저장
     * (3) 신규 레코드 저장 (4) 경고 저장을 수행한다. 중간에 예외가 발생하면 전부 롤백되어
     * 기존 데이터가 그대로 유지된다.
     */
    @Transactional
    DatasetImportBatch replaceYearData(int datasetYear, List<FestivalRecord> records, DatasetImportBatch batchToSave, List<RowWarning> warnings) {
        festivalRecordRepository.deleteByDatasetYear(datasetYear);

        DatasetImportBatch savedBatch = datasetImportBatchRepository.save(batchToSave);
        records.forEach(record -> record.setImportBatch(savedBatch));
        festivalRecordRepository.saveAll(records);

        if (!warnings.isEmpty()) {
            List<ImportWarning> warningEntities = warnings.stream()
                    .map(w -> ImportWarning.builder()
                            .batch(savedBatch)
                            .sourceRowNumber(w.sourceRowNumber())
                            .message(w.message())
                            .build())
                    .toList();
            importWarningRepository.saveAll(warningEntities);
        }

        return savedBatch;
    }

    /**
     * 구조적 검증 실패 등으로 Import를 진행하지 못했을 때, festival_record 테이블은 건드리지
     * 않고 시도 이력만 감사 로그로 남긴다. 독립된 트랜잭션이라 메인 흐름과 무관하게 커밋된다.
     */
    @Transactional
    DatasetImportBatch recordFailedAttempt(DatasetImportBatch failedBatch) {
        return datasetImportBatchRepository.save(failedBatch);
    }
}