package atracio.agent.atracio;

import java.util.Map;

public class AtracioTokenExpiredException extends RuntimeException {
 
    private final int                 httpStatus;
    private final Map<String, Object> body;
 
    public AtracioTokenExpiredException(int httpStatus, Map<String, Object> body) {
        super("Atracio returned HTTP " + httpStatus);
        this.httpStatus = httpStatus;
        this.body       = body;
    }
 
    /** The HTTP status code returned by Atracio (e.g. 401, 404, 500). */
    public int getHttpStatus() {
        return httpStatus;
    }
 
    /**
     * The parsed JSON body from Atracio. May be null if the response had no body
     * or if JSON parsing itself failed.
     */
    public Map<String, Object> getBody() {
        return body;
    }
}
