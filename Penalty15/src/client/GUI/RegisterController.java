package client.GUI;

import client.Client;
import common.Message;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void handleRegister() throws IOException {
        // Reset messages
        errorLabel.setText("");
        successLabel.setText("");
        
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Validate input
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        
        if (username.length() < 3) {
            errorLabel.setText("Tên đăng nhập phải có ít nhất 3 ký tự.");
            return;
        }
        
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            errorLabel.setText("Tên đăng nhập chỉ được chứa chữ cái, số và dấu gạch dưới.");
            return;
        }
        
        if (password.length() < 6) {
            errorLabel.setText("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            errorLabel.setText("Mật khẩu xác nhận không khớp.");
            return;
        }
        
        // Send register request to server
        String[] credentials = {username, password};
        Message registerMessage = new Message("register", credentials);
        client.sendMessage(registerMessage);
    }

    @FXML
    private void handleBackToLogin() throws IOException {
        client.showLoginUI();
    }

    public void showError(String error) {
        Platform.runLater(() -> {
            errorLabel.setText(error);
            successLabel.setText("");
        });
    }

    public void showSuccess(String message) {
        Platform.runLater(() -> {
            successLabel.setText(message);
            errorLabel.setText("");
            
            // Clear form fields
            usernameField.clear();
            passwordField.clear();
            confirmPasswordField.clear();
            
            // Auto redirect to login after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        try {
                            client.showLoginUI();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }
}
