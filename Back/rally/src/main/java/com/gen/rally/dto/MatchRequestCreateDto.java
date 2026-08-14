package com.gen.rally.dto;

import com.gen.rally.entity.MatchRequest;
import lombok.*;

import java.time.LocalDate;

// 매칭 신청 시 프론트에서 전달하는 데이터 형식
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchRequestCreateDto {
    private int gameType;
    private int gameStyle;
    private boolean sameGender;

    private LocalDate gameDate;
    private int startTime;
    private int endTime;

    private String place;
    private double latitude;
    private double longitude;

    // Entity -> DTO 변환
    public static MatchRequestCreateDto from(MatchRequest request) {
        return MatchRequestCreateDto.builder()
                .gameType(request.getGameType().getCode())
                .gameStyle(request.getGameStyle().getCode())
                .sameGender(request.isSameGender())
                .gameDate(request.getGameDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .place(request.getPlace())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
    }
}