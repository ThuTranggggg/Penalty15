# 🎉 Hướng Dẫn Thêm Video Pháo Giấy Chúc Mừng

## ✅ Đã thêm tính năng:

Khi người chơi **THẮNG**, hệ thống sẽ tự động phát video pháo giấy chúc mừng!

---

## 📹 Chuẩn bị video

### Cách 1: Dùng MP4 có nền trong suốt (khuyến nghị nếu có)
1. Tải video pháo giấy có **alpha channel** (nền trong suốt)
2. Format: **MP4 with H.264 codec + alpha channel**
3. Đặt tên file: `celebration.mp4`
4. Đặt vào: `src/assets/celebration.mp4`

**Lưu ý:** MP4 thông thường KHÔNG hỗ trợ alpha channel tốt. Nếu muốn nền trong suốt, nên dùng:
- **WebM** (VP9 codec with alpha) - tốt nhất cho nền trong suốt
- **MOV** (ProRes 4444) - nhưng file size lớn
- Hoặc dùng GIF animated

### Cách 2: Dùng MP4 thông thường (dễ tìm)
1. Tải video pháo giấy bất kỳ từ internet
2. Format: **MP4** thông thường
3. Đặt tên: `celebration.mp4`
4. Đặt vào: `src/assets/celebration.mp4`
5. Hiệu ứng: Video sẽ phủ lên màn hình với opacity 85% (hơi trong suốt)

### Cách 3: Dùng AnimatedGIF (đơn giản nhất)
Nếu bạn muốn dùng GIF thay vì MP4, tôi có thể chỉnh code để dùng GIF.

---

## 🔍 Tìm video pháo giấy

**Nguồn miễn phí:**
1. **Pixabay** - https://pixabay.com/videos/search/confetti/
2. **Pexels** - https://www.pexels.com/search/videos/confetti/
3. **Mixkit** - https://mixkit.co/free-stock-video/confetti/

**Từ khóa tìm kiếm:**
- "confetti celebration"
- "confetti explosion"
- "party celebration"
- "firework celebration"

---

## 🎨 Hiệu ứng hiện tại

```java
// Video được phát khi người chơi thắng
celebrationMediaView.setOpacity(0.85); // 85% opacity (hơi trong suốt)
celebrationMediaView.setFitWidth(paneWidth); // Full width
celebrationMediaView.setFitHeight(paneHeight); // Full height

// Tự động tắt khi video kết thúc
celebrationMediaPlayer.setOnEndOfMedia(() -> {
    celebrationMediaPlayer.stop();
    celebrationMediaPlayer.dispose();
    gamePane.getChildren().remove(celebrationMediaView);
});
```

---

## 📂 Cấu trúc thư mục

```
Penalty15/
├── src/
│   ├── assets/
│   │   ├── celebration.mp4  ← ĐẶT VIDEO VÀO ĐÂY
│   │   ├── CauThu.png
│   │   ├── QuaBong.png
│   │   └── ...
│   └── ...
```

---

## ⚙️ Tùy chỉnh thêm

### Thay đổi độ trong suốt
Trong `GameRoomController.java`, dòng:
```java
celebrationMediaView.setOpacity(0.85); // Thay đổi từ 0.0 (hoàn toàn trong suốt) đến 1.0 (không trong suốt)
```

### Thay đổi kích thước video
```java
celebrationMediaView.setFitWidth(paneWidth * 0.8); // 80% chiều rộng
celebrationMediaView.setFitHeight(paneHeight * 0.6); // 60% chiều cao
celebrationMediaView.setPreserveRatio(true); // Giữ tỷ lệ
```

### Lặp lại video
```java
celebrationMediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Lặp mãi
celebrationMediaPlayer.setCycleCount(3); // Lặp 3 lần
```

---

## 🐛 Xử lý lỗi

Nếu video không phát:
1. ✅ Kiểm tra file `celebration.mp4` có tồn tại trong `src/assets/`
2. ✅ Kiểm tra format video (phải là MP4 với H.264 codec)
3. ✅ Xem console log có thông báo lỗi không
4. ✅ Thử video khác (đơn giản hơn, file size nhỏ hơn)

---

## 🎬 Ví dụ video tốt

**Đặc điểm video tốt:**
- ✅ Độ phân giải: 1280x720 hoặc 1920x1080
- ✅ Thời lượng: 3-5 giây
- ✅ File size: < 5MB
- ✅ Format: MP4 (H.264)
- ✅ Không có âm thanh hoặc âm thanh nhỏ

---

## 💡 Lưu ý

1. **Nền trong suốt thật sự:** MP4 thông thường KHÔNG hỗ trợ alpha channel tốt. Nếu cần nền trong suốt hoàn hảo:
   - Dùng **WebM** với VP9 codec
   - Hoặc dùng **AnimatedGIF** 
   - Hoặc tạo hiệu ứng bằng code JavaFX (particles)

2. **Performance:** Video quá nặng có thể làm lag game. Nên chọn video nhẹ, ngắn.

3. **Fallback:** Nếu video không tải được, game vẫn chạy bình thường (chỉ không có hiệu ứng pháo giấy).

---

## 🚀 Test

1. Chạy game
2. Chơi đến khi thắng
3. Xem video pháo giấy phát tự động!

Nếu muốn dùng GIF hoặc tạo hiệu ứng particles bằng code JavaFX, tôi có thể hỗ trợ thêm! 🎉
