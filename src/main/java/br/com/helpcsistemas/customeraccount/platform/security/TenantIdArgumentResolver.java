package br.com.helpcsistemas.customeraccount.platform.security;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injeta {@link TenantId} em parâmetros de controllers. Assim os módulos de negócio recebem o tenant sem depender
 * de Spring Security (inversão de dependência).
 */
class TenantIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantId.class.equals(parameter.getParameterType());
    }

    @Override
    public TenantId resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return AuthenticatedTenant.current()
                .orElseThrow(() -> new AccessDeniedException("Token sem o claim tenant_id"));
    }
}
