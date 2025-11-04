# Các Thay Đổi Đã Thực Hiện cho Game Penalty15

## Ngày cập nhật: 3 tháng 11, 2025

### CẬP NHẬT MỚI NHẤT: Logic Chơi Lại

## ✅ Flow Mới Sau 10 Rounds:

1. **Kết thúc 10 vòng** → Hiển thị kết quả và tỷ số
2. **Server gửi yêu cầu chơi lại** → Cả 2 người chơi nhận dialog "Bạn có muốn chơi lại không?"
3. **Hai trường hợp:**
   - ✅ **CẢ HAI đồng ý** → Reset game, chơi lại từ vòng 1
   - ❌ **Một trong hai từ chối** → Hiển thị thông báo "Trận đấu kết thúc" → Tự động về màn hình chính

### 1. ~~Tự động về màn hình chính sau 10 vòng~~ (ĐÃ CẬP NHẬT)
**Vị trí:** `GameRoomController.java` - phương thức `handleMatchEnd()`, `Client.java`

**Thay đổi mới:**
- Sau khi hiển thị kết quả trận đấu (thắng/thua/hòa)
- **KHÔNG** tự động về màn hình chính nữa
- Hiển thị "⏳ Đang chờ quyết định chơi lại..."
- Đợi server gửi message `play_again_request`
- Chỉ khi có người từ chối hoặc nhận `match_end` với message "Trận đấu kết thúc" mới tự động về màn hình chính

**Code quan trọng trong Client.java:**
```java
case "match_end":
case "game_over":
    Platform.runLater(() -> {
        if (gameRoomController != null) {
            String endMessage = (String) message.getContent();
            // Check if this is final match end (from rematch declined or quit)
            if (endMessage != null && endMessage.contains("Trận đấu kết thúc")) {
                // This is final end - show message and return to main
                showAlert("Thông báo", endMessage, Alert.AlertType.INFORMATION);
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> {
                    try {
                        showMainUI();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                delay.play();
            } else {
                // This is match result after 10 rounds - just show result, wait for play_again_request
                gameRoomController.handleMatchEnd(endMessage);
            }
        }
    });
    break;

case "rematch_declined":
    Platform.runLater(() -> {
        if (gameRoomController != null) {
            // Show declined message
            String declineMsg = (String) message.getContent();
            showAlert("Thông báo", declineMsg, Alert.AlertType.INFORMATION);
            // Auto return to main screen after a short delay
            PauseTransition delay = new PauseTransition(Duration.millis(500));
            delay.setOnFinished(e -> {
                try {
                    showMainUI();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            delay.play();
        }
    });
    break;
```

**Code trong GameRoomController.java:**
```java
public void handleMatchEnd(String finalResult) {
    Platform.runLater(() -> {
        // ... hiển thị kết quả ...
        
        // Show result alert - NO auto return to main screen
        // Server will send play_again_request after this
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Kết quả trận đấu");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        
        // Wait for server to send play_again_request
        // Do NOT auto return to main screen
        
        instructionLabel.setText("⏳ Đang chờ quyết định chơi lại...");
        instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0ea5e9; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f0f9ff; -fx-background-radius: 10; -fx-border-radius: 10;");
    });
}
```

---

### 2. ✅ Loại bỏ dấu tròn phía trên thủ môn
**Vị trí:** `GameRoomController.java` - phương thức `createEnhancedGoalkeeper()`

**Thay đổi:**
- Đã xóa `Circle head` ở cả hai chế độ (sprite mode và fallback mode)
- Giờ thủ môn không còn có vòng tròn hiển thị phía trên nữa
- Giao diện sạch sẽ và tập trung hơn vào hành động game

---

### 3. ✅ Thiết kế lại nút chọn vị trí - Click trực tiếp không cần bấm nút
**Vị trí:** 
- `GameRoomController.java` - phương thức `handleShootMode()`, `handleGoalkeeperMode()`, `promptYourTurn()`, `promptGoalkeeperTurn()`
- `style.css` - styling cho toggle buttons

**Thay đổi:**

#### A. Tự động chọn chế độ khi đến lượt
- Khi đến lượt **sút bóng**: Nút "🎯 SÚT BÓNG" tự động **sáng lên màu xanh lá**
- Khi đến lượt **chặn bóng**: Nút "🛡️ CHẶN BÓNG" tự động **sáng lên màu vàng**
- Người chơi **KHÔNG CẦN** bấm nút nữa, chỉ cần **click vào vị trí** trên khung thành

