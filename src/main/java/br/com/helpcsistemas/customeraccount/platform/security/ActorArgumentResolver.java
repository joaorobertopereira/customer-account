package br.com.helpcsistemas.customeraccount.platform.security;

import br.com.helpcsistemas.customeraccount.shared.domain.Actor;
import java.util.stream.Stream;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injeta o {@link Actor} autenticado (usuário: {@code preferred_username}; client: {@code azp}). */
class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Actor resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        var token = AuthenticatedTenant.currentToken()
                .orElseThrow(() -> new AccessDeniedException("Requisição não autenticada"))
                .getToken();
        return Stream.of("preferred_username", AuthenticatedTenant.CLIENT_CLAIM, "sub")
                .map(token::getClaimAsString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(Actor::new)
                .orElseThrow(() -> new AccessDeniedException("Token sem identificação do autor"));
    }
}
