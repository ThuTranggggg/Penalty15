# ⚽ PENALTY SHOOTOUT GAME

Ứng dụng game đá penalty đa người chơi sử dụng JavaFX và Socket Programming.

## 📖 Giới thiệu

Đây là một game đá penalty trực tuyến nơi người chơi có thể:
- Đăng nhập và tạo tài khoản
- Thách đấu người chơi khác trong thời gian thực
- Xem bảng xếp hạng
- Xem lịch sử trận đấu
- Tích lũy điểm số

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ:** Java 21
- **GUI Framework:** JavaFX 23.0.1
- **Database:** MySQL 8.0+
- **Networking:** Java Socket (Client-Server)
- **Build Tool:** Apache Ant (NetBeans)

## 📂 Cấu trúc Project

```
Penalty15/
├── src/
│   ├── client/              # Client-side code
│   │   ├── Client.java      # Client logic
│   │   ├── ClientApp.java   # JavaFX Application entry
│   │   └── GUI/             # Controllers cho UI
│   ├── server/              # Server-side code
│   │   ├── Server.java      # Server main
│   │   ├── ClientHandler.java
│   │   ├── DatabaseManager.java
│   │   └── GameRoom.java
│   ├── common/              # Shared classes
│   │   ├── User.java
│   │   ├── Match.java
│   │   ├── Message.java
│   │   └── MatchDetails.java
│   └── resources/           # FXML và CSS files
├── database_setup.sql       # Database schema
└── SETUP_GUIDE.md          # Hướng dẫn cài đặt
```

## 🚀 Hướng dẫn cài đặt

Xem file chi tiết: **[SETUP_GUIDE.md](SETUP_GUIDE.md)**

### Tóm tắt nhanh:

1. **Cài đặt MySQL** và tạo database:
   ```bash
   mysql -u root -p < database_setup.sql
   ```

2. **Cấu hình database** trong `src/server/DatabaseManager.java`:
   ```java
   private static final String PASSWORD = "your_password";
   ```

3. **Thêm thư viện:**
   - MySQL Connector/J
   - JavaFX (đã có)

4. **Chạy:**
   - Chạy `Server.java` trước
   - Chạy `ClientApp.java` (có thể chạy nhiều lần)

## 🎮 Cách chơi

1. Đăng nhập với tài khoản (mặc định: player1/123456)
2. Chọn người chơi online để thách đấu
3. Lần lượt làm người sút và thủ môn
4. Chọn hướng sút/bắt bóng
5. Người thắng nhiều lượt hơn sẽ chiến thắng!

## 🔐 Tài khoản mặc định

Sau khi import database, bạn có thể dùng các tài khoản sau:

| Username | Password | Điểm |
|----------|----------|------|
| player1  | 123456   | 100  |
| player2  | 123456   | 80   |
| player3  | 123456   | 50   |
| admin    | 123456   | 200  |

## 📊 Database Schema

### Table: users
- `id` - Primary key
- `username` - Tên đăng nhập (unique)
- `password` - Mật khẩu
- `points` - Điểm số
- `status` - Trạng thái (online/offline)

### Table: matches
- `id` - Primary key
- `player1_id` - Người chơi 1
- `player2_id` - Người chơi 2
- `winner_id` - Người thắng
- `end_reason` - Lý do kết thúc
- `timestamp` - Thời gian

### Table: match_details
- `id` - Primary key
- `match_id` - ID trận đấu
- `round` - Lượt chơi
- `shooter_id` - Người sút
- `goalkeeper_id` - Thủ môn
- `shooter_direction` - Hướng sút
- `goalkeeper_direction` - Hướng bắt
- `result` - Kết quả (goal/save)

## 🌐 Kiến trúc mạng

```
Client 1 ─────┐
              │
Client 2 ─────┼────> Server (Port 12345) ────> MySQL Database
              │
Client 3 ─────┘
```

- **Port:** 12345
- **Protocol:** TCP Socket
- **Message Format:** Serialized Java objects

## 📝 Tính năng

- ✅ Đăng nhập/Đăng xuất
- ✅ Hiển thị danh sách người chơi online
- ✅ Gửi lời mời đấu
- ✅ Chơi game penalty real-time
- ✅ Lưu lịch sử trận đấu
- ✅ Bảng xếp hạng theo điểm
- ✅ Xem chi tiết trận đấu
- ✅ Tự động cập nhật điểm số

## ⚠️ Lưu ý

- Server phải chạy trước khi chạy Client
- Mỗi client phải đăng nhập bằng tài khoản khác nhau
- Cần ít nhất 2 client để có thể chơi
- Đảm bảo MySQL service đang chạy
- Port 12345 phải không bị firewall chặn

## 🐛 Troubleshooting

Xem mục "XỬ LÝ LỖI THƯỜNG GẶP" trong [SETUP_GUIDE.md](SETUP_GUIDE.md)

## 👨‍💻 Phát triển

Để phát triển thêm:
1. Clone/Download project
2. Import vào NetBeans
3. Cấu hình database
4. Thêm tính năng mới

## 📜 License

Educational project - Học kỳ 7

## 🤝 Đóng góp

Đây là project học tập. Mọi góp ý xin gửi về...

---

**Happy Gaming! ⚽🎯**
