package com.petly.common.exception;
import java.util.Map;
public class ApiException extends RuntimeException {
 public final int status; public final Map<String,String> fields;
 public ApiException(int status,String message) {this(status,message,Map.of());}
 public ApiException(int status,String message,Map<String,String> fields) {super(message);this.status=status;this.fields=fields;}
}
