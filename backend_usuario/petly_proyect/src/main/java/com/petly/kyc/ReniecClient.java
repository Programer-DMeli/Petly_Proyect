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
    private final ApiPeruDniClient apiPeru;

    public ReniecClient(
            @Value("${petly.reniec-api-url:}") String endpoint,
            @Value("${petly.reniec-api-token:}") String token,
            @Value("${petly.reniec-provider-name:RENIEC}") String provider,
            @Value("${petly.reniec-timeout-ms:5000}") int timeoutMs,
            ApiPeruDniClient apiPeru) {
        this.apiPeru = apiPeru;
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

    static String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().replaceAll("\\s+", " ")
                .toUpperCase(java.util.Locale.ROOT);
    }

    public String getProvider() {
        return endpoint.isBlank() ? "ApiPeruDev" : provider;
    }

    public ReniecResponse consultar(String dni, String nombres, String apellidos) {
        if (dni == null || !dni.matches("[0-9]{8}")
                || !StringUtils.hasText(nombres) || !StringUtils.hasText(apellidos)) {
            throw new IllegalArgumentException("El DNI debe tener ocho dígitos y los nombres son obligatorios.");
        }

        if (!StringUtils.hasText(endpoint)) {
            final ApiPeruDniResult person;
            try {
                person = apiPeru.consultar(dni);
            } catch (com.petly.common.exception.ApiException ex) {
                throw new ReniecNoDisponibleException();
            }
            boolean matches = normalize(nombres).equals(normalize(person.nombres()))
                    && normalize(apellidos).equals(normalize(person.apellidos()));
            // Referencia interna de esta comprobación; no se atribuye al proveedor.
            return new ReniecResponse(matches, "petly-apiperu-" + java.util.UUID.randomUUID());
        }
        if (!StringUtils.hasText(token)) throw new ReniecNoConfiguradoException();

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
