CREATE TABLE product (
  id INT AUTO_INCREMENT  PRIMARY KEY,
  product_id INT NOT NULL,
  name VARCHAR(250) NOT NULL,
  description VARCHAR(250) NOT NULL,
  price DECIMAL(10,2) DEFAULT NULL,
  category VARCHAR(120) DEFAULT NULL,
  brand VARCHAR(120) DEFAULT NULL,
  stock_quantity INT DEFAULT 0,
  rating DECIMAL(2,1) DEFAULT NULL
);

CREATE INDEX idx_product_product_id ON product (product_id);
CREATE INDEX idx_product_category ON product (category);
