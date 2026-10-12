package com.petly.common.exception;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import com.petly.auth.*;
@RestControllerAdvice
public class GlobalExceptionHandler {
 public static Map<String,Object> body(String message, Map<String,String> fields) {
  return Map.of("error",message,"campos",fields);
 }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<?> validation(MethodArgumentNotValidException e) {
  Map<String,String> fields=new LinkedHashMap<>();
  e.getBindingResult().getFieldErrors().forEach(f->fields.put(f.getField(),f.getDefaultMessage()));
  return ResponseEntity.badRequest().body(body("datos inválidos",fields));
 }
 @ExceptionHandler(ApiException.class)
 ResponseEntity<?> api(ApiException e) { return ResponseEntity.status(e.status).body(body(e.getMessage(),e.fields)); }
 @ExceptionHandler(CorreoYaRegistradoException.class)
 ResponseEntity<?> duplicate(CorreoYaRegistradoException e) {return ResponseEntity.status(409).body(body("correo ya registrado",Map.of()));}
 @ExceptionHandler(AuthCuentaBloqueadaException.class)
 ResponseEntity<?> locked(AuthCuentaBloqueadaException e) {
  return ResponseEntity.status(423).header("Retry-After",String.valueOf(e.getRetryAfterSeconds()))
   .body(Map.of("error","Cuenta bloqueada tras dos fallos. Espera para volver a intentar.","campos",Map.of(),
     "codigo","CUENTA_BLOQUEADA","reintentarEnSegundos",e.getRetryAfterSeconds()));
 }
 @ExceptionHandler(CredencialesInvalidasException.class)
 ResponseEntity<?> credentials(CredencialesInvalidasException e) {return ResponseEntity.status(401).body(body(e.getMessage(),Map.of()));}
 @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 ResponseEntity<?> bad(Exception e) {return ResponseEntity.badRequest().body(body("datos inválidos",Map.of()));}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<?> conflict(Exception e) {return ResponseEntity.status(409).body(body("conflicto de datos",Map.of()));}
 @ExceptionHandler(Exception.class)
 ResponseEntity<?> unexpected(Exception e) {
  org.slf4j.LoggerFactory.getLogger(getClass()).error("Error interno de Petly",e);
  return ResponseEntity.internalServerError().body(body("error interno",Map.of()));
 }
}
