package insper.store.gateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import insper.store.auth.SolveIn;
import insper.store.auth.SolveOut;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter implements GlobalFilter {

    @Autowired
    private RouterValidator routerValidator;

    @Autowired
    private WebClient.Builder webClient;

    @SuppressWarnings("null")
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();
        // System.out.println("AQUIIIII -> " +  (routerValidator.openApiEndpoints));
        // verificar se a rota eh segura
        if (!routerValidator.isSecured.test(request)) {
            return chain.filter(exchange);
        }
        // verificar se o cabecalho de autenticacao esta presente
        if (isAuthMissing(request)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization header.");
        }
        final String[] parts = getAuthHeader(request).split(" ");
        if (parts.length != 2 || !parts[0].equals("Bearer")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization header.");
        }
        final String token = parts[1];
        return webClient
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri("http://auth:8080/auth/solve")
            .bodyValue(SolveIn.builder().token(token).build())
            .retrieve()
            .toEntity(SolveOut.class)
            .flatMap(response -> {
                if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    // atualizar o cabecalho da requisicao com o usuario logado
                    updateRequestWithUser(exchange, response.getBody());
                    return chain.filter(exchange);
                } else {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
                }
            });
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey("Authorization");
    }

    private String getAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty("Authorization").get(0);
    }

    private void updateRequestWithUser(ServerWebExchange exchange, SolveOut user) {
        exchange.getRequest().mutate()
            .header("id-user", user.id())
            .header("name-user", user.name())
            .header("role-user", user.role())
            .build();
    }
    
}
