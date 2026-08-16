package com.gen.rally.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "이미 존재하는 아이디입니다.", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH("PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_STATE("INVALID_STATE", "유효하지 않은 요청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_KAKAO_CODE("INVALID_KAKAO_CODE", "카카오 인가 코드가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_NAVER_CODE("INVALID_NAVER_CODE", "네이버 인가 코드가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_MESSAGE("INVALID_MESSAGE","유효하지 않은 메시지입니다.", HttpStatus.BAD_REQUEST),
    INVALID_INPUT("INVALID_INPUT","유효하지 않은 입력값입니다.", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    INVALID_TOKEN("INVALID_TOKEN", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN("FORBIDDEN", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

    // 404 Not Found
    USER_NOT_FOUND("USER_NOT_FOUND", "해당 유저를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GAME_NOT_FOUND("GAME_NOT_FOUND","해당 게임을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MATCH_REQUEST_NOT_FOUND("MATCH_REQUEST_NOT_FOUND", "해당 매칭 신청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    MATCH_INVITATION_NOT_FOUND("MATCH_INVITATION_NOT_FOUND", "해당 매칭 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CHATROOM_NOT_FOUND("CHATROOM_NOT_FOUND","해당 채팅방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CHATMESSAGE_NOT_FOUND("CHATMESSAGE_NOT_FOUND","메시지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GOAL_NOT_FOUND("GOAL_NOT_FOUND", "해당 목표를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    GAME_SCORE_NOT_FOUND("GAME_SCORE_NOT_FOUND","해당 점수 기록을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    EVALUATION_NOT_FOUND("EVALUATION_NOT_FOUND", "해당 평가를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "해당 알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // 409 Conflict
    CONFLICT("CONFLICT", "중복이 발생했습니다.", HttpStatus.CONFLICT),
    ALREADY_DENIED("ALREADY_DENIED","이미 거절된 요청입니다.", HttpStatus.CONFLICT),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
