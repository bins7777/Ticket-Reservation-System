CREATE TABLE IF NOT EXISTS users (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  username VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  auto_pick_best_seat TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Default seating capacity for all showtimes
CREATE TABLE IF NOT EXISTS showtimes (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  movie_id INT NOT NULL,
  branch_id INT NOT NULL,
  show_date DATE NOT NULL,
  show_time TIME NOT NULL,
  available_seats INT NOT NULL DEFAULT 216,
  ticket_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reservations (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  confirmation_code VARCHAR(40) NOT NULL UNIQUE,
  user_id INT NOT NULL,
  showtime_id INT NOT NULL,
  payment_method VARCHAR(60) NOT NULL,
  ticket_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  ticket_count INT NOT NULL DEFAULT 0,
  ticket_subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
  food_subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
  grand_total DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reservation_seats (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reservation_id INT NOT NULL,
  showtime_id INT NOT NULL,
  seat_label VARCHAR(8) NOT NULL,
  UNIQUE KEY uniq_showtime_seat (showtime_id, seat_label)
);

CREATE TABLE IF NOT EXISTS reservation_food_items (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  reservation_id INT NOT NULL,
  item_name VARCHAR(200) NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  unit_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  line_total DECIMAL(10,2) NOT NULL DEFAULT 0
);
