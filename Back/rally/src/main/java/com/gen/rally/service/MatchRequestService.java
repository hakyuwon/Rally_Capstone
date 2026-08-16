package com.gen.rally.service;

import com.gen.rally.dto.*;
import com.gen.rally.entity.*;
import com.gen.rally.enums.GameStyle;
import com.gen.rally.enums.GameType;
import com.gen.rally.enums.State;
import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import com.gen.rally.repository.GameRepository;
import com.gen.rally.repository.MatchInvitationRepository;
import com.gen.rally.repository.MatchRequestRepository;
import com.gen.rally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchRequestService {
    private final MatchRequestRepository matchRequestRepository;
    private final MatchInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    DateTimeFormatter formatter= DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREA);

    @Transactional
    public Long createMatchRequest(String userId, MatchRequestCreateDto userInput) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 과거에 사용자가 동일한 날짜, 겹치는 시간의 신청을 했는지 확인
        List<MatchRequest> duplicates = matchRequestRepository.findOverlappingRequests(
                userId,
                userInput.getGameDate(),
                userInput.getStartTime(),
                userInput.getEndTime()
        );
        if (!duplicates.isEmpty()) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        MatchRequest matchRequest = MatchRequest.of(user,userInput);
        MatchRequest saved = matchRequestRepository.save(matchRequest);
        return saved.getRequestId();
    }

    @Transactional(readOnly = true)
    public List<CandidateResponseDto> findCandidates(String userId, Long requestId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        MatchRequest myReq = matchRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        if (!myReq.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        int mySkill = myReq.getSkill();

        // DB에서 사전 필터링
        List<MatchRequest> potentialCandidates = matchRequestRepository.findPotentialCandidates(
                userId,
                State.대기,
                myReq.getGameDate(),
                myReq.getGameType(),
                mySkill - 10,
                mySkill + 10,
                myReq.getStartTime(),
                myReq.getEndTime()
        );

        // DB에서 필터링 안 되는 것들을 여기서
        List<MatchRequest> candidates = potentialCandidates.stream()
                // 같은 성별 선호
                .filter(r -> {
                    if (myReq.isSameGender()) {
                        return r.getGender() == myReq.getGender();
                    } else {
                        return !r.isSameGender() || (r.getGender() == myReq.getGender());
                    }
                })
                // 게임 스타일
                .filter(r -> {
                    int myStyle = myReq.getGameStyle().getCode();
                    int otherStyle = r.getGameStyle().getCode();
                    return myStyle == 0 || otherStyle == 0 || otherStyle == myStyle;
                })
                // 반경 10km 이내
                .filter(r -> haversine(myReq.getLatitude(), myReq.getLongitude(),
                        r.getLatitude(), r.getLongitude()) <= 10)
                .collect(Collectors.toList());

        // 코사인 유사도 기반 상위 3명 정렬
        List<MatchRequest> topCandidates = candidates.stream()
                .map(r -> Map.entry(r, cosineSimilarity(
                        new double[]{
                                myReq.getSkill() / 100.0,             // 내 요청 벡터화
                                myReq.getStartTime() / 24.0,
                                myReq.getEndTime() / 24.0,
                                (myReq.getGameStyle().getCode() == 1 || myReq.getGameStyle().getCode() == 0) ? 1.0 : 0.0,
                                (myReq.getGameStyle().getCode() == 2 || myReq.getGameStyle().getCode() == 0) ? 1.0 : 0.0
                        },
                        new double[]{
                                r.getSkill() / 100.0, // 후보군 요청 벡터화
                                r.getStartTime() / 24.0,
                                r.getEndTime() / 24.0,
                                (r.getGameStyle().getCode() == 1 || r.getGameStyle().getCode() == 0) ? 1.0 : 0.0,
                                (r.getGameStyle().getCode() == 2 || r.getGameStyle().getCode() == 0) ? 1.0 : 0.0
                        })))
                .sorted(Map.Entry.<MatchRequest, Double>comparingByValue().reversed()) // 결과 내림차순 정렬
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

         // dto 변환
        return topCandidates.stream()
                .map(r -> {
                    double distance = haversine(myReq.getLatitude(), myReq.getLongitude(),
                            r.getLatitude(), r.getLongitude());
                    double winningRate = 0;
                    int skillGap = 0;

                    if (myReq.getGameType().getCode() == 0) {
                        winningRate = calculateWinningRate(r.getUser().getUserId());
                    } else {
                        skillGap = Math.abs(r.getSkill() - myReq.getSkill());
                    }

                    int isSameTier;
                    if (user.getTier() == r.getUser().getTier()) {
                        isSameTier = 1;
                    } else if (user.getTier().getCode() < r.getUser().getTier().getCode()) {
                        isSameTier = 0;
                    } else {
                        isSameTier = -1;
                    }

                    return new CandidateResponseDto(r, myReq, distance, winningRate, skillGap, isSameTier);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MatchRequestDetails getMatchRequestDetails(String userId, Long myRequestId, Long opponentRequestId) {
        MatchRequest my = matchRequestRepository.findByRequestId(myRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        MatchRequest opponent = matchRequestRepository.findByRequestId(opponentRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_INVITATION_NOT_FOUND));

        double distance = haversine(my.getLatitude(), my.getLongitude(), opponent.getLatitude(), opponent.getLongitude());

        double winningRate = 0.0;
        int skillGap = 0;

        if (opponent.getGameType().getCode() == 0) {
            winningRate = calculateWinningRate(opponent.getUser().getUserId());
        } else {
            skillGap = Math.abs(opponent.getUser().getSkill() - my.getUser().getSkill());
        }

        int isSameTier = (my.getUser().getTier() == opponent.getUser().getTier()) ? 1 :
                (my.getUser().getTier().getCode() < opponent.getUser().getTier().getCode()) ? 0 : -1;

        // dto 변환
        MatchRequestInfoDto myInfo = MatchRequestInfoDto.from(my);
        CandidateResponseDto opponentDto = new CandidateResponseDto(opponent, my, distance, winningRate, skillGap, isSameTier);

        return MatchRequestDetails.of(myInfo, opponentDto);
    }

    public List<MatchSeekingItem> findSeekingMatchByUser(String userId) {
        List<State> states = java.util.List.of(State.대기, State.요청중);
        List<MatchRequest> list =
                matchRequestRepository.findByUserAndStates(userId, states);

        return list.stream().map(r -> new MatchSeekingItem(
                r.getRequestId(),
                r.getGameDate() != null ? r.getGameDate().format(formatter) : null,
                r.getGameType() != null ? r.getGameType().name() : null,
                r.getGameStyle() != null ? r.getGameStyle().name() : null,
                timeFormat(r.getStartTime(), r.getEndTime()),
                r.getPlace(),
                r.getState() != null ? r.getState().name() : null,
                r.getCreatedAt().toString()
        )).collect(Collectors.toList());
    }

    @Transactional
    public void cancelRequest(String userId, Long requestId) {
        MatchRequest req = matchRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        if (!req.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        invitationRepository.deleteAllByRequestId(requestId);
        matchRequestRepository.delete(req);
    }

    private String timeFormat (int h1, int h2) {
        return String.format("%02d:00~%02d:00", h1, h2);
    }

    // 코사인 유사도
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Haversine 거리 계산 (단위: km)
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // 최근 5경기 승률 계산
    private double calculateWinningRate(String userId) {
        List<Game> games = gameRepository.findRecentGamesByUserId(userId, PageRequest.of(0, 5));
        long wins = games.stream()
                .filter(g -> g.getWinner() != null && g.getWinner().getUserId().equals(userId))
                .count();
        return games.isEmpty() ? 0.0 : (wins * 100.0 / games.size());
    }
}