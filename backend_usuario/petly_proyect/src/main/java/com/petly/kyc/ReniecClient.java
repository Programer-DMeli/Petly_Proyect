package com.petly.kyc;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Cliente del endpoint autorizado de validación RENIEC configurado por entorno. */
@Component
public class ReniecClient {

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String token;
    private final String provider;

    public ReniecClient(
            @Value("${petly.reniec-api-url:}") String endpoint,
            @Value("${petly.reniec-api-token:}") String token,
            @Value("${petly.reniec-provider-name:RENIEC}") String provider,
            @Value("${petly.reniec-timeout-ms:5000}") int timeoutMs) {
        if (timeoutMs < 100 || timeoutMs > 60000) {
            throw new IllegalArgumentException("RENIEC_API_TIMEOUT_MS debe estar entre 100 y 60000.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.token = token == null ? "" : token.trim();
        this.provider = provider == null || provider.isBlank() ? "RENIEC" : provider.trim();
    }

    public String getProvider() {
        return provider;
    }

    public ReniecResponse consultar(String dni, String nombres, String apellidos) {
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(token)) {
            throw new ReniecNoConfiguradoException();
        }
        if (dni == null || !dni.matches("[0-9]{8}")
                || !StringUtils.hasText(nombres) || !StringUtils.hasText(apellidos)) {
            throw new IllegalArgumentException("El DNI debe tener ocho dígitos y los nombres son obligatorios.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);

        try {
            ReniecResponse result = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(new ReniecRequest(dni, nombres.trim(), apellidos.trim()), headers),
                    ReniecResponse.class).getBody();
            if (result == null || result.coincide() == null || !StringUtils.hasText(result.referencia())) {
                throw new ReniecRespuestaInvalidaException();
            }
            return result;
        } catch (RestClientException ex) {
            throw new ReniecNoDisponibleException();
        }
    }
}
