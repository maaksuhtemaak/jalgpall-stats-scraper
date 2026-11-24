package ee.jalgpall.scraper.app.web;

import ee.jalgpall.scraper.app.client.JalgpallUnavailableException;
import ee.jalgpall.scraper.app.parser.UnexpectedResponseException;
import ee.jalgpall.scraper.app.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler
    public ProblemDetail handleUnavailable(JalgpallUnavailableException e) {
        log.warn("jalgpall.ee request failed", e);
        return problem(HttpStatus.BAD_GATEWAY, "jalgpall.ee is unavailable", e.getMessage());
    }

    @ExceptionHandler
    public ProblemDetail handleUnexpectedResponse(UnexpectedResponseException e) {
        log.error("Unexpected response from jalgpall.ee", e);
        return problem(HttpStatus.BAD_GATEWAY, "Unexpected response from jalgpall.ee", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
