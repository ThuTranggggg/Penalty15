# HƯỚNG DẪN SETUP PROJECT PENALTY SHOOTOUT

## 📌 YÊU CẦU HỆ THỐNG
- ✅ JDK 21 (bạn đã có)
- ✅ JavaFX 23.0.1 (bạn đã có)  
- ⚠️ MySQL Server 8.0+ (cần cài đặt)
- ⚠️ MySQL Connector/J (cần tải)
- 💡 NetBeans IDE (khuyến nghị) hoặc IDE hỗ trợ JavaFX

---

## 🔧 BƯỚC 1: CÀI ĐẶT MYSQL

### 1.1. Tải MySQL
- Truy cập: https://dev.mysql.com/downloads/mysql/
- Chọn: Windows (x86, 64-bit), MySQL Installer
- Tải: mysql-installer-community-8.x.x.msi

### 1.2. Cài đặt MySQL
1. Chạy file installer vừa tải
2. Chọn "Developer Default" (hoặc "Server only")
3. Làm theo hướng dẫn:
   - Authentication: "Use Strong Password Encryption"
   - Root Password: Đặt password (VD: 123456) - **HÃY NHỚ PASSWORD NÀY!**
   - Windows Service: Để mặc định
4. Click "Execute" và đợi cài đặt hoàn tất

### 1.3. Kiểm tra MySQL
Mở Command Prompt và gõ:
```bash
mysql --version
```
Nếu hiển thị version → Thành công!

---

## 💾 BƯỚC 2: TẠO DATABASE

### 2.1. Mở MySQL Workbench
- Tìm "MySQL Workbench" trong Start Menu
- Click vào kết nối "Local instance MySQL80"
- Nhập password root bạn đã đặt

### 2.2. Import Database
1. Trong MySQL Workbench: File → Open SQL Script
2. Chọn file: `database_setup.sql` (trong thư mục project)
3. Click biểu tượng sấm sét ⚡ (Execute)
4. Kiểm tra bên trái có database "penalty_shootout" chưa

### 2.3. Kiểm tra dữ liệu
Chạy câu lệnh:
```sql
USE penalty_shootout;
SELECT * FROM users;
```
Bạn sẽ thấy 4 tài khoản test:
- player1 / 123456
- player2 / 123456
- player3 / 123456
- admin / 123456

---

## 📚 BƯỚC 3: CÀI ĐẶT THƯ VIỆN

### 3.1. Tải MySQL Connector/J
1. Truy cập: https://dev.mysql.com/downloads/connector/j/
2. Chọn "Platform Independent" 
3. Tải file: mysql-connector-j-x.x.x.zip
4. Giải nén vào một thư mục (VD: C:\mysql-connector)

### 3.2. Thêm thư viện vào NetBeans
1. Mở project trong NetBeans
2. Chuột phải vào project → Properties
3. Categories → Libraries
4. Classpath → Add JAR/Folder
5. Chọn file .jar trong thư mục vừa giải nén
   (Tên file: mysql-connector-j-x.x.x.jar)
6. Click OK

---

## ⚙️ BƯỚC 4: CẤU HÌNH DATABASE

### 4.1. Sửa file DatabaseManager.java
Mở file: `src/server/DatabaseManager.java`

Tìm dòng 14-16:
```java
private static final String URL = "jdbc:mysql://localhost:3306/penalty_shootout";
private static final String USER = "root";
private static final String PASSWORD = "1235aBc@03"; // ← SỬA DÒN NÀY
```

**Thay password bằng password MySQL của bạn:**
```java
private static final String PASSWORD = "123456"; // password bạn đã đặt
```

Lưu file (Ctrl + S)

---

## ▶️ BƯỚC 5: CHẠY PROJECT

### 5.1. Cấu trúc chạy
Project này có 2 phần:
- **Server** (phải chạy trước): `server.Server.java`
- **Client** (chạy sau): `client.ClientApp.java`

### 5.2. Chạy Server
1. Trong NetBeans: 
   - Chuột phải vào file `src/server/Server.java`
   - Chọn "Run File" (hoặc Shift+F6)
2. Console sẽ hiển thị: "Server đã khởi động trên cổng 12345"

### 5.3. Chạy Client (có thể chạy nhiều lần)
1. Chuột phải vào file `src/client/ClientApp.java`
2. Chọn "Run File"
3. Giao diện đăng nhập sẽ xuất hiện

### 5.4. Đăng nhập
Sử dụng tài khoản test:
- Username: player1
- Password: 123456

Hoặc các tài khoản khác: player2, player3, admin

---

## 🎮 HƯỚNG DẪN CHƠI

1. Chạy Server (1 lần)
2. Chạy Client lần 1 → Đăng nhập player1
3. Chạy Client lần 2 → Đăng nhập player2
4. Từ một trong hai client, mời người kia vào phòng chơi
5. Chơi game đá penalty!

---

## ❗ XỬ LÝ LỖI THƯỜNG GẶP

### Lỗi 1: "Communications link failure"
**Nguyên nhân:** MySQL chưa chạy hoặc password sai
**Giải pháp:**
- Kiểm tra MySQL đã chạy: Services → MySQL80 → Start
- Kiểm tra password trong DatabaseManager.java

### Lỗi 2: "Access denied for user 'root'@'localhost'"
**Nguyên nhân:** Password sai
**Giải pháp:**
- Sửa lại PASSWORD trong DatabaseManager.java
- Hoặc reset password MySQL

### Lỗi 3: "Unknown database 'penalty_shootout'"
**Nguyên nhân:** Chưa import file SQL
**Giải pháp:**
- Import lại file database_setup.sql trong MySQL Workbench

### Lỗi 4: "ClassNotFoundException: com.mysql.cj.jdbc.Driver"
**Nguyên nhân:** Chưa add thư viện MySQL Connector
**Giải pháp:**
- Làm lại BƯỚC 3.2

### Lỗi 5: "Could not find or load main class"
**Nguyên nhân:** Thư viện JavaFX chưa được cấu hình đúng
**Giải pháp:**
- Project Properties → Libraries → Add Library → JavaFX

---

## 📝 GHI CHÚ

- Server phải chạy TRƯỚC client
- Có thể chạy nhiều client cùng lúc (nhiều người chơi)
- Mỗi lần test, nên chạy ít nhất 2 client để có thể đấu với nhau
- Database lưu lịch sử trận đấu, điểm số, bảng xếp hạng

---

## 🆘 HỖ TRỢ

Nếu gặp lỗi khác, hãy:
1. Chụp màn hình lỗi
2. Kiểm tra Console output
3. Đảm bảo đã làm đúng từng bước

Good luck! 🎯⚽
