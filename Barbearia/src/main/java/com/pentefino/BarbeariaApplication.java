package com.pentefino;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class BarbeariaApplication {
    private static final Logger LOGGER = Logger.getLogger(BarbeariaApplication.class.getName());
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PASSWORD_ITERATIONS = 600_000;
    private static final int SLOT_MINUTES = 30;
    private static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);
    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(?:\\.[^@\\s.]+)+$");
    private static final Pattern PHONE_DIGITS_PATTERN = Pattern.compile("\\D");
    private static final DateTimeFormatter DATABASE_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final Map<String, Integer> SERVICE_CATALOG = Map.ofEntries(
            Map.entry("Combo Completo (Corte + Barba + Sobrancelha) - R$ 55,00 (60 min)", 60),
            Map.entry("Combo (Corte + Barba) - R$ 50,00 (60 min)", 60),
            Map.entry("Corte - R$ 30,00 (40 min)", 40),
            Map.entry("Corte + Hair Design (Desenho) - R$ 35,00 (60 min)", 60),
            Map.entry("Corte + Sobrancelha - R$ 35,00 (50 min)", 50),
            Map.entry("Corte + Pigmentação - R$ 50,00 (90 min)", 90),
            Map.entry("Acabamento - R$ 10,00 (20 min)", 20),
            Map.entry("Barba - R$ 25,00 (40 min)", 40),
            Map.entry("Barba + Barboterapia - R$ 30,00 (30 min)", 30),
            Map.entry("Barba + Sobrancelha - R$ 30,00 (30 min)", 30),
            Map.entry("Pigmentação (Barba ou Cabelo) - R$ 20,00 (30 min)", 30),
            Map.entry("Limpeza de Pele - R$ 25,00 (40 min)", 40),
            Map.entry("Sobrancelha - R$ 10,00 (15 min)", 15),
            Map.entry("Lavagem Capilar - R$ 15,00 (30 min)", 30),
            Map.entry("Corte & Barba + Platinado / Luzes ou Reflexo - R$ 130,00 (180 min)", 180),
            Map.entry("Corte + Platinado / Luzes ou Reflexo - R$ 100,00 (180 min)", 180),
            Map.entry("Luzes / Platinado / Reflexo - R$ 80,00 (120 min)", 120),
            Map.entry("Botox - R$ 50,00 (120 min)", 120),
            Map.entry("Botox / Produto à parte - R$ 25,00 (120 min)", 120),
            Map.entry("Coloração / Pigmentação (Produto à parte) - R$ 20,00 (30 min)", 30),
            Map.entry("Alisante / Produto à parte - R$ 15,00 (60 min)", 60),
            Map.entry("Hidratação - R$ 15,00 (30 min)", 30),
            Map.entry("Matizador - R$ 10,00 (10 min)", 10)
    );

    private final JdbcTemplate jdbc;
    private final String adminEmail;
    private final String adminPassword;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public BarbeariaApplication(
            JdbcTemplate jdbc,
            @Value("${admin.email:}") String adminEmail,
            @Value("${admin.password:}") String adminPassword
    ) {
        this.jdbc = jdbc;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(BarbeariaApplication.class, args);
    }

    @Bean
    ApplicationRunner initializeDatabase() {
        return args -> {
            if (!isValidEmail(adminEmail) || adminPassword.length() < 12) {
                throw new IllegalStateException(
                        "Configure ADMIN_EMAIL com um e-mail válido e ADMIN_PASSWORD com pelo menos 12 caracteres."
                );
            }
            jdbc.execute("PRAGMA journal_mode = WAL");
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS customers (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL UNIQUE,
                        phone TEXT NOT NULL,
                        password_hash BLOB NOT NULL,
                        password_salt BLOB NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS appointments (
                        id TEXT PRIMARY KEY,
                        customer_id TEXT NOT NULL REFERENCES customers(id),
                        service TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        starts_at TEXT NOT NULL,
                        ends_at TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS appointments_start_idx ON appointments(starts_at)");
        };
    }

    @GetMapping("/")
    public ResponseEntity<Resource> home() {
        return staticFile("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/index.html")
    public ResponseEntity<Resource> index() {
        return staticFile("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/script.js")
    public ResponseEntity<Resource> script() {
        return staticFile("script.js", MediaType.parseMediaType("text/javascript"));
    }

    @GetMapping("/style.css")
    public ResponseEntity<Resource> stylesheet() {
        return staticFile("style.css", MediaType.valueOf("text/css"));
    }

    private ResponseEntity<Resource> staticFile(String filename, MediaType mediaType) {
        Path file = Path.of(filename).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Arquivo da página não encontrado.");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/api/me")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        Session session = requireSession(request, null);
        return Map.of("user", session.user());
    }

    @GetMapping("/api/availability")
    public Map<String, Object> availability(
            @RequestParam String date,
            @RequestParam int duration
    ) {
        if (!SERVICE_CATALOG.containsValue(duration)) {
            throw badRequest("A duração do serviço selecionado não é válida.");
        }
        LocalDate day = parseDate(date);
        List<String> slots = new ArrayList<>();
        if (day.isBefore(LocalDate.now()) || day.getDayOfWeek().getValue() == 7) {
            return Map.of("slots", slots);
        }
        LocalDateTime now = LocalDateTime.now();
        for (LocalTime time = OPENING_TIME; !time.plusMinutes(duration).isAfter(CLOSING_TIME);
             time = time.plusMinutes(SLOT_MINUTES)) {
            LocalDateTime start = LocalDateTime.of(day, time);
            LocalDateTime end = start.plusMinutes(duration);
            if (start.isAfter(now) && !hasConflict(start, end)) {
                slots.add(time.toString());
            }
        }
        return Map.of("slots", slots);
    }

    @GetMapping("/api/admin/appointments")
    public Map<String, Object> adminAppointments(
            HttpServletRequest request,
            @RequestParam String from,
            @RequestParam String to
    ) {
        requireSession(request, "admin");
        LocalDate startDate = parseDate(from);
        LocalDate endDate = parseDate(to);
        if (!endDate.isAfter(startDate) || Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() > 370) {
            throw badRequest("O intervalo de datas é inválido.");
        }
        List<Map<String, Object>> appointments = jdbc.query(
                """
                SELECT a.id, a.service, a.duration, a.starts_at,
                       c.name AS client_name, c.email AS client_email, c.phone AS client_phone
                FROM appointments a JOIN customers c ON c.id = a.customer_id
                WHERE a.starts_at >= ? AND a.starts_at < ?
                ORDER BY a.starts_at
                """,
                (row, index) -> {
                    LocalDateTime start = LocalDateTime.parse(row.getString("starts_at"), DATABASE_DATE_TIME);
                    Map<String, Object> appointment = new LinkedHashMap<>();
                    appointment.put("id", row.getString("id"));
                    appointment.put("service", row.getString("service"));
                    appointment.put("duration", row.getInt("duration"));
                    appointment.put("date", start.toLocalDate().toString());
                    appointment.put("time", start.toLocalTime().toString());
                    appointment.put("clientName", row.getString("client_name"));
                    appointment.put("clientEmail", row.getString("client_email"));
                    appointment.put("clientPhone", row.getString("client_phone"));
                    return appointment;
                },
                startDate.toString(),
                endDate.toString()
        );
        return Map.of("appointments", appointments);
    }

    @PostMapping("/api/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> payload) {
        String name = cleanName(stringValue(payload, "name"));
        String email = normalizedEmail(stringValue(payload, "email"));
        String phone = stringValue(payload, "phone").trim();
        String digits = PHONE_DIGITS_PATTERN.matcher(phone).replaceAll("");
        if (digits.length() < 8 || digits.length() > 15 || phone.length() > 20) {
            throw badRequest("Informe um telefone válido com DDD.");
        }
        if (MessageDigest.isEqual(email.getBytes(StandardCharsets.UTF_8), adminEmail.getBytes(StandardCharsets.UTF_8))) {
            throw badRequest("Este e-mail está reservado para a conta de administrador.");
        }

        PasswordDigest password = hashPassword(stringValue(payload, "password"));
        String id = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO customers (id, name, email, phone, password_hash, password_salt, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                name,
                email,
                phone,
                password.hash(),
                password.salt(),
                LocalDateTime.now().toString()
        );
        Map<String, Object> user = customerUser(id, name, email);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("token", newSession("customer", id, user), "user", user));
    }

    @PostMapping("/api/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> payload) {
        String email = normalizedEmail(stringValue(payload, "email"));
        String password = stringValue(payload, "password");
        if (password.isEmpty() || password.length() > 128) {
            throw badRequest("E-mail ou senha incorretos.");
        }

        if (MessageDigest.isEqual(email.getBytes(StandardCharsets.UTF_8), adminEmail.getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), adminPassword.getBytes(StandardCharsets.UTF_8))) {
            Map<String, Object> user = Map.of(
                    "id", "admin",
                    "name", "Administrador",
                    "email", adminEmail,
                    "role", "admin"
            );
            return Map.of("token", newSession("admin", "admin", user), "user", user);
        }

        List<CustomerCredentials> matches = jdbc.query(
                "SELECT id, name, email, password_hash, password_salt FROM customers WHERE email = ?",
                (row, index) -> new CustomerCredentials(
                        row.getString("id"),
                        row.getString("name"),
                        row.getString("email"),
                        row.getBytes("password_hash"),
                        row.getBytes("password_salt")
                ),
                email
        );
        if (matches.isEmpty()) {
            throw badRequest("E-mail ou senha incorretos.");
        }
        CustomerCredentials customer = matches.get(0);
        PasswordDigest candidate = hashPassword(password, customer.salt());
        if (!MessageDigest.isEqual(candidate.hash(), customer.passwordHash())) {
            throw badRequest("E-mail ou senha incorretos.");
        }
        Map<String, Object> user = customerUser(customer.id(), customer.name(), customer.email());
        return Map.of("token", newSession("customer", customer.id(), user), "user", user);
    }

    @PostMapping("/api/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            sessions.remove(authorization.substring(7));
        }
        return Map.of("ok", true);
    }

    @PostMapping("/api/appointments")
    public synchronized ResponseEntity<Map<String, Object>> createAppointment(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload
    ) {
        Session session = requireSession(request, "customer");
        String service = stringValue(payload, "service");
        Integer duration = SERVICE_CATALOG.get(service);
        if (duration == null) {
            throw badRequest("Selecione um serviço válido.");
        }
        if (!(payload.get("duration") instanceof Number submittedDuration)
                || submittedDuration.intValue() != duration) {
            throw badRequest("A duração do serviço não corresponde à opção selecionada.");
        }

        LocalDate day = parseDate(stringValue(payload, "date"));
        if (day.isBefore(LocalDate.now())) {
            throw badRequest("Não é possível agendar em uma data passada.");
        }
        if (day.getDayOfWeek().getValue() == 7) {
            throw badRequest("A barbearia não abre aos domingos.");
        }
        LocalTime time;
        try {
            time = LocalTime.parse(stringValue(payload, "time"), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException exception) {
            throw badRequest("Selecione um horário válido.");
        }
        if (time.getMinute() % SLOT_MINUTES != 0 || time.isBefore(OPENING_TIME)) {
            throw badRequest("O horário deve começar entre 09h00 e 19h30, em intervalos de 30 minutos.");
        }
        if (time.plusMinutes(duration).isAfter(CLOSING_TIME)) {
            throw badRequest("Esse serviço ultrapassa o horário de fechamento (20h).");
        }
        LocalDateTime start = LocalDateTime.of(day, time);
        LocalDateTime end = start.plusMinutes(duration);
        if (!start.isAfter(LocalDateTime.now())) {
            throw badRequest("Escolha um horário futuro.");
        }
        if (hasConflict(start, end)) {
            throw new ApiException(HttpStatus.CONFLICT, "Esse horário acabou de ser reservado. Escolha outro horário.");
        }

        String appointmentId = UUID.randomUUID().toString();
        jdbc.update(
                """
                INSERT INTO appointments (id, customer_id, service, duration, starts_at, ends_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                appointmentId,
                session.userId(),
                service,
                duration,
                DATABASE_DATE_TIME.format(start),
                DATABASE_DATE_TIME.format(end),
                LocalDateTime.now().toString()
        );

        boolean emailSent = false;
        String emailMessage = "O agendamento está no calendário do administrador; o envio de e-mail precisa ser configurado.";
        try {
            sendConfirmationEmail(session.user(), service, start, duration);
            emailSent = true;
            emailMessage = "E-mail enviado.";
        } catch (MailException | MessagingException | IllegalStateException | IOException exception) {
            LOGGER.log(Level.SEVERE, "Falha ao enviar a confirmação do agendamento " + appointmentId, exception);
            emailMessage = "Não foi possível enviar a confirmação. Verifique as configurações de e-mail da barbearia.";
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "appointmentId", appointmentId,
                "emailSent", emailSent,
                "emailMessage", emailMessage
        ));
    }

    private boolean hasConflict(LocalDateTime start, LocalDateTime end) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE starts_at < ? AND ends_at > ?",
                Integer.class,
                DATABASE_DATE_TIME.format(end),
                DATABASE_DATE_TIME.format(start)
        );
        return count != null && count > 0;
    }

    private void sendConfirmationEmail(Map<String, Object> user, String service, LocalDateTime start, int duration)
            throws MessagingException, IOException {
        String host = environment("SMTP_HOST");
        String senderEmail = environment("SMTP_FROM_EMAIL");
        if (host.isBlank() || senderEmail.isBlank()) {
            throw new IllegalStateException("Configure SMTP_HOST e SMTP_FROM_EMAIL para habilitar o envio.");
        }
        senderEmail = normalizedEmail(senderEmail);
        String security = environmentOrDefault("SMTP_SECURITY", "starttls").toLowerCase(Locale.ROOT);
        if (!security.equals("starttls") && !security.equals("ssl")) {
            throw new IllegalStateException("SMTP_SECURITY deve ser starttls ou ssl.");
        }
        String portText = environmentOrDefault("SMTP_PORT", security.equals("ssl") ? "465" : "587");
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("SMTP_PORT deve ser uma porta numérica.");
        }
        String smtpUser = environment("SMTP_USER");
        String smtpPassword = environment("SMTP_PASSWORD");
        if (smtpUser.isBlank() != smtpPassword.isBlank()) {
            throw new IllegalStateException("Configure SMTP_USER e SMTP_PASSWORD juntos.");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(smtpUser);
        sender.setPassword(smtpPassword);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().put("mail.smtp.auth", Boolean.toString(!smtpUser.isBlank()));
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", Boolean.toString(security.equals("starttls")));
        sender.getJavaMailProperties().put("mail.smtp.starttls.required", Boolean.toString(security.equals("starttls")));
        sender.getJavaMailProperties().put("mail.smtp.ssl.enable", Boolean.toString(security.equals("ssl")));
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "20000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "20000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "20000");

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String senderName = environmentOrDefault("SMTP_FROM_NAME", "Barbearia Pente Fino");
        helper.setFrom(senderEmail, senderName);
        helper.setTo(String.valueOf(user.get("email")));
        helper.setSubject("Agendamento confirmado — Barbearia Pente Fino");
        helper.setText("""
                Olá, %s!

                Seu agendamento na Barbearia Pente Fino foi registrado:
                Serviço: %s
                Data: %s
                Horário: %s
                Duração: %d minutos

                Esperamos por você!
                """.formatted(
                user.get("name"),
                service,
                start.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                start.toLocalTime(),
                duration
        ));
        sender.send(message);
    }

    private Session requireSession(HttpServletRequest request, String requiredRole) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(
                    requiredRole == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    requiredRole == null
                            ? "Sua sessão expirou. Entre novamente."
                            : "Acesso permitido apenas ao usuário autorizado."
            );
        }
        Session session = sessions.get(authorization.substring(7));
        if (session == null || !session.expiresAt().isAfter(Instant.now())) {
            if (session != null) {
                sessions.remove(authorization.substring(7));
            }
            throw new ApiException(
                    requiredRole == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    requiredRole == null
                            ? "Sua sessão expirou. Entre novamente."
                            : "Acesso permitido apenas ao usuário autorizado."
            );
        }
        if (requiredRole != null && !requiredRole.equals(session.role())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    requiredRole.equals("admin")
                            ? "Acesso permitido apenas ao administrador."
                            : "Entre em uma conta de cliente antes de agendar."
            );
        }
        return session;
    }

    private String newSession(String role, String userId, Map<String, Object> user) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(role, userId, user, Instant.now().plus(SESSION_LIFETIME)));
        return token;
    }

    private static String normalizedEmail(String value) {
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !isValidEmail(email)) {
            throw badRequest("Informe um e-mail válido.");
        }
        return email;
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    private static String cleanName(String value) {
        String name = value.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 100) {
            throw badRequest("O nome deve ter entre 2 e 100 caracteres.");
        }
        return name;
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String text)) {
            throw badRequest("O campo " + key + " é obrigatório.");
        }
        return text;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw badRequest("Selecione uma data válida.");
        }
    }

    private static PasswordDigest hashPassword(String password) {
        if (password.length() < 8 || password.length() > 128) {
            throw badRequest("A senha deve ter entre 8 e 128 caracteres.");
        }
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return hashPassword(password, salt);
    }

    private static PasswordDigest hashPassword(String password, byte[] salt) {
        try {
            javax.crypto.spec.PBEKeySpec keySpec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, PASSWORD_ITERATIONS, 256
            );
            byte[] hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
                    .getEncoded();
            keySpec.clearPassword();
            return new PasswordDigest(hash, salt);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível verificar a senha.", exception);
        }
    }

    private static Map<String, Object> customerUser(String id, String name, String email) {
        return Map.of("id", id, "name", name, "email", email, "role", "customer");
    }

    private static String environment(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static String environmentOrDefault(String key, String defaultValue) {
        String value = environment(key);
        return value.isBlank() ? defaultValue : value;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateCustomer(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Este e-mail já possui uma conta cadastrada."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Envie os dados em formato JSON válido."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception exception) {
        LOGGER.log(Level.SEVERE, "Erro inesperado ao processar uma solicitação.", exception);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Ocorreu um erro ao processar a solicitação. Tente novamente."));
    }

    private record Session(String role, String userId, Map<String, Object> user, Instant expiresAt) {}
    private record PasswordDigest(byte[] hash, byte[] salt) {}
    private record CustomerCredentials(String id, String name, String email, byte[] passwordHash, byte[] salt) {}

    private static final class ApiException extends RuntimeException {
        private final HttpStatus status;

        private ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        private HttpStatus status() {
            return status;
        }
    }
}
