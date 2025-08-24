package kz.kbtu.sf.botforbusiness.exception;

public class PlatformOperationException extends RuntimeException {
    public PlatformOperationException(String message) {
        super(message);
    }
    public PlatformOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}