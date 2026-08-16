package com.gen.rally.service;

import com.gen.rally.dto.*;
import com.gen.rally.entity.*;
import com.gen.rally.enums.MessageType;
import com.gen.rally.enums.State;
import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import com.gen.rally.repository.ChatMessageRepository;
import com.gen.rally.repository.ChatRoomRepository;
import com.gen.rally.repository.GameRepository;
import com.gen.rally.repository.UserRepository;
import com.nimbusds.jose.shaded.gson.Gson;
import com.nimbusds.jose.shaded.gson.annotations.SerializedName;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.gen.rally.enums.MessageType.TEXT;

@Transactional
@Service
@RequiredArgsConstructor
public class ChatService {
    private final SimpMessagingTemplate template;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final Gson gson = new Gson();

    private static class MessagePayload {
        @SerializedName("type")
        private String type;
        @SerializedName("data")
        private Object data;

        public String getType() { return type; }
    }

    // 채팅방 목록 조회
    public List<ChatRoomListDto> findRoomsByUser(Long userId) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByUser1_IdOrUser2_Id(userId, userId);
        return rooms.stream()
                .map(room -> convertToDto(room, userId)) // DTO 변환 메서드 사용
                .collect(Collectors.toList());
    }

    private ChatRoomListDto convertToDto(ChatRoom room, Long myId) {
        User user1 = room.getUser1();
        User user2 = room.getUser2();

        User opponent = null;
        if (user1 != null && user1.getId() != null && user1.getId().equals(myId)) {
            opponent = user2;
        } else if (user2 != null && user2.getId() != null && user2.getId().equals(myId)) {
            opponent = user1;
        }
        ChatMessage lastMessage = getLastMessage(room.getId());

        return ChatRoomListDto.builder()
                .roomId(room.getId())
                .opponentId(opponent != null ? opponent.getId() : null)
                .opponentName(opponent != null ? opponent.getName() : "탈퇴 회원") // 안전한 기본값
                .gameStyle(room.getGame() != null ? room.getGame().getGameType().toString() : "미정")
                .opponentProfileImageUrl(opponent != null ? opponent.getImageUrl() : null)
                .lastMessage(lastMessage != null ? lastMessage.getContent() : " ")
                .lastMessageTime(lastMessage != null ? lastMessage.getCreatedAt() : room.getCreatedAt())
                .unreadCount(getUnreadCount(room.getId(), myId, room))
                .build();
    }

    private ChatMessage getLastMessage(Long roomId) {
        return chatMessageRepository.findFirstByChatRoom_IdOrderByCreatedAtDesc(roomId)
                .orElse(null);
    }

    private int getUnreadCount(Long roomId, Long myId, ChatRoom room) {
        LocalDateTime lastReadAt = null;

        if (room.getUser1().getId().equals(myId)) {
            lastReadAt = room.getUser1LastReadAt();
        } else {
            lastReadAt = room.getUser2LastReadAt();
        }
        if (lastReadAt == null) {
            lastReadAt = room.getCreatedAt();
        }
        return chatMessageRepository.countUnreadMessages(roomId, lastReadAt, myId);
    }

    // 채팅 읽음 처리
    public void markMessagesAsRead(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();

        if (room.getUser1().getId().equals(userId)) {
            room.setUser1LastReadAt(now);
        } else if (room.getUser2().getId().equals(userId)) {
            room.setUser2LastReadAt(now);
        }
    }

    // 채팅방 입장, 정보 가져오기
    public ChatRoomDto enter(Long roomId, Long userId){
        ChatRoom room = chatRoomRepository.findById(roomId)
                        .orElseThrow(()-> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

        Long user1Id = room.getUser1().getId();
        Long user2Id = room.getUser2().getId();

        if (!user1Id.equals(userId) && !user2Id.equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        User me = user1Id.equals(userId) ? room.getUser1() : room.getUser2();
        User opponent = user1Id.equals(userId) ? room.getUser2() : room.getUser1();

        Game game = room.getGame();
        if (game == null) {
            throw new CustomException(ErrorCode.GAME_NOT_FOUND);
        }

        MatchRequest req1 = game.getRequestId1();
        MatchRequest req2 = game.getRequestId2();

        MatchRequest myRequest = user1Id.equals(userId) ? req1 : req2;
        MatchRequest opponentRequest = user1Id.equals(userId) ? req2 : req1;

        MatchRequestInfoDto myReqInfo = createMatchRequestInfo(myRequest);
        MatchRequestInfoDto opponentReqInfo = createMatchRequestInfo(opponentRequest);

        return new ChatRoomDto(
                opponent.getId(),
                opponent.getName(),
                opponent.getImageUrl(),
                myReqInfo,
                opponentReqInfo
        );
    }

    // 매칭 요청 정보 dto 생성 메서드
    private MatchRequestInfoDto createMatchRequestInfo(MatchRequest request) {
        if (request == null) {
            return null;
        }
        MatchRequestInfoDto dto = MatchRequestInfoDto.from(request);
        return dto;
    }

    // 이전 메시지 조회
    public List<ChatMessageDto> loadMessages(Long roomId, Long userId){
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(()-> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

        if(!room.getUser1().getId().equals(userId) && !room.getUser2().getId().equals(userId)){
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<ChatMessage> messages = chatMessageRepository.findAllByChatRoomId(roomId);
        List<ChatMessageDto> dtos = messages.stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());

        return dtos;
    }

    // 게임 정보 확정
    public String confirm(MatchConfirmDto dto){
        ChatRoom room = chatRoomRepository.findById(dto.getRoomId())
                .orElseThrow(()-> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));
        Game game = gameRepository.findById(room.getGame().getGameId())
                .orElseThrow(()-> new CustomException(ErrorCode.GAME_NOT_FOUND));

        game.setTime(dto.getTime());
        game.setPlace(dto.getPlace());
        game.setState(State.경기확정);
        gameRepository.save(game);

        return "경기가 확정되었습니다.";
    }


    // 메시지 전송
    public ChatMessage send(Long roomId, Long senderId, String content){
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_MESSAGE);
        }
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(()-> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        MessageType messageType = TEXT; // 기본값
        try {
            MessagePayload payload = gson.fromJson(content, MessagePayload.class);
            if (payload != null && "MATCH_CARD".equals(payload.getType())) {
                messageType = MessageType.MATCH_CARD;
            }
        } catch (Exception e) {

        }

        ChatMessage message = ChatMessage.create(room, sender, content);
        message.setMessageType(messageType);
        chatMessageRepository.save(message);

        ChatMessageDto payload = ChatMessageDto.builder()
                .id(message.getId())
                .roomId(room.getId())
                .senderId(sender.getId())
                .type(messageType)
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
        // 브로커로 전송
        template.convertAndSend("/sub/dm/" + roomId, payload);
        return message;
    }

}
