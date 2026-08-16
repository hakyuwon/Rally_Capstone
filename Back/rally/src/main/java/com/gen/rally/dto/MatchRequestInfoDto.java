package com.gen.rally.dto;

import com.gen.rally.entity.MatchRequest;
import com.gen.rally.enums.GameStyle;
import com.gen.rally.enums.GameType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class MatchRequestInfoDto {
    private String place;
    private String date;
    private String timeRange;
    private GameStyle gameStyle;
    private GameType gameType;

    public static MatchRequestInfoDto from(MatchRequest my) {
        return MatchRequestInfoDto.builder()
                .place(my.getPlace())
                .date(my.getGameDate() != null ? my.getGameDate().toString() : null)
                .timeRange(String.format("%02d:00~%02d:00", my.getStartTime(), my.getEndTime()))
                .gameStyle(my.getGameStyle())
                .gameType(my.getGameType())
                .build();
    }
}
