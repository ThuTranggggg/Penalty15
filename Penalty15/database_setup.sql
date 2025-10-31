-- ====================================
-- PENALTY SHOOTOUT DATABASE SETUP
-- ====================================

-- Tạo database
CREATE DATABASE IF NOT EXISTS penalty15 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Sử dụng database
USE penalty15;

-- ====================================
-- Bảng USERS (người dùng)
-- ====================================
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    points INT DEFAULT 0,
    wins INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'offline',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================
-- Bảng MATCHES (trận đấu)
-- ====================================
CREATE TABLE IF NOT EXISTS matches (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player1_id INT NOT NULL,
    player2_id INT NOT NULL,
    winner_id INT,
    end_reason VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (player2_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================
-- Bảng MATCH_DETAILS (chi tiết trận đấu)
-- ====================================
CREATE TABLE IF NOT EXISTS match_details (
    id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    round INT NOT NULL,
    shooter_id INT NOT NULL,
    goalkeeper_id INT NOT NULL,
    shooter_direction VARCHAR(20),
    goalkeeper_direction VARCHAR(20),
    result VARCHAR(20),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    FOREIGN KEY (shooter_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (goalkeeper_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ====================================
-- THÊM DỮ LIỆU MẪU (Test accounts)
-- ====================================
-- Password cho tất cả là: 123456
INSERT INTO users (username, password, points, wins, status) VALUES
('player1', '123456', 100, 10, 'offline'),
('player2', '123456', 80, 8, 'offline'),
('player3', '123456', 50, 5, 'offline'),
('admin', '123456', 200, 20, 'offline');

-- ====================================
-- HIỂN thị kết quả
-- ====================================
SELECT 'Database created successfully!' AS Status;
SELECT * FROM users;
