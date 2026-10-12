package com.petly.auth;
import com.petly.auth.dto.*;
import com.petly.usuarios.*;
import com.petly.common.exception.ApiException;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
 private final UsuarioRepository usuarios;
 private final PasswordEncoder encoder;
 private final JwtService jwt;
 private final JavaMailSender mail;
 private final JdbcTemplate db;
 private final String mailFrom, resetBase;
 @Value("${petly.web-reset-link-base:http://localhost:5173/recuperacion}") private String webResetBase;
 public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt,
    JavaMailSender mail, JdbcTemplate db, @Value("${spring.mail.from}") String mailFrom,
    @Value("${petly.reset-link-base}") String resetBase) {
  this.usuarios=usuarios;this.encoder=encoder;this.jwt=jwt;this.mail=mail;this.db=db;
  this.mailFrom=mailFrom;this.resetBase=resetBase;
 }
 @Transactional
 public AuthResponse registrar(RegistroRequest request) {
  validarPassword(request.getPassword(),"password");
  String correo=request.getCorreo().trim().toLowerCase(Locale.ROOT);
  if(usuarios.existsByCorreo(correo)) throw new CorreoYaRegistradoException("correo ya registrado");
  Usuario u=new Usuario("petly_"+UUID.randomUUID().toString().replace("-",""),correo,encoder.encode(request.getPassword()),Usuario.Rol.ADOPTANTE);
  usuarios.save(u); return new AuthResponse(u.getId(),u.getCorreo(),u.getRol().name());
 }
 @Transactional(noRollbackFor={CredencialesInvalidasException.class,AuthCuentaBloqueadaException.class})
 public LoginResponse login(LoginRequest request) {
  Usuario u=usuarios.bloquearPorCorreo(request.getCorreo().trim().toLowerCase(Locale.ROOT))
    .orElseThrow(CredencialesInvalidasException::new);
  LocalDateTime now=LocalDateTime.now();
  if(u.getBloqueadoHasta()!=null && u.getBloqueadoHasta().isAfter(now)) throw new AuthCuentaBloqueadaException("bloqueada",Math.max(1,(Duration.between(now,u.getBloqueadoHasta()).toMillis()+999)/1000));
  if(u.getBloqueadoHasta()!=null) {u.setIntentosFallidos(0);u.setBloqueadoHasta(null);}
  if(!encoder.matches(request.getPassword(),u.getPasswordHash())) {
   u.setIntentosFallidos(u.getIntentosFallidos()+1);
   if(u.getIntentosFallidos()>=2) {u.setBloqueadoHasta(now.plusMinutes(1));usuarios.save(u);throw new AuthCuentaBloqueadaException("bloqueada");}
   usuarios.save(u);throw new CredencialesInvalidasException();
  }
  u.setIntentosFallidos(0);u.setBloqueadoHasta(null);usuarios.save(u);
  return new LoginResponse(jwt.generarToken(u),new AuthResponse(u.getId(),u.getCorreo(),u.getRol().name()));
 }
 private static void validarPassword(String password,String campo) {
  if(password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72)
   throw new ApiException(400,"datos inválidos",Map.of(campo,"Usa al menos 8 caracteres y hasta 72 bytes UTF-8."));
 }
 public static String hash(String token) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
 public void solicitarResetPassword(String correo) { solicitarResetPassword(correo, false); }
 public void solicitarResetPassword(String correo, boolean web) {
  var optional=usuarios.findByCorreo(correo.trim().toLowerCase(Locale.ROOT));
  if(optional.isEmpty()) return;
  Usuario u=optional.get(); byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  db.update("INSERT INTO password_resets (token_hash,usuario_id,expires_at) VALUES (?,?,?)",hash(token),u.getId(),java.sql.Timestamp.from(Instant.now().plusSeconds(900)));
  SimpleMailMessage msg=new SimpleMailMessage();msg.setFrom(mailFrom);msg.setTo(u.getCorreo());
  msg.setSubject("Recuperación de contraseña - Petly");
  msg.setText("Abre este enlace en Petly para cambiar tu contraseña (vence en 15 minutos y sirve una sola vez):\n"+(web ? webResetBase : resetBase)+"?token="+token);
  try {mail.send(msg);} catch(org.springframework.mail.MailException e) {
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("Envío de recuperación no disponible; revisar SMTP.");
  }
 }
 @Transactional
 public void confirmarResetPassword(String token,String password) {
  validarPassword(password,"nuevaPassword");
  String hash=hash(token);
  var rows=db.queryForList("SELECT usuario_id FROM password_resets WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",hash);
  if(rows.isEmpty()) throw new ApiException(400,"enlace inválido, vencido o utilizado");
  long id=((Number)rows.get(0).get("usuario_id")).longValue();
  db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,id);
  int changed=db.update("UPDATE password_resets SET used_at=CURRENT_TIMESTAMP WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",hash);
  if(changed!=1) throw new ApiException(400,"enlace inválido, vencido o utilizado");
  db.update("UPDATE usuarios SET password_hash=?,token_version=token_version+1,intentos_fallidos=0,bloqueado_hasta=NULL WHERE id=?",encoder.encode(password),id);
  db.update("UPDATE password_resets SET used_at=CURRENT_TIMESTAMP WHERE usuario_id=? AND used_at IS NULL",id);
 }
}
