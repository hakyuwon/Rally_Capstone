package com.gen.rally.service;

import com.gen.rally.entity.MatchRequest;
import com.gen.rally.entity.User;
import com.gen.rally.enums.GameStyle;
import com.gen.rally.enums.GameType;
import com.gen.rally.enums.State;
import com.gen.rally.repository.MatchRequestRepository;
import com.gen.rally.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class OomTest {
    @Autowired
    private MatchRequestRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRequestService matchService;

    @Test
    @DisplayName("15만 건 더미 데이터 넣기")
    void insertDummyData() {

        User dummyUser = userRepository.findAll().get(0);

        List<MatchRequest> dummies = new ArrayList<>();
        for (int i = 0; i < 150000; i++) {
            MatchRequest req = MatchRequest.builder()
                    .user(dummyUser)                  // 연관관계 유저 세팅
                    .state(State.대기)
                    .place("OOM_TEST_DUMMY")          // 나중에 지우기 위한..
                    .gameDate(LocalDate.now())        // NPE 방지
                    .gameType(GameType.단식)
                    .gameStyle(GameStyle.상관없음)
                    .gender(dummyUser.getGender())
                    .skill(50)
                    .startTime(18)
                    .endTime(20)
                    .latitude(37.5665)
                    .longitude(126.9780)
                    .sameGender(false)
                    .build();
            dummies.add(req);

            if (i % 10000 == 0) {
                repository.saveAll(dummies);
                dummies.clear();
            }
        }
        repository.saveAll(dummies);
    }

    @Test
    @DisplayName("성능 비교: findAll() vs DB필터링")
    void comparePerformance() {
        String testUserId = "test123";
        Long testRequestId = 1L;

        // 1차 테스트: 레거시 방식 (findAll)
        long start1 = System.currentTimeMillis();
        try {
            matchService.findCandidatesLegacy(testUserId, testRequestId);
            long end1 = System.currentTimeMillis();
            System.out.println("기존 방식 (findAll) 수행 시간: " + (end1 - start1) + " ms");
        } catch (OutOfMemoryError e) {
            System.out.println("기존 방식 (findAll) 수행 중 OOM 발생");
        }

        // 2차 테스트: 개선된 방식 (DB 필터링)
        long start2 = System.currentTimeMillis();
        matchService.findCandidates(testUserId, testRequestId);
        long end2 = System.currentTimeMillis();
        System.out.println("개선 방식 (DB 필터링) 수행 시간: " + (end2 - start2) + " ms");
    }
}
