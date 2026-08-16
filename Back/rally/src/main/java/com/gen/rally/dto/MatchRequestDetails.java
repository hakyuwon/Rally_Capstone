package com.gen.rally.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchRequestDetails {
    private MatchRequestInfoDto my;  // 내 신청 정보
    private CandidateResponseDto opponent;   // 상대 신청 정보

    public static MatchRequestDetails of(MatchRequestInfoDto myInfo, CandidateResponseDto opponentDto) {
        return MatchRequestDetails.builder()
                .my(myInfo)
                .opponent(opponentDto)
                .build();
    }
}
