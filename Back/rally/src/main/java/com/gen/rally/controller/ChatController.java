package com.gen.rally.controller;

import com.gen.rally.dto.*;
import com.gen.rally.entity.ChatMessage;
import com.gen.rally.entity.ChatRoom;
import com.gen.rally.entity.CustomUserDetails;
import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import com.gen.rally.repository.ChatMessageRepository;
import com.gen.rally.repository.ChatRoomRepository;
import com.gen.rally.repository.GameRepository;
import com.gen.rally.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    private final GameRepository gameRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 채팅방 목록 조회
    @GetMapping("/api/rooms")
    @ResponseBody
    public List<ChatRoomListDto> findAllRooms(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = userDetails.getId();
        List<ChatRoomListDto> lists = chatService.findRoomsByUser(userId);
        return lists;
    }

    // 채팅 읽음 표시
    @PostMapping("/api/rooms/{roomId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = userDetails.getId();
        chatService.markMessagesAsRead(roomId, userId);
        return ResponseEntity.ok().build();
    }

    /* 채팅방 개설
    @PostMapping("/api/rooms/{gameId}")
    @ResponseBody
    public ChatRoom createRoom(@PathVariable Long gameId, @RequestParam Long userId ) {
        return chatService.createRoom(gameId, userId);
    } */

    // 채팅방 입장, 사용자 프로필 캐싱용
    @GetMapping("/api/rooms/{roomId}/participants")
    @ResponseBody
    public ChatRoomDto enterChatRoom(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = userDetails.getId();
        return chatService.enter(roomId, userId);
    }

    // 이전 메시지 로드
    @GetMapping("/api/rooms/{roomId}/messages")
    @ResponseBody
    public ResponseEntity<List<ChatMessageDto>> findChatMessages(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = userDetails.getId();
        List<ChatMessageDto> list = chatService.loadMessages(roomId, userId);
        return ResponseEntity.ok(list);
    }

    // 경기 확정 시, 게임 상태 수정
    @PutMapping("/api/rooms/match-confirm")
    @ResponseBody
    public ResponseEntity<String> confirmMatch(@RequestBody MatchConfirmDto dto, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        String res = chatService.confirm(dto, userDetails.getId());
        return ResponseEntity.ok(res);
    }

    // 메시지 발신 및 수신
    @MessageMapping("/dm/{roomId}")
    public void send(@DestinationVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails,
                     @Payload ChatMessageRequest req){
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Long senderId = userDetails.getId();
        chatService.send(roomId, senderId, req.getContent());
    }
}
