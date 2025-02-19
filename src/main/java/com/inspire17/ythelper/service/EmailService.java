package com.inspire17.ythelper.service;

import com.inspire17.ythelper.dto.AccountStatus;
import com.inspire17.ythelper.dto.SignupRequestDto;
import com.inspire17.ythelper.dto.UserRole;
import com.inspire17.ythelper.entity.UserEntity;
import com.inspire17.ythelper.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${company.name}")
    private String companyName;

    @Value("${company.logo}")
    private String companyLogo;

    @Value("${HOST_URL:http://localhost:8080}") // Default to localhost for local dev
    private String hostUrl;

    @Value("${UI_HOST_URL:http://localhost:3000/admin}") // Default to localhost for local dev
    private String consoleUrl;

    @Autowired
    private UserRepository userRepository;

    @Async("verificationEmailTaskExecutor")
    public void sendVerificationEmail(String to, String token) {
        String subject = MessageFormat.format("🔐 Verify Your Email - Welcome to {0}!", companyName);
        String verificationUrl = hostUrl + "/api/auth/verify?token=" + token;

        // HTML Email Content with Images
        String content = "<html><body style='font-family:Arial, sans-serif; text-align:center;'>"
                + "<div style='max-width: 600px; margin: auto; padding: 20px; border-radius: 10px; background-color: #f9f9f9;'>"
                + "<img src='" + companyLogo + "' alt='Logo' style='width:120px; margin-bottom:20px;' />"
                + "<h2 style='color: #333;'>Verify Your Email Address</h2>"
                + "<p style='color: #555; font-size:16px;'>Thanks for signing up! Click the button below to verify your email:</p>"
                + "<a href='" + verificationUrl + "' style='display:inline-block; padding: 12px 24px; font-size: 16px; "
                + "color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px; margin:20px 0;'>"
                + "Verify Email</a>"
                + "<p style='color: #888; font-size:14px;'>If the button doesn't work, copy and paste this URL into your browser:</p>"
                + "<p style='word-break: break-all; font-size:14px; color:#007BFF;'>" + verificationUrl + "</p>"
                + "<hr style='margin: 20px 0;'>"
                + "<p style='color: #aaa; font-size:12px;'>© 2025 " + companyName + ". All rights reserved.</p>"
                + "</div>"
                + "</body></html>";

        try {
            sendEmail(to, subject, content);
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        mailSender.send(message);
    }

    @Async("verificationEmailTaskExecutor")
    public void notifyAdminsForApproval(@Valid SignupRequestDto signupRequest) {
        try {


            List<UserEntity> activeAdmins = userRepository.findByUserRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE);

            if (activeAdmins.isEmpty()) {
                log.warn("No active admins found. Approval emails will not be sent.");
                return;
            }

            for (UserEntity admin : activeAdmins) {
                String subject = "🔔 New User Signup Request - Admin Approval Needed";

                String content = "<html><body style='font-family:Arial, sans-serif; text-align:center;'>"
                        + "<div style='max-width: 600px; margin: auto; padding: 20px; border-radius: 10px; background-color: #f9f9f9;'>"
                        + "<img src='" + companyLogo + "' alt='Logo' style='width:120px; margin-bottom:20px;' />"
                        + "<h2 style='color: #333;'>New User Signup Request</h2>"
                        + "<p style='color: #555; font-size:16px;'>A new user: <b style='color:#007BFF;'>" + signupRequest.getFullName() + "</b> has signed up requesting the role: <b style='color:#007BFF;'>"
                        + signupRequest.getUserRole() + "</b></p>"
                        + "<p style='color: #555; font-size:14px;'>Please log in to your admin panel to approve or reject this request.</p>"
                        + "<a href='" + consoleUrl + "' style='display:inline-block; padding: 12px 24px; font-size: 16px; "
                        + "color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px; margin:20px 0;'>"
                        + "🔑 Login to Admin Panel</a>"
                        + "<hr style='margin: 20px 0;'>"
                        + "<p style='color: #aaa; font-size:12px;'>© 2025 " + companyName + ". All rights reserved.</p>"
                        + "</div>"
                        + "</body></html>";
                sendEmail(admin.getEmailId(), subject, content);
                log.info("Approval email sent to admin: {}", admin.getEmailId());
            }
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }

    @Async("verificationEmailTaskExecutor")
    public void sendUserApprovalEmail(UserEntity user) {
        try {
            String subject = "🎉 Your Account Has Been Approved!";
            String loginUrl = hostUrl + "/login";

            // Styled HTML Email Content
            String content = "<html><body style='font-family:Arial, sans-serif; text-align:center;'>"
                    + "<div style='max-width: 600px; margin: auto; padding: 20px; border-radius: 10px; background-color: #f9f9f9;'>"
                    + "<img src='" + companyLogo + "' alt='Logo' style='width:120px; margin-bottom:20px;' />"
                    + "<h2 style='color: #28a745;'>🎉 Congratulations, " + user.getFullName() + "!</h2>"
                    + "<p style='color: #555; font-size:16px;'>Your account has been approved.</p>"
                    + "<p style='color: #555; font-size:14px;'>You can now log in and start using our platform.</p>"
                    + "<a href='" + loginUrl + "' style='display:inline-block; padding: 12px 24px; font-size: 16px; "
                    + "color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px; margin:20px 0;'>"
                    + "Login Now</a>"
                    + "<p style='color: #888; font-size:14px;'>If the button doesn't work, copy and paste this URL into your browser:</p>"
                    + "<p style='word-break: break-all; font-size:14px; color:#007BFF;'>" + loginUrl + "</p>"
                    + "<hr style='margin: 20px 0;'>"
                    + "<p style='color: #aaa; font-size:12px;'>© 2025 " + companyName + ". All rights reserved.</p>"
                    + "</div>"
                    + "</body></html>";

            sendEmail(user.getEmailId(), subject, content);
            log.info("Approval email sent to user: {}", user.getEmailId());

        } catch (Exception e) {
            log.error("Failed to send approval email to {}: {}", user.getEmailId(), e.getMessage());
        }
    }

    @Async("verificationEmailTaskExecutor")
    public void sendPasswordResetOtp(String email, String otp) {
        try {
            String subject = "🔑 Password Reset OTP - " + companyName;

            // Reset Password URL (optional)
            String resetUrl = hostUrl + "/reset-password";

            // Fancy HTML Email Content
            String content = "<html><body style='font-family: Arial, sans-serif; text-align: center;'>"
                    + "<div style='max-width: 600px; margin: auto; padding: 20px; border-radius: 10px; background-color: #f9f9f9;'>"
                    + "<img src='" + companyLogo + "' alt='Logo' style='width:120px; margin-bottom:20px;' />"
                    + "<h2 style='color: #ff9800;'>🔑 Password Reset Request</h2>"
                    + "<p style='color: #555; font-size:16px;'>We received a request to reset your password.</p>"
                    + "<p style='color: #555; font-size:14px;'>Use the OTP below to proceed:</p>"
                    + "<p style='font-size: 24px; font-weight: bold; color: #007BFF; background: #E3F2FD; padding: 10px; border-radius: 5px; display: inline-block;'>"
                    + otp + "</p>"
                    + "<p style='color: #888; font-size:14px;'>If you did not request this, please ignore this email.</p>"
                    + "<a href='" + resetUrl + "' style='display:inline-block; padding: 12px 24px; font-size: 16px; "
                    + "color: #fff; background-color: #007BFF; text-decoration: none; border-radius: 5px; margin:20px 0;'>"
                    + "Reset Password</a>"
                    + "<p style='color: #888; font-size:14px;'>Or copy and paste this link into your browser:</p>"
                    + "<p style='word-break: break-all; font-size:14px; color:#007BFF;'>" + resetUrl + "</p>"
                    + "<hr style='margin: 20px 0;'>"
                    + "<p style='color: #aaa; font-size:12px;'>© 2025 " + companyName + ". All rights reserved.</p>"
                    + "</div>"
                    + "</body></html>";

            sendEmail(email, subject, content);
            log.info("Password reset OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());
        }
    }
}
