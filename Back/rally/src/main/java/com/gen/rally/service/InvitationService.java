package com.gen.rally.service;

import com.gen.rally.dto.*;
import com.gen.rally.entity.*;
import com.gen.rally.enums.State;
import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import com.gen.rally.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class InvitationService {
    private final MatchInvitationRepository invitationRepo;
    private final GameRepository gameRepo;
    private final ChatRoomRepository chatRoomRepo;
    private final NotificationService notiService;
    private final UserRepository userRepo;
    private final MatchRequestRepository requestRepo;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREA);

    public ResponseEntity<MatchInviteResponse> invite(String userId, MatchInviteRequest req
    ) {
        User sender = userRepo.findByUserId(userId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        User receiver = userRepo.findByUserId(req.getReceiverId()).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        MatchRequest senderReq = requestRepo.findById(req.getSenderRequestId())
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        MatchRequest receiverReq = requestRepo.findById(req.getReceiverRequestId())
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND));

        // 소유자 검증
        if (!senderReq.getUser().getUserId().equals(sender.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN); // 내 요청이 아님
        }
        if (!receiverReq.getUser().getUserId().equals(receiver.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN); // 상대의 요청과 receiverId 불일치
        }
        // 상태 검증 (대기 상태만 요청 가능)
        if (senderReq.getState() != State.대기 || receiverReq.getState() != State.대기) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        senderReq.changeState(State.요청중);
        MatchInvitation inv = new MatchInvitation();
        inv.setSender(sender);
        inv.setReceiver(receiver);
        inv.setSenderRequest(senderReq);
        inv.setReceiverRequest(receiverReq);
        inv.setState(State.요청중);

        invitationRepo.save(inv);
        notiService.sendInvitationNotification(receiver.getUserId(), inv.getInvitationId());

        return ResponseEntity.ok(new MatchInviteResponse(inv.getInvitationId(), inv.getState().name()));
    }


    public List<InvitationItem> findReceived(String myUserId) {
        return invitationRepo.findReceivedByUserId(myUserId).stream().map(mi -> {
            var myReq = mi.getReceiverRequest();
            var oppReq = mi.getSenderRequest();
            var opponent = mi.getSender();
            return new InvitationItem(
                    mi.getInvitationId(),
                    myReq != null ? myReq.getRequestId() : null,
                    oppReq != null ? oppReq.getRequestId() : null,
                    myReq != null && myReq.getGameDate()!=null ? myReq.getGameDate().format(DATE_FMT) : null,
                    myReq != null && myReq.getGameType()!=null ? myReq.getGameType().name() : null,
                    opponent != null ? opponent.getUserId() : null,
                    opponent != null ? opponent.getName() : null,
                    opponent != null ? opponent.getImageUrl() : null,
                    mi.getState() != null ? mi.getState().name() : null,
                    mi.getRefusal()
            );
        }).collect(toList());
    }

    public List<InvitationItem> findSent(String myUserId) {
        return invitationRepo.findSentByUserId(myUserId).stream().map(mi -> {
            var myReq = mi.getSenderRequest();
            var oppReq = mi.getReceiverRequest();
            var opponent = mi.getReceiver();
            return new InvitationItem(
                    mi.getInvitationId(),
                    myReq != null ? myReq.getRequestId() : null,
                    oppReq != null ? oppReq.getRequestId() : null,
                    myReq != null && myReq.getGameDate()!=null ? myReq.getGameDate().format(DATE_FMT) : null,
                    myReq != null && myReq.getGameType()!=null ? myReq.getGameType().name() : null,
                    opponent != null ? opponent.getUserId() : null,
                    opponent != null ? opponent.getName() : null,
                    opponent != null ? opponent.getImageUrl() : null,
                    mi.getState() != null ? mi.getState().name() : null,
                    mi.getRefusal()
            );
        }).collect(toList());
    }

    @Transactional
    public InvitationAcceptResponse accept(String myUserId, Long invitationId){
        MatchInvitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_INVITATION_NOT_FOUND));

        // 수신자 검증
        if (inv.getReceiver() == null || inv.getReceiver().getUserId() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!inv.getReceiver().getUserId().equals(myUserId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (inv.getState() == State.거절) {
            throw new CustomException(ErrorCode.ALREADY_DENIED);
        }

        MatchRequest senderReq = inv.getSenderRequest();
        MatchRequest receiverReq = inv.getReceiverRequest();
        if (senderReq == null) {
            throw new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND);
        } else if (receiverReq == null) {
            throw new CustomException(ErrorCode.MATCH_INVITATION_NOT_FOUND);
        }

        Game existing = gameRepo.findByRequests(senderReq, receiverReq).orElse(null);

        User me  = inv.getReceiver();
        User opp = inv.getSender();
        Long myId = me.getId();
        String myProfile =   me.getImageUrl();
        String opponentProfile = opp.getImageUrl();
        String opponentName = opp.getName();

        // 이미 수락 & 게임 존재 시: 5개 필드 모두 반환
        if (inv.getState() == State.수락 && existing != null) {
            ChatRoom room = chatRoomRepo.findByGame(existing).orElse(null);
            return new InvitationAcceptResponse(
                    existing.getGameId(),
                    room != null ? room.getId() : null,
                    myId,
                    myProfile,
                    opponentProfile,
                    opponentName
            );
        }

        // 상태 업데이트
        inv.setState(State.수락);
        senderReq.changeState(State.수락);
        receiverReq.changeState(State.수락);

        Game game = existing;
        if (game == null) {
            game = new Game();
            game.setRequestId1(senderReq);
            game.setRequestId2(receiverReq);
            game.setUser1(inv.getSender());   // user1 = 보낸 사람
            game.setUser2(inv.getReceiver()); // user2 = 받은 사람(나)
            game.setDate(senderReq.getGameDate() != null ? senderReq.getGameDate() : receiverReq.getGameDate());
            game.setGameType(senderReq.getGameType() != null ? senderReq.getGameType() : receiverReq.getGameType());
            game.setGameStyle(senderReq.getGameStyle());
            game.setState(State.수락);
            gameRepo.save(game);
        }

        ChatRoom room = chatRoomRepo.findByGame(game).orElse(null);
        if (room == null) {
            room = ChatRoom.create(game);
            chatRoomRepo.save(room);
        }

        notiService.sendMatchAcceptedNotification(me.getName(), opp.getUserId(), game.getGameId());  // 알림+fcm 전송

        return new InvitationAcceptResponse(
                game.getGameId(),
                room.getId(),
                myId,
                myProfile,
                opponentProfile,
                opponentName
        );
    }

    @Transactional
    public void refuse(String userId, Long invitationId, String refusal) {
        // 요청 데이터 확인
        MatchInvitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(()-> new CustomException(ErrorCode.MATCH_INVITATION_NOT_FOUND));

        // 사용자 권한 체크
        if(inv.getReceiver() == null || !inv.getReceiver().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        // 상태
        if (inv.getState() != State.요청중) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        // 거절 사유 업데이트
        inv.setState(State.거절);
        inv.setRefusal(refusal);
        // 요청자의 매칭 신청 상태 요청중 -> 대기로 전환
        MatchRequest req = inv.getSenderRequest();
        if (req == null) {
            throw new CustomException(ErrorCode.MATCH_REQUEST_NOT_FOUND);
        }
        req.changeState(State.대기);
    }
}
