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
            // Thiết lập Stage trước khi tạo Client
            primaryStage.setTitle("Penalty Shootout");
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            
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
                        // Đợi một chút để message được gửi
                        Thread.sleep(100);
                    }
                } catch (IOException e) {
                    // Bỏ qua lỗi khi gửi message - socket có thể đã đóng
                    System.out.println("Không thể gửi logout message: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    try {
                        client.closeConnection();
                    } catch (IOException e) {
                        // Bỏ qua lỗi khi đóng connection
                        System.out.println("Lỗi khi đóng kết nối: " + e.getMessage());
                    }
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