package client.GUI;

import client.Client;
import common.Message;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    private Client client;

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void handleLogin() throws IOException {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        String[] credentials = {username, password};
        Message loginMessage = new Message("login", credentials);
        client.sendMessage(loginMessage);
    }

    public void showError(String error) {
        Platform.runLater(() -> {
            errorLabel.setText(error);
        });
    }
}
