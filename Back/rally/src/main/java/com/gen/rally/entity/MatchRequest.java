package com.gen.rally.entity;

import com.gen.rally.dto.MatchRequestCreateDto;
import com.gen.rally.enums.GameStyle;
import com.gen.rally.enums.GameType;
import com.gen.rally.enums.Gender;
import com.gen.rally.enums.State;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MatchRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private int skill;

    @Enumerated(EnumType.STRING)
    private GameType gameType;   // 경기 유형: 단식(0), 복식(1)

    @Enumerated(EnumType.STRING)
    private GameStyle gameStyle; // 경기 스타일: 상관없음(0), 편하게(1), 열심히(2)

    private boolean sameGender;   // 경기 상대 성별: 상관없음(0), 같은 성별(1)
    //시간
    private LocalDate gameDate;
    private int startTime;
    private int endTime;
    // 장소
    private String place;
    private double latitude;
    private double longitude;

    @Enumerated(EnumType.STRING)
    private State state;

    @Builder.Default
    private LocalDateTime createdAt =  LocalDateTime.now();

    public static MatchRequest of(User user, MatchRequestCreateDto dto) {
        return MatchRequest.builder()
                .user(user)
                .gender(user.getGender())
                .skill(user.getSkill())
                .gameType(GameType.fromCode(dto.getGameType()))
                .gameStyle(GameStyle.fromCode(dto.getGameStyle()))
                .sameGender(dto.isSameGender())
                .gameDate(dto.getGameDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .place(dto.getPlace())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .state(State.대기)
                .build();
    }

    public void changeState(State newState) {
        this.state = newState;
    }

}
