package com.example.shell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication
public class ShellApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShellApplication.class, args);
    }

}

@Component
class Granter {

    private final ClientRegistration registration;
    private final RestClient http;

    Granter(InMemoryClientRegistrationRepository registrations,
            RestClient.Builder http) {
        this.registration = registrations.findByRegistrationId("spring");
        this.http = http
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(this.registration.getClientId(),
                            this.registration.getClientSecret());
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                })
                .build();
    }

    private LinkedMultiValueMap<String, String> from(Map<String, String> map) {
        var l = new LinkedMultiValueMap<String, String>();
        map.forEach(l::add);
        return l;
    }

    Grant grant() {
        var metadata = from(Map.of("scope", String.join(" ", this.registration.getScopes()),
                "client_id", this.registration.getClientId()));
        var result = this.http
                .post()
                .uri(this.registration.getProviderDetails().getConfigurationMetadata()
                        .get("device_authorization_endpoint") + "")
                .body(metadata)
                .retrieve()
                .body(JsonNode.class);
        var deviceCode = result.get("device_code").asString();
        var verificationUri = result.get("verification_uri_complete").asString();
        return new Grant(verificationUri, CompletableFuture.supplyAsync(() -> {
            while (true) {
                try {
                    Thread.sleep(5_000);
                    IO.println("waiting...");
                    return this.getToken(deviceCode);
                } //
                catch (Throwable throwable) {
                    IO.println("error: " + throwable.getMessage());
                    //
                }
            }
        }));
    }

    private String getToken(String deviceCode) {
        var data = from(Map.of("device_code", deviceCode,
                "client_id", this.registration.getClientId(),
                "grant_type", AuthorizationGrantType.DEVICE_CODE.getValue()
        ));
        var result = this.http
                .post()
                .uri(this.registration.getProviderDetails().getTokenUri() + "")
                .body(data)
                .retrieve()
                .body(JsonNode.class)
                .get("access_token")
                .asString();
        return result;

    }
}

record Grant(String verificationUri, CompletableFuture<String> accessToken) {
}

@Component
class ShellComponent {

    private final RestClient http;
    private final Granter granter;
    private final AtomicReference<String> code = new AtomicReference<>();

    ShellComponent(RestClient.Builder http, Granter granter) {
        this.http = http.build();
        this.granter = granter;
    }

    @Command(description = "returns the message from a secured HTTP endpoint")
    String message() throws Exception {
        if (this.code.get() == null) {
            var result = this.granter.grant();
            IO.println("please go to " + result.verificationUri());
            var code = result.accessToken().get();
            this.code.set(code);
        }
        return this.http
                .get()
                .uri("http://localhost:8085/rest")
                .headers(h -> h.setBearerAuth(this.code.get()))
                .retrieve()
                .body(JsonNode.class)
                .get("name")
                .asString();
    }
}
