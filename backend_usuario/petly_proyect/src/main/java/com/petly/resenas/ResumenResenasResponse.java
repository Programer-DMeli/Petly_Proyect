package com.petly.resenas;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResumenResenasResponse(
        @JsonProperty("albergue_id") Long albergueId,
        BigDecimal promedio,
        long total,
        List<ResenaItem> resenas) {
}
