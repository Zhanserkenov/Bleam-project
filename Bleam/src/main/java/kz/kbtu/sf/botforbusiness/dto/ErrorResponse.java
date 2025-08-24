package kz.kbtu.sf.botforbusiness.dto;

public record ErrorResponse(
        long timestamp,
        int status,
        String error,
        String message,
        String path
) {}
