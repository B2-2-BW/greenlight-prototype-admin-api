package com.winten.greenlight.prototype.admin.support.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", LogLevel.WARN),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "User not found.", LogLevel.INFO),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E404, "Event not found.", LogLevel.INFO),
    EVENT_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCode.E409, "Duplicated event name.", LogLevel.INFO),
    REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred while accessing data." , LogLevel.WARN ),
    INVALID_DATA(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "Data is not valid." , LogLevel.WARN ),;

    private final HttpStatus status; //HTTP 응답 코드
    private final ErrorCode code; // 고유 오류 코드
    private final String message; // 노출 메시지
    private final LogLevel logLevel;

}