package br.com.helpcsistemas.customeraccount.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.Binary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/** Replays completed POST responses for an authenticated tenant and idempotency key. */
class IdempotencyFilter extends OncePerRequestFilter {

    static final String COLLECTION = "idempotency_keys";
    static final String HEADER = "Idempotency-Key";
    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_RESPONSE_BYTES = 14 * 1024 * 1024;
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String UNREPLAYABLE = "UNREPLAYABLE";

    private final MongoTemplate mongo;
    private final IdempotencyProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    IdempotencyFilter(MongoTemplate mongo, IdempotencyProperties properties, JsonMapper json, Clock clock) {
        this.mongo = mongo;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        if (properties.getTtl() == null
                || properties.getTtl().isZero()
                || properties.getTtl().isNegative()) {
            throw new IllegalArgumentException("app.idempotency.ttl must be positive");
        }
        if (properties.getMaxRequestBytes() < 1) {
            throw new IllegalArgumentException("app.idempotency.max-request-bytes must be positive");
        }
        mongo.indexOps(COLLECTION)
                .createIndex(new Index()
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO)
                        .named("ttl_idempotency_expires_at"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var key = request.getHeader(HEADER);
        if (key == null
                || !request.getMethod().equals("POST")
                || !request.getRequestURI().startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        var token = SecurityContextHolder.getContext().getAuthentication();
        if (!(token instanceof JwtAuthenticationToken jwt)) {
            chain.doFilter(request, response);
            return;
        }

        if (!key.matches("[A-Za-z0-9._~-]{1," + MAX_KEY_LENGTH + "}")) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key inválida");
            return;
        }

        var tenant = jwt.getToken().getClaimAsString(AuthenticatedTenant.TENANT_CLAIM);
        if (tenant == null || tenant.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > properties.getMaxRequestBytes()) {
            writeProblem(response, HttpStatus.PAYLOAD_TOO_LARGE, "IDEMPOTENCY_REQUEST_TOO_LARGE", "Corpo muito grande");
            return;
        }
        var requestBody = request.getInputStream().readNBytes(properties.getMaxRequestBytes() + 1);
        if (requestBody.length > properties.getMaxRequestBytes()) {
            writeProblem(response, HttpStatus.PAYLOAD_TOO_LARGE, "IDEMPOTENCY_REQUEST_TOO_LARGE", "Corpo muito grande");
            return;
        }

        var recordId = sha256(tenant + "\n" + key);
        var bodyHash = sha256(requestBody);
        var existing = reserve(recordId, bodyHash);
        if (existing != null) {
            replayOrReject(existing, bodyHash, response);
            return;
        }

        var repeatableRequest = new RepeatableBodyRequest(request, requestBody);
        var cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(repeatableRequest, cachedResponse);
            complete(recordId, cachedResponse);
            cachedResponse.copyBodyToResponse();
        } catch (IOException | ServletException | RuntimeException ex) {
            try {
                release(recordId);
            } catch (RuntimeException releaseFailure) {
                ex.addSuppressed(releaseFailure);
            }
            throw ex;
        }
    }

    private Document reserve(String id, String bodyHash) {
        var now = clock.instant();
        var record = new Document("_id", id)
                .append("state", PROCESSING)
                .append("requestHash", bodyHash)
                .append("createdAt", Date.from(now))
                .append("expiresAt", Date.from(now.plus(properties.getTtl())));
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                mongo.insert(record, COLLECTION);
                return null;
            } catch (DuplicateKeyException ex) {
                var existing = mongo.findById(id, Document.class, COLLECTION);
                if (existing == null) {
                    continue;
                }
                var expiresAt = ((Date) existing.get("expiresAt")).toInstant();
                if (!expiresAt.isAfter(now)) {
                    mongo.remove(Query.query(Criteria.where("_id").is(id)), COLLECTION);
                    continue;
                }
                return existing;
            }
        }
        throw new IllegalStateException("Could not reserve idempotency key");
    }

    private void replayOrReject(Document existing, String requestHash, HttpServletResponse response)
            throws IOException {
        if (!Objects.equals(existing.getString("requestHash"), requestHash)) {
            writeProblem(
                    response,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "A chave já foi usada com outro corpo");
            return;
        }
        var state = existing.getString("state");
        if (COMPLETED.equals(state)) {
            response.setStatus(existing.getInteger("responseStatus"));
            var headers = existing.get("responseHeaders", Document.class);
            if (headers != null) {
                headers.forEach((name, value) -> response.setHeader(name, String.valueOf(value)));
            }
            var body = existing.get("responseBody", Binary.class);
            if (body != null) {
                response.getOutputStream().write(body.getData());
            }
            return;
        }
        var code = UNREPLAYABLE.equals(state) ? "IDEMPOTENCY_RESPONSE_UNAVAILABLE" : "IDEMPOTENCY_REQUEST_IN_PROGRESS";
        var detail = UNREPLAYABLE.equals(state)
                ? "A resposta original não pode ser reproduzida"
                : "Uma requisição com esta chave ainda está sendo processada";
        response.setHeader("Retry-After", "1");
        writeProblem(response, HttpStatus.CONFLICT, code, detail);
    }

    private void complete(String id, ContentCachingResponseWrapper response) {
        var body = response.getContentAsByteArray();
        var headers = new Document();
        copyHeader(response, headers, "Content-Type");
        copyHeader(response, headers, "Location");
        var state = body.length <= MAX_RESPONSE_BYTES ? COMPLETED : UNREPLAYABLE;
        var update = new Update()
                .set("state", state)
                .set("responseStatus", response.getStatus())
                .set("responseHeaders", headers)
                .set("expiresAt", Date.from(clock.instant().plus(properties.getTtl())));
        if (COMPLETED.equals(state)) {
            update.set("responseBody", new Binary(body));
        }
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(id).and("state").is(PROCESSING)), update, COLLECTION);
        if (result.getModifiedCount() != 1) {
            throw new IllegalStateException("Idempotency reservation was not completed");
        }
        if (UNREPLAYABLE.equals(state)) {
            log.atError().addKeyValue("idempotency.state", state).log("Resposta excedeu o limite de replay");
        }
    }

    private void release(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id).and("state").is(PROCESSING)), COLLECTION);
    }

    private static void copyHeader(ContentCachingResponseWrapper response, Document headers, String name) {
        var value = response.getHeader(name);
        if (value != null) {
            headers.put(name, value);
        }
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), problem);
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static final class RepeatableBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private RepeatableBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("Asynchronous request reads are not supported");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            var encoding = getCharacterEncoding();
            var charset = encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding);
            return new java.io.BufferedReader(new java.io.InputStreamReader(new ByteArrayInputStream(body), charset));
        }
    }
}
