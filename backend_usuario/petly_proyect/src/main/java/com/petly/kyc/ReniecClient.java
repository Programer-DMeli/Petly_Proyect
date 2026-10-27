package com.petly.kyc;

import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/** Cliente del proveedor de consulta DNI configurado por entorno. */
@Component
public class ReniecClient {

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String token;
    private final String provider;

    public ReniecClient(
            @Value("${petly.reniec-api-url:}") String endpoint,
            @Value("${petly.reniec-api-token:}") String token,
            @Value("${petly.reniec-provider-name:ApiPeruDev}") String provider,
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
        this.provider = provider == null || provider.isBlank() ? "ApiPeruDev" : provider.trim();
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
            ResponseEntity<ApiPeruDniResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("dni", dni), headers),
                    ApiPeruDniResponse.class);
            ApiPeruDniResponse result = response.getBody();
            if (result == null || !Boolean.TRUE.equals(result.success())
                    || !StringUtils.hasText(result.code())
                    || result.data() == null || !StringUtils.hasText(result.data().numero())) {
                throw new ReniecRespuestaInvalidaException();
            }
            ApiPeruDniResponse.Datos data = result.data();
            boolean numeroCoincide = dni.equals(data.numero());
            boolean nombresCoinciden = normalizar(nombres).equals(normalizar(data.nombres()));
            String apellidosProveedor = String.join(" ",
                    List.of(data.apellidoPaterno() == null ? "" : data.apellidoPaterno(),
                            data.apellidoMaterno() == null ? "" : data.apellidoMaterno()).stream()
                            .filter(StringUtils::hasText).toList());
            boolean apellidosCoinciden = normalizar(apellidos).equals(normalizar(apellidosProveedor));
            return new ReniecResponse(numeroCoincide && nombresCoinciden && apellidosCoinciden,
                    "ApiPeruDev:" + result.code());
        } catch (HttpClientErrorException.NotFound ex) {
            return new ReniecResponse(false, "ApiPeruDev:document_not_found");
        } catch (RestClientException ex) {
            throw new ReniecNoDisponibleException();
        }
    }

    private static String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) return "";
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private record ApiPeruDniResponse(Boolean success, String code, Datos data, String message) {
        private record Datos(
                String numero,
                String nombres,
                @JsonProperty("apellido_paterno") String apellidoPaterno,
                @JsonProperty("apellido_materno") String apellidoMaterno) {}
    }
}
