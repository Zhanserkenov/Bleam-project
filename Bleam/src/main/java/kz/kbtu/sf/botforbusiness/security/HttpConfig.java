package kz.kbtu.sf.botforbusiness.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HttpConfig {

    @Bean
    public WebClient webClient(JwtUtil jwtUtil) {
        ExchangeFilterFunction authFilter = (request, next) -> {
            String token = jwtUtil.generateServiceToken("spring-service");
            ClientRequest newReq = ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            return next.exchange(newReq);
        };

        return WebClient.builder()
                .filter(authFilter)
                .build();
    }
}
