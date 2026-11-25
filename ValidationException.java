package brgy.service;

/** Thrown for any rule violation. getMessage() is user-readable and safe to show in a GUI dialog. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
