package com.petly.kyc;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.petly.common.exception.ApiException;

/** Cliente servidor-servidor de ApiPeruDev para autocompletar datos por DNI. */
@Component
public class ApiPeruDniClient {
    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String token;

    public ApiPeruDniClient(
        @Value("${petly.apiperu-dni-url:https://api.apiperu.dev/dni}") String endpoint,
        @Value("${petly.apiperu-api-token:}") String token,
        @Value("${petly.reniec-timeout-ms:5000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.token = token == null ? "" : token.trim();
    }

    public ApiPeruDniResult consultar(String dni) {
        if (dni == null || !dni.matches("[0-9]{8}")) {
            throw new ApiException(400, "El DNI debe tener exactamente ocho dígitos.");
        }
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(token)) {
            throw new ApiException(503, "La consulta de DNI no está configurada. Revisa APIPERU_API_TOKEN en el archivo local .env.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);

        final ApiPeruEnvelope response;
        try {
            response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(Map.of("dni", dni), headers),
                ApiPeruEnvelope.class
            ).getBody();
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400) throw new ApiException(400, "ApiPeruDev rechazó el DNI enviado.");
            if (status == 404) throw new ApiException(404, "No se encontraron datos para ese DNI.");
            if (status == 429) throw new ApiException(429, "Se alcanzó el límite de consultas del plan ApiPeruDev.");
            if (status == 401 || status == 403) throw new ApiException(503, "ApiPeruDev no autorizó la consulta. Revisa el token y el acceso del plan.");
            throw new ApiException(503, "ApiPeruDev no está disponible en este momento. Intenta nuevamente.");
        } catch (RestClientException ex) {
            throw new ApiException(503, "No se pudo conectar con ApiPeruDev. Intenta nuevamente.");
        }

        if (response == null) throw new ApiException(502, "ApiPeruDev devolvió una respuesta vacía.");
        if (!Boolean.TRUE.equals(response.success()) || response.data() == null) {
            if ("document_not_found".equals(response.code())) throw new ApiException(404, "No se encontraron datos para ese DNI.");
            if ("invalid_input".equals(response.code())) throw new ApiException(400, "ApiPeruDev rechazó el DNI enviado.");
            throw new ApiException(503, "ApiPeruDev no pudo completar la consulta. Intenta nuevamente.");
        }

        ApiPeruData data = response.data();
        String nombres = clean(data.nombres());
        String apellidos = String.join(" ", List.of(clean(data.apellidoPaterno()), clean(data.apellidoMaterno()))).trim();

        if (!StringUtils.hasText(nombres) || !StringUtils.hasText(apellidos)) {
            throw new ApiException(502, "ApiPeruDev no devolvió nombres y apellidos completos para ese DNI.");
        }

        String numero = StringUtils.hasText(data.numero()) ? data.numero() : dni;
        return new ApiPeruDniResult(numero, nombres, apellidos, "ApiPeruDev");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiPeruEnvelope(
        Boolean success,
        String code,
        ApiPeruData data,
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiPeruData(
        String numero,
        String nombres,
        @JsonProperty("apellido_paterno") String apellidoPaterno,
        @JsonProperty("apellido_materno") String apellidoMaterno
    ) {}
}
