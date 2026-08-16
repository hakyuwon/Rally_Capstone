package com.gen.rally.service;

import com.gen.rally.dto.MatchFoundItem;
import com.gen.rally.entity.ChatRoom;
import com.gen.rally.entity.Game;
import com.gen.rally.enums.State;
import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import com.gen.rally.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GameService {
    private final GameRepository gameRepository;
    private final MatchRequestRepository requestRepository;
    private final MatchInvitationRepository invitationRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;


    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREA);


    public List<MatchFoundItem> findFound(String myUserId, Long userId) {
        // game 조회
        List<Game> games = gameRepository.findAllByUser(myUserId);

        // gameId 가져오기
        var gameIds = games.stream()
                .map(Game::getGameId)
                .filter(java.util.Objects::nonNull)
                .toList();

        var roomByGameId = roomRepository.findByGame_GameIdIn(gameIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> r.getGame().getGameId(), java.util.function.Function.identity()
                ));

        return games.stream().map(g -> {
            boolean meIsUser1 = g.getUser1() != null && myUserId.equals(g.getUser1().getUserId());
            var opponent = meIsUser1 ? g.getUser2() : g.getUser1();

            Long roomId = null;
            ChatRoom room = roomByGameId.get(g.getGameId());
            if (room != null) roomId = room.getId();

            return MatchFoundItem.builder()
                    .gameId(g.getGameId())
                    .roomId(roomId)
                    .userId(userId)
                    .opponentId(opponent != null ? opponent.getUserId() : null)
                    .opponentProfile(opponent != null ? opponent.getImageUrl() : null)
                    .opponentName(opponent != null ? opponent.getName() : null)
                    .date(g.getDate() != null ? g.getDate().format(DATE_FMT) : null)
                    .gameType(g.getGameType() != null ? g.getGameType().name() : null)
                    .time(g.getTime())
                    .place(g.getPlace())
                    .state(g.getState() != null ? g.getState().name() : null)
                    .build();
        }).toList();
    }

    @Transactional
    public void cancelGame(String userId, Long gameId) {
        // 게임 존재 여부
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));

        // 소유권 검사
        boolean meIsUser1 = game.getUser1() != null && userId.equals(game.getUser1().getUserId());
        boolean meIsUser2 = game.getUser2() != null && userId.equals(game.getUser2().getUserId());
        if (!meIsUser1 && !meIsUser2) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // requestId
        Long reqId1 = game.getRequestId1() != null ? game.getRequestId1().getRequestId() : null;
        Long reqId2 = game.getRequestId2() != null ? game.getRequestId2().getRequestId() : null;

        // ChatRoom 찾기
        ChatRoom room = roomRepository.findByGame_GameId(gameId)
                .orElseThrow(() ->new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

        // ChatMessage 삭제
        if (room != null) {
            Long roomId = room.getId();
            messageRepository.deleteByRoomId(roomId);
        }

        // ChatRoom 삭제
        roomRepository.deleteByGame_GameId(gameId);

        // Game 삭제
        gameRepository.delete(game);

        // MatchInvitaiton 삭제
        if (reqId1 != null) {
            invitationRepository.deleteAllByRequestId(reqId1);
            requestRepository.findByRequestId(reqId1).ifPresent(r -> r.changeState(State.대기));
        }
        if (reqId2 != null && !reqId2.equals(reqId1)) {
            invitationRepository.deleteAllByRequestId(reqId2);
            requestRepository.findByRequestId(reqId2).ifPresent(r -> r.changeState(State.대기));
        }
    }
}
