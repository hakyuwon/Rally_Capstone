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

        // 이미 해당 조건의 신청이 있으면 에러 발생
        if (!duplicates.isEmpty()) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        MatchRequest matchRequest = new MatchRequest();
        matchRequest.setUser(user);
        matchRequest.setGameType(GameType.fromCode(userInput.getGameType()));
        matchRequest.setGameStyle(GameStyle.fromCode(userInput.getGameStyle()));
        matchRequest.setSameGender(userInput.isSameGender());
        matchRequest.setGameDate(userInput.getGameDate());
        matchRequest.setStartTime(userInput.getStartTime());
        matchRequest.setEndTime(userInput.getEndTime());
        matchRequest.setPlace(userInput.getPlace());
        matchRequest.setLatitude(userInput.getLatitude());
        matchRequest.setLongitude(userInput.getLongitude());

        // User에서 가져온 정보 저장
        matchRequest.setSkill(user.getSkill());
        matchRequest.setGender(user.getGender());

        // 상태 저장
        matchRequest.setState(State.대기);

        MatchRequest saved = matchRequestRepository.save(matchRequest);
        return saved.getRequestId();
    }

    public List<CandidateResponseDto> findCandidates(String userId, Long requestId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        MatchRequest request = matchRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        // 소유권 체크
        if (!request.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        MatchRequestCreateDto userInput = MatchRequestCreateDto.from(request);

        List<MatchRequest> candidates = matchRequestRepository.findAll().stream()
                .filter(r -> !r.getUser().getUserId().equals(user.getUserId()))
                // (1)  대기 상태이고 날짜, 경기 유형 일치
                .filter(r -> r.getGameDate().equals(userInput.getGameDate()) && r.getGameType().getCode() == userInput.getGameType() && r.getState()==State.대기)

                // (2) sameGender 조건 필터링
                .filter(r -> {
                    // 신청자가 같은 성별을 원할 경우
                    if (userInput.isSameGender()) {
                        return r.getUser().getGender() == user.getGender();
                    } else {
                        // 신청자가 상대 성별 상관없음 택한 경우 -> 똑같이 상관없음을 택했거나, 동일한 성별의 상대만을 남김
                        return ! r.isSameGender() || (r.isSameGender() && r.getUser().getGender() == user.getGender());
                    }
                })
                // (3) gameStyle 조건 필터링
                .filter(r -> {
                    int myStyle = userInput.getGameStyle();
                    int otherStyle = r.getGameStyle().getCode();

                    if (myStyle == 0) {
                        return true;
                    }
                    // 상대가 상관없음(0) 나와 같은 스타일인 경우만 허용
                    return otherStyle == 0 || otherStyle == myStyle;
                })
                // (4) 거리 10km 이내
                .filter(r -> haversine(userInput.getLatitude(), userInput.getLongitude(),
                        r.getLatitude(), r.getLongitude()) <= 10)

                // (5) 시간 1시간 이상 겹침
                .filter(r -> Math.max(0, Math.min(userInput.getEndTime(), r.getEndTime()) -
                        Math.max(userInput.getStartTime(), r.getStartTime())) >= 1)

                // (6) 실력 점수 10점 이내 차이
                .filter(r -> Math.abs(r.getUser().getSkill() - user.getSkill()) <= 10)

                .collect(Collectors.toList());

        // 코사인 유사도 기반 유사도 계산 ( startTime + endTime + skill + gameStyle)
        List<MatchRequest> topCandidates = candidates.stream()
                .map(r -> Map.entry(r, cosineSimilarity(
                        new double[]{
                                user.getSkill() / 100.0,
                                userInput.getStartTime() / 24.0,
                                userInput.getEndTime() / 24.0,
                                // GameStyle One-hot Encoding
                                (userInput.getGameStyle() == 1 || userInput.getGameStyle() == 0) ? 1.0 : 0.0, // "편하게" 또는 "상관없음"
                                (userInput.getGameStyle() == 2 || userInput.getGameStyle() == 0) ? 1.0 : 0.0  // "열심히" 또는 "상관없음"
                        },
                        new double[]{
                                r.getUser().getSkill() / 100.0,
                                r.getStartTime() / 24.0,
                                r.getEndTime() / 24.0,
                                (r.getGameStyle().getCode() == 1 || r.getGameStyle().getCode() == 0) ? 1.0 : 0.0,
                                (r.getGameStyle().getCode() == 2 || r.getGameStyle().getCode() == 0) ? 1.0 : 0.0
                        })))
                .sorted(Map.Entry.<MatchRequest, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

         // dto 변환
        return topCandidates.stream()
                .map(r -> {
                    double distance = haversine(userInput.getLatitude(), userInput.getLongitude(),
                            r.getLatitude(), r.getLongitude());
                    double winningRate = 0;
                    int skillGap = 0;
                    if(userInput.getGameType() == 0)
                        winningRate = calculateWinningRate(r.getUser().getUserId());
                    else
                        skillGap=Math.abs(r.getUser().getSkill()*2-r.getSkill()*2);

                    int isSameTier;
                    if (user.getTier() == r.getUser().getTier()) {
                        isSameTier=1;
                    } else if (user.getTier().getCode() < r.getUser().getTier().getCode()) {
                        isSameTier = 0;    // 사용자보다 상위 티어
                    } else {
                        isSameTier = -1;    // 사용자보다 하위 티어
                    }

                    return new CandidateResponseDto(r, userInput, distance, winningRate, skillGap, isSameTier);
                })
                .collect(Collectors.toList());
    }

    public MatchRequestDetails getMatchRequestDetails(String userId, Long myRequestId, Long opponentRequestId) {
        MatchRequest my = matchRequestRepository.findByRequestId(myRequestId)
                .stream()
                .max(Comparator.comparing(MatchRequest::getCreatedAt))
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        MatchRequest opponent = matchRequestRepository.findByRequestId(opponentRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_INVITATION_NOT_FOUND));

        double distance = haversine(my.getLatitude(), my.getLongitude(), opponent.getLatitude(), opponent.getLongitude());

        int overlappedHours = overlappedHours( my.getStartTime(), my.getEndTime(), opponent.getStartTime(), opponent.getEndTime());

        double winningRate = 0.0;
        int skillGap = 0;
        if (opponent.getGameType().getCode() == 0) {
            winningRate = calculateWinningRate(opponent.getUser().getUserId());
        } else {
            skillGap = Math.abs(opponent.getUser().getSkill() - my.getUser().getSkill());
        }

        int isSameTier = (my.getUser().getTier() == opponent.getUser().getTier()) ? 1 : (my.getUser().getTier().getCode() < opponent.getUser().getTier().getCode()) ? 0 : -1;

        MatchRequestInfoDto myInfo = toMyInfoDto(my);

        MatchRequestCreateDto myInput = MatchRequestCreateDto.from(my);

        var opponentDto = new CandidateResponseDto(opponent, myInput, distance, winningRate, skillGap, isSameTier);
        return MatchRequestDetails.builder()
                .my(myInfo)
                .opponent(opponentDto)
                .build();
    }
    private MatchRequestInfoDto toMyInfoDto(MatchRequest r) {
        MatchRequestInfoDto dto = new MatchRequestInfoDto();
        dto.setPlace(r.getPlace());
        dto.setDate(r.getGameDate() != null ? r.getGameDate().toString() : null);
        dto.setTimeRange(timeFormat(r.getStartTime(), r.getEndTime()));
        dto.setGameStyle(r.getGameStyle());
        dto.setGameType(r.getGameType());
        return dto;
    }
    public List<MatchSeekingItem> findSeekingMatchByUser(String userId) {
        var states = java.util.List.of(State.대기, State.요청중);
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
        // 매칭 신청 존재 여부 확인
        MatchRequest req = matchRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        // 사용자 소유권 확인
        if (!req.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 관련 매칭 invitation, request 삭제
        invitationRepository.deleteAllByRequestId(requestId);
        matchRequestRepository.delete(req);

    }

    private String timeFormat (int h1, int h2) {  return String.format("%02d:00~%02d:00", h1, h2); }

    private int overlappedHours(int s1, int e1, int s2, int e2) {
        int start = Math.max(s1, s2);
        int end = Math.min(e1, e2);
        return Math.max(0, end - start);
    }

    // cosine 유사도
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
        var games = gameRepository.findRecentGamesByUserId(userId, PageRequest.of(0, 5));
        long wins = games.stream()
                .filter(g -> g.getWinner() != null && g.getWinner().getUserId().equals(userId))
                .count();
        return games.isEmpty() ? 0.0 : (wins * 100.0 / games.size());
    }
}