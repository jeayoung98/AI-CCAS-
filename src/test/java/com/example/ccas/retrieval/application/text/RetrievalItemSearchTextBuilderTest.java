package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalItemSearchTextBuilderTest {

    private final SearchTextBuilder searchTextBuilder = new SearchTextBuilder();
    private final RetrievalItemMetadataExtractor metadataExtractor = new RetrievalItemMetadataExtractor();

    @Test
    void buildsSearchTextForLostItemCategoryReference() {
        CategoryReference reference = lostItemReference();

        String searchText = searchTextBuilder.buildForCategoryReference(reference);

        assertThat(searchText).isEqualTo("""
                자료 유형: 민원 유형 기준
                민원 유형: 유실물 관련 민원
                설명: 물건을 잃어버린 사용자가 검색 또는 처리 방법을 문의하는 민원 기준
                포함 상황: 지갑, 휴대전화, 가방 등 소지품 분실, 분실물 조회 또는 습득물 확인 요청
                제외 상황: 절도 피해를 명시적으로 주장하는 경우
                관련 민원 대상: 유실물
                관련 요청 행위: 검색 또는 조회 요청, 상담 요청
                주의할 위험 신호: 별도 위험 신호 없음
                공식 안내 근거 필요 여부: 필요함""");
    }

    @Test
    void buildsSearchTextForSafetyThreatCategoryReference() {
        CategoryReference reference = new CategoryReference(
                "SAFETY_THREAT_REFERENCE",
                "안전 위협 관련 민원",
                "신체적 안전 위험이나 협박 가능성이 있는 민원 기준",
                List.of("위협 발언 또는 흉기 언급", "반복 접근이나 현재 폭행 가능성"),
                List.of(),
                List.of(RiskSignalCode.WEAPON, RiskSignalCode.DEATH_THREAT),
                List.of(SubjectMatterCode.SAFETY_THREAT),
                List.of(RequestedActionCode.REPORT, RequestedActionCode.CONSULT),
                true
        );

        String searchText = searchTextBuilder.buildForCategoryReference(reference);

        assertThat(searchText).contains("관련 민원 대상: 안전 위협");
        assertThat(searchText).contains("관련 요청 행위: 신고 문의, 상담 요청");
        assertThat(searchText).contains("주의할 위험 신호: 흉기 또는 무기 관련 위험, 살해 협박성 표현");
        assertThat(searchText).contains("제외 상황: 별도 제외 기준 없음");
    }

    @Test
    void printsEmptyExclusionCriteriaFallback() {
        CategoryReference reference = new CategoryReference(
                "PATROL_REQUEST_REFERENCE",
                "순찰 요청 기준",
                "순찰 강화를 요청하는 민원 기준",
                List.of("특정 지역 순찰 요청"),
                List.of(),
                List.of(),
                List.of(SubjectMatterCode.PATROL_REQUEST),
                List.of(RequestedActionCode.APPLY),
                false
        );

        String searchText = searchTextBuilder.buildForCategoryReference(reference);

        assertThat(searchText).contains("제외 상황: 별도 제외 기준 없음");
        assertThat(searchText).contains("공식 안내 근거 필요 여부: 필요하지 않음");
    }

    @Test
    void buildsSearchTextForLostItemOfficialGuideChunk() {
        OfficialGuideChunk guideChunk = lostItemGuideChunk();

        String searchText = searchTextBuilder.buildForOfficialGuide(guideChunk);

        assertThat(searchText).isEqualTo("""
                자료 유형: 공식 안내 자료
                문서 제목: 경찰 민원 안내
                문서 구간: 유실물 민원 안내
                대상 민원: 유실물
                관련 요청 행위: 검색 또는 조회 요청, 신고 문의
                관련 위험 신호: 별도 위험 신호 없음
                안내 내용: 분실한 물건에 대한 조회 및 신고는 유실물 관련 민원 절차를 통해 확인할 수 있다.""");
    }

    @Test
    void buildsSearchTextForSafetyThreatOfficialGuideChunk() {
        OfficialGuideChunk guideChunk = new OfficialGuideChunk(
                "경찰 민원 안내",
                "즉각적인 안전 위험이 있는 상황",
                "현재 신체적 위험이 발생하고 있거나 즉각적인 보호가 필요한 상황은 긴급 대응 필요 여부를 우선 확인한다.",
                List.of(SubjectMatterCode.SAFETY_THREAT),
                List.of(RequestedActionCode.REPORT),
                List.of(RiskSignalCode.WEAPON, RiskSignalCode.ASSAULT_IN_PROGRESS)
        );

        String searchText = searchTextBuilder.buildForOfficialGuide(guideChunk);

        assertThat(searchText).contains("대상 민원: 안전 위협");
        assertThat(searchText).contains("관련 요청 행위: 신고 문의");
        assertThat(searchText).contains("관련 위험 신호: 흉기 또는 무기 관련 위험, 현재 폭행 진행 가능성");
    }

    @Test
    void extractsCategoryReferenceMetadataCodesWithDistinctOrder() {
        CategoryReference reference = new CategoryReference(
                "LOST_ITEM_REFERENCE",
                "유실물 관련 민원",
                "유실물 기준",
                List.of("분실물 조회"),
                List.of(),
                List.of(),
                List.of(SubjectMatterCode.LOST_ITEM, SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.CONSULT, RequestedActionCode.SEARCH),
                true
        );

        RetrievalMetadata metadata = metadataExtractor.extractFromCategoryReference(reference);

        assertThat(metadata.subjectMatterCodes()).containsExactly("LOST_ITEM");
        assertThat(metadata.requestedActionCodes()).containsExactly("SEARCH", "CONSULT");
        assertThat(metadata.riskSignalCodes()).isEmpty();
        assertThat(metadata.placeType()).isNull();
        assertThat(metadata.relationshipType()).isNull();
        assertThat(metadata.ongoingStatus()).isNull();
    }

    @Test
    void extractsOfficialGuideMetadataCodesWithDistinctOrder() {
        OfficialGuideChunk guideChunk = new OfficialGuideChunk(
                "경찰 민원 안내",
                "유실물 민원 안내",
                "유실물 안내",
                List.of(SubjectMatterCode.LOST_ITEM, SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.CONSULT, RequestedActionCode.SEARCH),
                List.of(RiskSignalCode.DEATH_THREAT, RiskSignalCode.WEAPON, RiskSignalCode.DEATH_THREAT)
        );

        RetrievalMetadata metadata = metadataExtractor.extractFromOfficialGuide(guideChunk);

        assertThat(metadata.subjectMatterCodes()).containsExactly("LOST_ITEM");
        assertThat(metadata.requestedActionCodes()).containsExactly("SEARCH", "CONSULT");
        assertThat(metadata.riskSignalCodes()).containsExactly("DEATH_THREAT", "WEAPON");
        assertThat(metadata.placeType()).isNull();
        assertThat(metadata.relationshipType()).isNull();
        assertThat(metadata.ongoingStatus()).isNull();
    }

    private CategoryReference lostItemReference() {
        return new CategoryReference(
                "LOST_ITEM_REFERENCE",
                "유실물 관련 민원",
                "물건을 잃어버린 사용자가 검색 또는 처리 방법을 문의하는 민원 기준",
                List.of("지갑, 휴대전화, 가방 등 소지품 분실", "분실물 조회 또는 습득물 확인 요청"),
                List.of("절도 피해를 명시적으로 주장하는 경우"),
                List.of(),
                List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.CONSULT),
                true
        );
    }

    private OfficialGuideChunk lostItemGuideChunk() {
        return new OfficialGuideChunk(
                "경찰 민원 안내",
                "유실물 민원 안내",
                "분실한 물건에 대한 조회 및 신고는 유실물 관련 민원 절차를 통해 확인할 수 있다.",
                List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.REPORT),
                List.of()
        );
    }
}
