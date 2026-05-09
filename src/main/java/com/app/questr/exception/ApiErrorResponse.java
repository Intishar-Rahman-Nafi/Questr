package com.app.questr.exception;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path, Map<String,String> errors) {
    public static ApiErrorResponse of(int s, String e, String m, String p) {
        return new ApiErrorResponse(LocalDateTime.now(),s,e,m,p,null);
    }
    public static ApiErrorResponse of(int s, String e, String m, String p, Map<String,String> er) {
        return new ApiErrorResponse(LocalDateTime.now(),s,e,m,p,er);
    }
}
