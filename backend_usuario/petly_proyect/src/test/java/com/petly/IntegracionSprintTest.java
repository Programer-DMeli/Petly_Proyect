package com.petly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import com.petly.auth.*;
import com.petly.usuarios.*;
import com.petly.perfiles.PerfilService;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class IntegracionSprintTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired UsuarioRepository users;
 @Autowired PasswordEncoder encoder; @Autowired JwtService jwt; @Autowired PerfilService perfil;
 @MockitoBean JavaMailSender mail;
 ObjectMapper json=new ObjectMapper();
 String token(String role) {
  Usuario u=users.save(new Usuario("test_"+UUID.randomUUID(),UUID.randomUUID()+"@example.test",encoder.encode("password123"),Usuario.Rol.valueOf(role)));
  return jwt.generarToken(u);
 }
 long uid(String token) {return Long.parseLong(jwt.validarToken(token).getSubject());}
 @BeforeEach void fixtures() {
  db.update("DELETE FROM autorizaciones_consulta");db.update("DELETE FROM mascotas_mascota");db.update("DELETE FROM albergues_albergue");db.update("DELETE FROM acceso_user");
  db.update("INSERT INTO acceso_user VALUES (100,100)");
  db.update("INSERT INTO albergues_albergue VALUES (100,'Refugio ejemplo','Lima',100,TRUE)");
  for(int i=1;i<=4;i++) db.update("INSERT INTO mascotas_mascota VALUES (?,?,'PERRO','MEDIANO',2,0,'POR_EVALUAR','MEDIA','Descripción',100,'DISPONIBLE','mascotas/luna.png')",i,"Luna "+i);
 }
 @Test void partialAndFinalPersistAndFalseIsNotNull() throws Exception {
  String t=token("ADOPTANTE");
  String header=new String(java.util.Base64.getUrlDecoder().decode(t.split("\\.")[0]));
  assertTrue(header.contains("HS256"));
  mvc.perform(patch("/api/perfil").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"tieneNinos\":false}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.tieneNinos").value(false)).andExpect(jsonPath("$.tieneOtrasMascotas").isEmpty());
  mvc.perform(patch("/api/perfil").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"finalizado\":true}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.vivienda").exists());
  mvc.perform(patch("/api/perfil").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"vivienda\":\"Departamento\",\"horasFuera\":8.5,\"presupuesto\":150.25,\"tieneOtrasMascotas\":false,\"finalizado\":true}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.finalizado").value(true));
  assertEquals(false,perfil.get(uid(t)).get("tieneNinos"));
  mvc.perform(get("/api/perfil").header("Authorization","Bearer "+token("ADOPTANTE")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.vivienda").isEmpty());
  mvc.perform(patch("/api/perfil").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":1}"))
   .andExpect(status().isBadRequest());
 }
 @Test void validationAndRoleDenied() throws Exception {
  String t=token("ADOPTANTE");
  for(String body:List.of("{\"horasFuera\":24.01}","{\"presupuesto\":-1}","{\"tieneNinos\":\"false\"}","{\"presupuesto\":1.234}"))
   mvc.perform(patch("/api/perfil").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/perfil").header("Authorization","Bearer "+token("ALBERGUE"))).andExpect(status().isForbidden());
 }
 @Test void catalogRealCombinationPaginationAndStatus() throws Exception {
  String t=token("ADOPTANTE");
  mvc.perform(get("/api/catalogo").param("especie","Perro").param("tamano","Mediano").param("temperamento","Por evaluar").param("edadMinimaMeses","24").param("edadMaximaMeses","24").param("page","2").param("pageSize","2").header("Authorization","Bearer "+t))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.items[0].id").value(3));
  db.update("UPDATE mascotas_mascota SET estado='ADOPTADO' WHERE id=1");
  mvc.perform(get("/api/catalogo/1").header("Authorization","Bearer "+t)).andExpect(jsonPath("$.estado").value("Adoptado")).andExpect(jsonPath("$.edadMeses").value(24));
  mvc.perform(get("/api/catalogo").param("edadMinimaMeses","25").param("edadMaximaMeses","24").header("Authorization","Bearer "+t)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/catalogo").param("search","%'").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
 }
 @Test void consentSpecificAndRevoked() throws Exception {
  String adopt=token("ADOPTANTE"), shelter=token("ALBERGUE"), other=token("ALBERGUE");
  db.update("UPDATE acceso_user SET spring_id=? WHERE id=100",uid(shelter));
  String path="/api/albergue/perfiles/"+uid(adopt);
  mvc.perform(get(path).header("Authorization","Bearer "+shelter)).andExpect(status().isForbidden());
  mvc.perform(post("/api/autorizaciones").header("Authorization","Bearer "+adopt).contentType(MediaType.APPLICATION_JSON).content("{\"albergueId\":100}")).andExpect(status().isCreated());
  mvc.perform(get(path).header("Authorization","Bearer "+shelter)).andExpect(status().isOk());
  mvc.perform(get("/api/albergue/perfiles").header("Authorization","Bearer "+shelter))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].adoptanteId").value(uid(adopt)));
  mvc.perform(get("/api/albergue/perfiles").header("Authorization","Bearer "+other))
   .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  mvc.perform(get(path).header("Authorization","Bearer "+other)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/autorizaciones/100").header("Authorization","Bearer "+adopt)).andExpect(status().isOk());
  mvc.perform(get(path).header("Authorization","Bearer "+shelter)).andExpect(status().isForbidden());
  mvc.perform(get("/api/albergue/perfiles").header("Authorization","Bearer "+shelter)).andExpect(jsonPath("$").isEmpty());
 }
 @Test void resetSingleUseExpiryAndOldSessionRejected() throws Exception {
  String t=token("ADOPTANTE");Usuario u=users.findById(uid(t)).orElseThrow();
  mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",u.getCorreo())))).andExpect(status().isOk());
  var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(captor.capture());
  String reset=captor.getValue().getText().split("token=")[1].split("\n")[0];
  String body=json.writeValueAsString(Map.of("token",reset,"nuevaPassword","newpassword123","nuevaPasswordConfirm","newpassword123"));
  mvc.perform(post("/api/auth/password/reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(post("/api/auth/password/reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
  assertTrue(encoder.matches("newpassword123",users.findById(u.getId()).orElseThrow().getPasswordHash()));
  db.update("INSERT INTO tokens_recuperacion (usuario_id,token_hash,expira_en,usado_en,created_at) VALUES (?,?,?,?,?)",u.getId(),AuthService.hash("expired"),java.sql.Timestamp.from(Instant.now().minusSeconds(1)),null,java.sql.Timestamp.from(Instant.now()));
  mvc.perform(post("/api/auth/password/reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body.replace(reset,"expired"))).andExpect(status().isBadRequest());
 }
 @Test void unicodePasswordBeyondBcryptLimitIsRejected() throws Exception {
  mvc.perform(post("/api/auth/registro").with(r->{r.setRemoteAddr("10.9.1.1");return r;})
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","unicode","correo","unicode@example.test","password","é".repeat(37)))))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.password").exists());
 }
 @Test void recoveryResponseDoesNotRevealAccountPresence() throws Exception {
  String t=token("ADOPTANTE");String correo=users.findById(uid(t)).orElseThrow().getCorreo();
  String real=mvc.perform(post("/api/auth/password/reset").with(r->{r.setRemoteAddr("10.9.1.2");return r;})
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",correo))))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String absent=mvc.perform(post("/api/auth/password/reset").with(r->{r.setRemoteAddr("10.9.1.3");return r;})
   .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"absent@example.test\"}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertEquals(real,absent);
 }

 @Test void recoveryWebLinkAndInvalidChannel() throws Exception {
  String t=token("ADOPTANTE");String correo=users.findById(uid(t)).orElseThrow().getCorreo();
  mvc.perform(post("/api/auth/password/reset").param("canal","web").with(r->{r.setRemoteAddr("10.9.1.4");return r;})
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",correo)))).andExpect(status().isOk());
  var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(captor.capture());
  assertTrue(captor.getValue().getText().contains("http://localhost:5173/recuperacion?token="));
  mvc.perform(post("/api/auth/password/reset").param("canal","other").with(r->{r.setRemoteAddr("10.9.1.5");return r;})
   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo",correo)))).andExpect(status().isBadRequest());
 }
 @Test void adminShelterAccountsAndRoleDenied() throws Exception {
  String shelter=token("ALBERGUE"), admin=token("ADMIN"), adopt=token("ADOPTANTE");
  mvc.perform(get("/api/admin/cuentas-albergue/"+uid(shelter)).header("Authorization","Bearer "+admin))
   .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(uid(shelter))).andExpect(jsonPath("$.rol").value("ALBERGUE"))
   .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.tokenVersion").doesNotExist());
  mvc.perform(get("/api/admin/cuentas-albergue").header("Authorization","Bearer "+shelter)).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/cuentas-albergue/"+uid(adopt)).header("Authorization","Bearer "+admin)).andExpect(status().isNotFound());
 }
 @Test void loginLockPersistsAndReportsRemainingAcrossRequests() throws Exception {
  String initial=token("ADOPTANTE");Usuario u=users.findById(uid(initial)).orElseThrow();
  String bad=json.writeValueAsString(Map.of("correo",u.getCorreo(),"password","x"));
  var ip="10.8.0.1";
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr(ip);return r;}).contentType(MediaType.APPLICATION_JSON).content(bad))
   .andExpect(status().isUnauthorized());
  assertEquals(1,users.findById(u.getId()).orElseThrow().getIntentosFallidos());
  // El segundo fallo deja la cuenta bloqueada 5 min pero responde 401;
  // el bloqueo se reporta como 423 desde el siguiente intento.
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr(ip);return r;}).contentType(MediaType.APPLICATION_JSON).content(bad))
   .andExpect(status().isUnauthorized());
  Usuario locked=users.findById(u.getId()).orElseThrow();assertEquals(2,locked.getIntentosFallidos());assertNotNull(locked.getBloqueadoHasta());
  // Otro cliente no evade el bloqueo: ni con la clave correcta entra.
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr("10.8.0.2");return r;}).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("correo",u.getCorreo(),"password","password123"))))
   .andExpect(status().isLocked()).andExpect(jsonPath("$.status").value(423));
  db.update("UPDATE usuarios SET bloqueado_hasta=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)),u.getId());
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr(ip);return r;}).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("correo",u.getCorreo(),"password","password123"))))
   .andExpect(status().isOk());
  assertEquals(0,users.findById(u.getId()).orElseThrow().getIntentosFallidos());
 }
 @Test void rateLimitHasRetryHeaderAndDoesNotLockUnknownAccount() throws Exception {
  for(int i=0;i<10;i++) mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr("10.8.0.3");return r;})
   .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"missing@example.test\",\"password\":\"x\"}"))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr("10.8.0.3");return r;})
   .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"missing@example.test\",\"password\":\"x\"}"))
   .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
   .andExpect(jsonPath("$.codigo").value("LIMITE_PETICIONES"))
   .andExpect(jsonPath("$.reintentarEnSegundos").value(org.hamcrest.Matchers.lessThanOrEqualTo(60)));
  assertTrue(users.findByCorreo("missing@example.test").isEmpty());
 }
 @Test void malformedEmailDoesNotIncrementRegisteredAccount() throws Exception {
  String t=token("ADOPTANTE");long id=uid(t);
  mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr("10.8.0.4");return r;})
   .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"nombre@gmail\",\"password\":\"x\"}"))
   .andExpect(status().isBadRequest());
  assertEquals(0,users.findById(id).orElseThrow().getIntentosFallidos());
 }
}
