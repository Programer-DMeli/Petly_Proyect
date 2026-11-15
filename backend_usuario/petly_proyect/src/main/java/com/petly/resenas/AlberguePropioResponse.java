package com.petly.resenas;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AlberguePropioResponse(@JsonProperty("albergue_id") Long albergueId) {
}
