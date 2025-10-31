-- ====================================
-- CẬP NHẬT DATABASE - THÊM CỘT WINS
-- ====================================
-- File này dùng để cập nhật database hiện có
-- Chạy file này nếu bạn đã tạo database trước đó

USE penalty15;

-- Thêm cột wins vào bảng users (nếu chưa có)
ALTER TABLE users ADD COLUMN IF NOT EXISTS wins INT DEFAULT 0 AFTER points;

-- Cập nhật dữ liệu mẫu (optional)
UPDATE users SET wins = 10 WHERE username = 'player1';
UPDATE users SET wins = 8 WHERE username = 'player2';
UPDATE users SET wins = 5 WHERE username = 'player3';
UPDATE users SET wins = 20 WHERE username = 'admin';

-- Kiểm tra kết quả
SELECT * FROM users ORDER BY points DESC, wins DESC;

SELECT 'Database updated successfully!' AS Status;
