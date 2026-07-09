package com.github.senkex.headrender;

/**
 * Unchecked exception raised when a head render operation fails.
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public class HeadRenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public HeadRenderException(final String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public HeadRenderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
