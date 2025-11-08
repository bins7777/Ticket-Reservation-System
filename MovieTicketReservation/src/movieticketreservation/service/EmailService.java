package movieticketreservation.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import movieticketreservation.util.EmailConfig;

public final class EmailService {

    private EmailService() {}

    public static void sendTicketEmail(String recipient,
                                       String subject,
                                       String body,
                                       Path qrAttachment) throws IOException {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        EmailConfig config = EmailConfig.getInstance();
        String message = buildMessage(config, recipient, subject, body, qrAttachment);
        send(config, recipient, message);
    }

    private static String buildMessage(EmailConfig config,
                                       String recipient,
                                       String subject,
                                       String body,
                                       Path attachment) throws IOException {
        String sanitizedSubject = subject == null ? "Movie Ticket QR" : subject;
        String sanitizedBody = body == null ? "" : body;
        if (attachment != null && !Files.exists(attachment)) {
            throw new IOException("Attachment not found: " + attachment);
        }

        if (attachment == null) {
            return new StringBuilder()
                    .append("From: ").append(config.getFromAddress()).append("\r\n")
                    .append("To: ").append(recipient).append("\r\n")
                    .append("Subject: ").append(sanitizedSubject).append("\r\n")
                    .append("Content-Type: text/plain; charset=UTF-8\r\n")
                    .append("Content-Transfer-Encoding: 8bit\r\n\r\n")
                    .append(sanitizedBody)
                    .append("\r\n")
                    .toString();
        }

        String boundary = "----=_MovieTicket_" + System.currentTimeMillis();
        byte[] attachmentBytes = Files.readAllBytes(attachment);
        String encodedAttachment = Base64.getMimeEncoder().encodeToString(attachmentBytes);
        return new StringBuilder()
                .append("From: ").append(config.getFromAddress()).append("\r\n")
                .append("To: ").append(recipient).append("\r\n")
                .append("Subject: ").append(sanitizedSubject).append("\r\n")
                .append("MIME-Version: 1.0\r\n")
                .append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n")
                .append("Content-Transfer-Encoding: 8bit\r\n\r\n")
                .append(sanitizedBody).append("\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: image/png; name=\"ticket-qr.png\"\r\n")
                .append("Content-Transfer-Encoding: base64\r\n")
                .append("Content-Disposition: attachment; filename=\"ticket-qr.png\"\r\n\r\n")
                .append(encodedAttachment).append("\r\n")
                .append("--").append(boundary).append("--\r\n")
                .toString();
    }

    private static void send(EmailConfig config,
                             String recipient,
                             String message) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(config.getHost(), config.getPort());
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            readResponse(reader, 220);
            sendCommand(writer, "EHLO movieticketreservation.local");
            readMultiLineResponse(reader, 250);
            sendCommand(writer, "AUTH LOGIN");
            readResponse(reader, 334);
            sendCommand(writer, Base64.getEncoder().encodeToString(config.getUsername().getBytes(StandardCharsets.UTF_8)));
            readResponse(reader, 334);
            sendCommand(writer, Base64.getEncoder().encodeToString(config.getPassword().getBytes(StandardCharsets.UTF_8)));
            readResponse(reader, 235);
            sendCommand(writer, "MAIL FROM:<" + config.getFromAddress() + ">");
            readResponse(reader, 250);
            sendCommand(writer, "RCPT TO:<" + recipient + ">");
            readResponse(reader, 250);
            sendCommand(writer, "DATA");
            readResponse(reader, 354);
            writer.write(message);
            writer.write("\r\n.\r\n");
            writer.flush();
            readResponse(reader, 250);
            sendCommand(writer, "QUIT");
            readResponse(reader, 221);
        }
    }

    private static void sendCommand(BufferedWriter writer, String command) throws IOException {
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
    }

    private static void readResponse(BufferedReader reader, int expectedCode) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("SMTP server closed connection.");
        }
        if (line.length() < 3 || !line.substring(0, 3).equals(Integer.toString(expectedCode))) {
            throw new IOException("Unexpected SMTP response: " + line);
        }
    }

    private static void readMultiLineResponse(BufferedReader reader, int expectedCode) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() >= 4 && line.charAt(3) == '-') {
                continue;
            }
            int code = Integer.parseInt(line.substring(0, 3));
            if (code != expectedCode) {
                throw new IOException("Unexpected SMTP response: " + line);
            }
            break;
        }
    }
}
