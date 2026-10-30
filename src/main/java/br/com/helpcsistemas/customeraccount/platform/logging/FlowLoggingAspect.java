package br.com.helpcsistemas.customeraccount.platform.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loga entrada/saída/duração de todo método público de serviços de aplicação e adapters, usando o logger da
 * própria classe. Junto com traceId/spanId/tenant.id no MDC, permite reconstruir o fluxo completo de uma requisição
 * ou de um evento processado por subscription. Argumentos e retornos NÃO são logados (LGPD/OWASP).
 */
@Aspect
@Component
class FlowLoggingAspect {

    @Around("(within(br.com.helpcsistemas.customeraccount..application..*)"
            + " || within(br.com.helpcsistemas.customeraccount..adapter..*))"
            + " && !@within(org.springframework.boot.context.properties.ConfigurationProperties)")
    Object logFlow(ProceedingJoinPoint pjp) throws Throwable {
        var type = pjp.getSignature().getDeclaringType();
        var log = LoggerFactory.getLogger(type);
        if (!log.isDebugEnabled()) {
            return pjp.proceed();
        }

        var flow = type.getSimpleName() + "." + pjp.getSignature().getName();
        log.atDebug()
                .addKeyValue("flow.step", flow)
                .addKeyValue("flow.phase", "start")
                .log("-> {}", flow);

        long start = System.nanoTime();
        try {
            var result = pjp.proceed();
            log.atDebug()
                    .addKeyValue("flow.step", flow)
                    .addKeyValue("flow.phase", "end")
                    .addKeyValue("flow.durationMs", elapsedMs(start))
                    .log("<- {}", flow);
            return result;
        } catch (Throwable ex) {
            log.atDebug()
                    .addKeyValue("flow.step", flow)
                    .addKeyValue("flow.phase", "error")
                    .addKeyValue("flow.durationMs", elapsedMs(start))
                    .addKeyValue("flow.error", ex.getClass().getSimpleName())
                    .log("<! {}", flow);
            throw ex;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