#### B. Hiệu ứng nút được chọn (CSS)
**Nút Sút Bóng (khi được chọn):**
- Màu nền: Gradient xanh lá (#4ecca3 → #16a34a)
- Chữ màu trắng
- Phóng to 8% (scale 1.08)
- Đổ bóng phát sáng màu xanh lá
- Viền sáng màu #4ecca3

**Nút Chặn Bóng (khi được chọn):**
- Màu nền: Gradient vàng (#ffd93d → #f59e0b)
- Chữ màu tối (#1e293b)
- Phóng to 8% (scale 1.08)
- Đổ bóng phát sáng màu vàng
- Viền sáng màu #ffd93d

#### C. Cơ chế hoạt động mới
```
CŨ: 
1. Bấm nút "Sút bóng" 
2. Bấm nút "Chọn vị trí"
3. Click vào vị trí

MỚI:
1. Đến lượt → Nút tự động sáng
2. Click trực tiếp vào vị trí → Xong!
```

---

### 4. ✅ Thay đổi biểu tượng nút gửi tin nhắn
**Vị trí:** `GameRoomUI.fxml`

**Thay đổi:**
- **Cũ:** 📤 (hộp thư đi)
- **Mới:** ✈ (máy bay giấy)
- Font size tăng lên 18px (to hơn, rõ hơn)
- Border radius 20px (hình tròn hoàn toàn)
- Hiệu ứng hover được cải thiện

---

## 📊 Sơ Đồ Flow Game Hoàn Chỉnh

```
┌─────────────────────────────────────┐
│   Bắt đầu trận đấu (10 vòng)        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Chơi vòng 1-10                     │
│  - Tự động chọn chế độ sút/chặn     │
│  - Click trực tiếp vào vị trí       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Kết thúc 10 vòng                   │
│  → Hiển thị kết quả & tỷ số         │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  "⏳ Đang chờ quyết định chơi lại..." │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Server gửi play_again_request      │
│  Dialog: "Bạn có muốn chơi lại?"    │
└──────────────┬──────────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ▼                ▼
   ┌───────┐       ┌────────┐
   │  CÓ   │       │ KHÔNG  │
   └───┬───┘       └────┬───┘
       │                │
       │                ▼
       │    ┌─────────────────────────┐
       │    │ Một trong hai từ chối?  │
       │    └──────────┬──────────────┘
       │               │
       │               ▼ (Có)
       │    ┌─────────────────────────┐
       │    │ "Trận đấu kết thúc."    │
       │    │ → Về màn hình chính     │
       │    └─────────────────────────┘
       │
       ▼ (Cả hai đồng ý)
┌─────────────────────────────────────┐
│  Reset game → Chơi lại từ vòng 1    │
└─────────────────────────────────────┘
```

---

## Tóm tắt các files đã thay đổi

1. **GameRoomController.java**
   - ~~Thêm tự động về màn hình chính sau trận đấu~~ (Đã xóa - chờ play_again_request)
   - Xóa vòng tròn thủ môn
   - Tự động chọn chế độ khi đến lượt
   - Cải thiện logic button handling
   - Thêm thông báo "Đang chờ quyết định chơi lại..."

2. **Client.java**
   - Thêm logic phân biệt `match_end` (kết quả) vs `match_end` (kết thúc cuối cùng)
   - Xử lý `rematch_declined` → tự động về màn hình chính
   - Thêm import `PauseTransition` và `Duration`

3. **GameRoomUI.fxml**
   - Đổi icon nút gửi tin nhắn từ 📤 thành ✈
   - Cải thiện styling cho nút

4. **style.css**
   - Thêm styling đặc biệt cho nút được chọn
   - Hiệu ứng gradient và glow cho shootModeButton & goalkeeperModeButton
   - Scale effect khi nút được chọn

---

## Hướng dẫn test các tính năng mới

### Test 1: Flow chơi lại
1. Chơi đủ 10 vòng
2. Xem thông báo kết quả → Đóng thông báo
3. Thấy "⏳ Đang chờ quyết định chơi lại..."
4. Dialog hỏi "Bạn có muốn chơi lại không?"
   - **Chọn CÓ** (cả 2 người) → Game reset, chơi lại từ vòng 1
   - **Chọn KHÔNG** (1 trong 2) → Thông báo "Trận đấu kết thúc" → Tự động về màn hình chính

### Test 2: Thủ môn không có vòng tròn
1. Vào phòng game
2. Nhìn vào thủ môn → Không còn vòng tròn phía trên

### Test 3: Click trực tiếp chọn vị trí
1. Đến lượt sút → Nút "🎯 SÚT BÓNG" sáng màu xanh lá, phóng to
2. Click trực tiếp vào vị trí khung thành → Không cần bấm nút
3. Đến lượt chặn → Nút "🛡️ CHẶN BÓNG" sáng màu vàng, phóng to
4. Click trực tiếp vào vị trí → Không cần bấm nút

### Test 4: Icon máy bay giấy
1. Vào phòng game
2. Nhìn vào ô chat
3. Nút gửi hiện ✈ thay vì 📤

---

## Ghi chú
- Tất cả các thay đổi đã được tích hợp vào code hiện có
- Không ảnh hưởng đến logic game cũ
- Cải thiện trải nghiệm người dùng (UX)
- Code tương thích với hệ thống hiện tại
- Logic rematch đã có sẵn trong server (GameRoom.java)

