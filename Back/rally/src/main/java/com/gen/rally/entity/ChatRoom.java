package com.gen.rally.entity;

import com.gen.rally.exception.CustomException;
import com.gen.rally.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @OneToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="game_id", nullable=false)
    private Game game;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id1", nullable = false)
    private User user1;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id2", nullable = false)
    private User user2;

    private LocalDateTime createdAt = LocalDateTime.now();

    // 사용자가 마지막으로 읽은 메시지의 시각
    private LocalDateTime user1LastReadAt;
    private LocalDateTime user2LastReadAt;

    public static ChatRoom create(Game game) {
        ChatRoom room = new ChatRoom();
        room.setGame(game);
        room.setUser1(game.getUser1());
        room.setUser2(game.getUser2());
        return room;
    }

    public boolean isParticipant(Long userId){
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    // 내 정보 가져오기
    public User getParticipant(Long userId) {
        if (user1.getId().equals(userId)) {
            return user1;
        }

        if (user2.getId().equals(userId)) {
            return user2;
        }

        throw new CustomException(ErrorCode.FORBIDDEN);
    }

    // 상대방 정보 가져오기
    public User getOpponent(Long userId) {
        if (user1.getId().equals(userId)) {
            return user2;
        }

        if (user2.getId().equals(userId)) {
            return user1;
        }

        throw new CustomException(ErrorCode.FORBIDDEN);
    }


}
