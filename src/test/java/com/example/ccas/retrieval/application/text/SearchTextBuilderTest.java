package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextBuilderTest {

    private final SearchTextBuilder searchTextBuilder = new SearchTextBuilder();
    private final StructuredMetadataExtractor metadataExtractor = new StructuredMetadataExtractor();

    @Test
    void buildsSearchTextForLostItemComplaintWithoutRiskSignals() {
        StructuredComplaint complaint = complaint(
                "공공장소 인근에서 지갑을 분실하여 찾는 방법을 문의하는 상황",
                context(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(fact("지갑 분실")),
                List.of(),
                List.of(subject(SubjectMatterCode.LOST_ITEM)),
                List.of(action(RequestedActionCode.SEARCH)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );

        String searchText = searchTextBuilder.buildForComplaint(complaint);

        assertThat(searchText).isEqualTo("""
                발생 상황: 공공장소 인근에서 지갑을 분실하여 찾는 방법을 문의하는 상황
                장소 맥락: 공공시설
                관계 맥락: 확인되지 않음
                발생 패턴: 일회성
                관찰된 사실: 지갑 분실
                위험 신호: 명시적 위험 신호 없음
                민원 대상: 유실물
                요청 행위: 검색 또는 조회 요청
                현재성: 과거 발생""");
    }

    @Test
    void buildsSearchTextForRepeatedThreatComplaint() {
        StructuredComplaint complaint = repeatedThreatComplaint();

        String searchText = searchTextBuilder.buildForComplaint(complaint);

        assertThat(searchText).contains("위험 신호: 살해 협박성 표현, 반복 접근 또는 감시 정황");
        assertThat(searchText).contains("민원 대상: 안전 위협");
        assertThat(searchText).contains("요청 행위: 신고 문의");
    }

    @Test
    void keepsUncertainRiskSignalInSearchText() {
        StructuredComplaint complaint = complaint(
                "상대방이 칼 같은 물체를 들고 있었던 것으로 보이는 상황",
                context(PlaceType.ROAD, RelationshipType.STRANGER, TimePattern.ONE_TIME),
                List.of(fact("칼 같은 물체 소지 가능성", Certainty.UNCERTAIN)),
                List.of(risk(RiskSignalCode.WEAPON, Certainty.UNCERTAIN)),
                List.of(subject(SubjectMatterCode.SAFETY_THREAT)),
                List.of(action(RequestedActionCode.REPORT)),
                OngoingStatus.UNKNOWN,
                List.of()
        );

        String searchText = searchTextBuilder.buildForComplaint(complaint);

        assertThat(searchText).contains("흉기 또는 무기 관련 위험(확인 필요)");
    }

    @Test
    void buildsSearchTextForOnlineTransactionComplaint() {
        StructuredComplaint complaint = complaint(
                "온라인 거래 상대방과의 거래 피해가 의심되어 신고 방법을 문의하는 상황",
                context(PlaceType.ONLINE, RelationshipType.TRANSACTION_PARTY, TimePattern.ONE_TIME),
                List.of(fact("온라인 거래 피해 의심")),
                List.of(),
                List.of(subject(SubjectMatterCode.CYBER_TRANSACTION)),
                List.of(action(RequestedActionCode.REPORT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );

        String searchText = searchTextBuilder.buildForComplaint(complaint);

        assertThat(searchText).contains("민원 대상: 온라인 거래 피해 의심");
        assertThat(searchText).contains("요청 행위: 신고 문의");
        assertThat(searchText).contains("위험 신호: 명시적 위험 신호 없음");
    }

    @Test
    void excludesMissingInformationFromSearchText() {
        StructuredComplaint complaint = complaint(
                "반복 방문과 협박성 발언으로 안전 위협을 느끼는 상황",
                context(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(fact("반복 방문")),
                List.of(risk(RiskSignalCode.DEATH_THREAT)),
                List.of(subject(SubjectMatterCode.SAFETY_THREAT)),
                List.of(action(RequestedActionCode.REPORT)),
                OngoingStatus.REPEATED_AND_MAY_CONTINUE,
                List.of("현재 상대방이 현장에 있는지 여부", "흉기 소지 여부")
        );

        String searchText = searchTextBuilder.buildForComplaint(complaint);

        assertThat(searchText)
                .doesNotContain("현재 상대방이 현장에 있는지 여부")
                .doesNotContain("흉기 소지 여부");
    }

    @Test
    void extractsMetadataCodesAndRemovesDuplicatesKeepingFirstOrder() {
        StructuredComplaint complaint = complaint(
                "이웃 주민이 반복적으로 찾아오고 죽이겠다고 말한 상황",
                context(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(fact("반복 방문"), fact("죽이겠다는 발언")),
                List.of(
                        risk(RiskSignalCode.DEATH_THREAT),
                        risk(RiskSignalCode.STALKING_PATTERN),
                        risk(RiskSignalCode.DEATH_THREAT)
                ),
                List.of(subject(SubjectMatterCode.SAFETY_THREAT), subject(SubjectMatterCode.SAFETY_THREAT)),
                List.of(action(RequestedActionCode.REPORT), action(RequestedActionCode.REPORT)),
                OngoingStatus.REPEATED_AND_MAY_CONTINUE,
                List.of()
        );

        RetrievalMetadata metadata = metadataExtractor.extract(complaint);

        assertThat(metadata.subjectMatterCodes()).containsExactly("SAFETY_THREAT");
        assertThat(metadata.requestedActionCodes()).containsExactly("REPORT");
        assertThat(metadata.riskSignalCodes()).containsExactly("DEATH_THREAT", "STALKING_PATTERN");
        assertThat(metadata.placeType()).isEqualTo("RESIDENCE");
        assertThat(metadata.relationshipType()).isEqualTo("NEIGHBOR");
        assertThat(metadata.ongoingStatus()).isEqualTo("REPEATED_AND_MAY_CONTINUE");
    }

    private StructuredComplaint repeatedThreatComplaint() {
        return complaint(
                "이웃 주민이 반복적으로 찾아오고 죽이겠다고 말한 상황",
                context(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(fact("반복 방문"), fact("죽이겠다는 발언")),
                List.of(risk(RiskSignalCode.DEATH_THREAT), risk(RiskSignalCode.STALKING_PATTERN)),
                List.of(subject(SubjectMatterCode.SAFETY_THREAT)),
                List.of(action(RequestedActionCode.REPORT)),
                OngoingStatus.REPEATED_AND_MAY_CONTINUE,
                List.of()
        );
    }

    private StructuredComplaint complaint(
            String factualSummary,
            ComplaintContext context,
            List<ObservedFact> observedFacts,
            List<RiskSignal> riskSignals,
            List<SubjectMatter> subjectMatters,
            List<RequestedAction> requestedActions,
            OngoingStatus ongoingStatus,
            List<String> missingInformation
    ) {
        return new StructuredComplaint(
                factualSummary,
                context,
                observedFacts,
                riskSignals,
                subjectMatters,
                requestedActions,
                ongoingStatus,
                missingInformation
        );
    }

    private ComplaintContext context(PlaceType placeType, RelationshipType relationshipType, TimePattern timePattern) {
        return new ComplaintContext(placeType, relationshipType, timePattern);
    }

    private ObservedFact fact(String value) {
        return fact(value, Certainty.EXPLICIT);
    }

    private ObservedFact fact(String value, Certainty certainty) {
        return new ObservedFact(FactType.ACTION, value, value, certainty);
    }

    private RiskSignal risk(RiskSignalCode code) {
        return risk(code, Certainty.EXPLICIT);
    }

    private RiskSignal risk(RiskSignalCode code, Certainty certainty) {
        return new RiskSignal(code, code.searchLabel(), certainty);
    }

    private SubjectMatter subject(SubjectMatterCode code) {
        return new SubjectMatter(code, code.searchLabel(), Certainty.EXPLICIT);
    }

    private RequestedAction action(RequestedActionCode code) {
        return new RequestedAction(code, code.searchLabel(), Certainty.EXPLICIT);
    }
}
