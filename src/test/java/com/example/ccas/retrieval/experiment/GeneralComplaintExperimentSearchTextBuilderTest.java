package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintContext;
import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExperimentObservedFact;
import com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralComplaintExperimentSearchTextBuilderTest {

    private final GeneralComplaintExperimentSearchTextBuilder builder = new GeneralComplaintExperimentSearchTextBuilder();

    @Test
    void withTitleIncludesTitleAndAllowedFieldsOnly() {
        String searchText = builder.build(record(), ExperimentSearchTextVariant.WITH_TITLE);

        assertThat(searchText).isEqualTo("""
                제목: 버스 정거장 개선
                민원 요약: 버스 정거장 위치 조정을 요청하는 민원
                핵심 사실: 버스 정거장 대기 불편, 정류장 동선 비효율
                발생 장소 유형: ROAD
                발생 패턴: ONGOING
                """.stripTrailing());
        assertThat(searchText).doesNotContain("original_content", "dept", "riskSignals", "subjectMatters", "requestedActions");
    }

    @Test
    void withoutTitleOmitsTitle() {
        String searchText = builder.build(record(), ExperimentSearchTextVariant.WITHOUT_TITLE);

        assertThat(searchText).doesNotContain("제목:");
        assertThat(searchText).contains("민원 요약: 버스 정거장 위치 조정을 요청하는 민원");
    }

    private ExperimentComplaintRecord record() {
        return new ExperimentComplaintRecord(
                1,
                "버스 정거장 개선",
                true,
                new ExperimentStructuredComplaint(
                        "버스 정거장 위치 조정을 요청하는 민원",
                        new ExperimentComplaintContext("ROAD", "ONGOING"),
                        List.of(
                                new ExperimentObservedFact("버스 정거장 대기 불편"),
                                new ExperimentObservedFact("정류장 동선 비효율")
                        )
                )
        );
    }
}
