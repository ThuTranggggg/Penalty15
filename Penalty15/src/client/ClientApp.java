package client;

import common.Message;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class ClientApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Client client = new Client(primaryStage);
            client.showLoginUI();

            // Chạy kết nối server trên một luồn2g riêng để tránh làm đóng băng giao diện
            new Thread(() -> {
                try {
                    // Thay "localhost" bằng địa chỉ IP của server nếu cần
                    client.startConnection("localhost", 12345);
                } catch (Exception e) {
                    e.printStackTrace();
                    client.showErrorAlert("Không thể kết nối tới server.");
                }
            }).start();

            // Thêm event handler cho việc đóng ứng dụng
            primaryStage.setOnCloseRequest(event -> {
                try {
                    if (client.getUser() != null) {
                        // Gửi yêu cầu đăng xuất trước khi đóng
                        Message logoutMessage = new Message("logout", client.getUser().getId());
                        client.sendMessage(logoutMessage);
                    }
                    client.closeConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            // Nếu có lỗi trong việc thiết lập UI
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}