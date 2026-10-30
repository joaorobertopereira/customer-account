package br.com.helpcsistemas.customeraccount.shared.web;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.DomainException;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceAlreadyExistsException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em respostas RFC 9457 (Problem Details), sempre com o traceId para correlação. Mensagens nunca
 * ecoam valores enviados (evita vazamento de dados pessoais em respostas e logs).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE_URI = "https://api.helpcsistemas.com.br/problems/";

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail handleInvalidValue(InvalidValueException ex) {
        log.atWarn().addKeyValue("error.code", ex.getCode()).log(ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Valor inválido", ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.atWarn().addKeyValue("error.code", ex.getCode()).log(ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex);
    }

    @ExceptionHandler({ResourceAlreadyExistsException.class, ConcurrencyConflictException.class})
    ProblemDetail handleConflict(DomainException ex) {
        log.atWarn().addKeyValue("error.code", ex.getCode()).log(ex.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito", ex);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        log.atWarn().addKeyValue("error.code", ex.getCode()).log(ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada", ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.atWarn().addKeyValue("error.code", "ACCESS_DENIED").log(ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso negado");
        problem.setTitle("Acesso negado");
        problem.setType(URI.create(PROBLEM_BASE_URI + "access-denied"));
        return withTraceId(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar requisição", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Informe o traceId ao suporte.");
        problem.setTitle("Erro interno");
        problem.setType(URI.create(PROBLEM_BASE_URI + "internal-error"));
        return withTraceId(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        log.atWarn().addKeyValue("errors", errors).log("Requisição inválida");

        var problem = ProblemDetail.forStatusAndDetail(status, "Um ou mais campos são inválidos");
        problem.setTitle("Requisição inválida");
        problem.setType(URI.create(PROBLEM_BASE_URI + "validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(withTraceId(problem));
    }

    private static ProblemDetail problem(HttpStatus status, String title, DomainException ex) {
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE_URI + ex.getCode().toLowerCase().replace('_', '-')));
        problem.setProperty("code", ex.getCode());
        return withTraceId(problem);
    }

    private static ProblemDetail withTraceId(ProblemDetail problem) {
        var traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
