CREATE DATABASE IF NOT EXISTS starrail_hearing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'oracle_app'@'localhost'
    IDENTIFIED BY 'change-this-password';

GRANT ALL PRIVILEGES ON starrail_hearing.* TO 'oracle_app'@'localhost';
FLUSH PRIVILEGES;
