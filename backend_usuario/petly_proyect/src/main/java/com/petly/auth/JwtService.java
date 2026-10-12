package com.petly.auth;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.petly.usuarios.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
@Service
public class JwtService {
 private final SecretKey key;
 private final UsuarioRepository users;
 public JwtService(@Value("${spring.jwt.secret}") String secret,UsuarioRepository users) {
  key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.users=users;
 }
 public String generarToken(Usuario u) {
  return Jwts.builder().subject(u.getId().toString()).claim("email",u.getCorreo())
   .claim("rol",u.getRol().name()).claim("version",u.getTokenVersion()).claim("type","access")
   .issuer("petly-spring").audience().add("petly-django").add("petly-api").and()
   .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+3600000)).signWith(key,Jwts.SIG.HS256).compact();
 }
 public Claims validarToken(String token) {
  Claims c=Jwts.parser().verifyWith(key).requireIssuer("petly-spring").requireAudience("petly-api")
   .require("type","access").build().parseSignedClaims(token).getPayload();
  Usuario u=users.findById(Long.parseLong(c.getSubject())).orElseThrow(()->new JwtException("cuenta inexistente"));
  Number version=c.get("version",Number.class);
  if(version==null || version.intValue()!=u.getTokenVersion() || !u.getRol().name().equals(c.get("rol"))) throw new JwtException("sesión inválida");
  return c;
 }
}
