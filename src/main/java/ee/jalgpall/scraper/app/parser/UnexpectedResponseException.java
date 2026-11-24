package ee.jalgpall.scraper.app.parser;

/**
 * jalgpall.ee answered, but not in the format the parser understands (the site layout probably changed).
 */
public class UnexpectedResponseException extends RuntimeException {

    public UnexpectedResponseException(String message) {
        super(message);
    }

    public UnexpectedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
