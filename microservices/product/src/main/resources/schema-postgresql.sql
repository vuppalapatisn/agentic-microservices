CREATE TABLE IF NOT EXISTS product (
  id BIGSERIAL PRIMARY KEY,
  product_id INT NOT NULL UNIQUE,
  name VARCHAR(250) NOT NULL,
  description VARCHAR(250) NOT NULL,
  price NUMERIC(10,2),
  category VARCHAR(120),
  brand VARCHAR(120),
  stock_quantity INT DEFAULT 0,
  rating NUMERIC(2,1)
);

CREATE INDEX IF NOT EXISTS idx_product_category ON product (category);
