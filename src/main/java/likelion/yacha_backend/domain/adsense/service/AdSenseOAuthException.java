
package likelion.yacha_backend.domain.adsense.service;

import org.springframework.http.HttpStatus;

public class AdSenseOAuthException extends RuntimeException {
    private final HttpStatus status;

    public AdSenseOAuthException(HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
