-- Common_TestData (Excel sheet: "Common_TestData")
CREATE TABLE IF NOT EXISTS common_testdata (
  test_id           VARCHAR(32) PRIMARY KEY,
  url               VARCHAR(512),
  username          VARCHAR(128),
  password          VARCHAR(128),
  browser           VARCHAR(32),
  application       VARCHAR(64),
  register_date     DATE        NULL,
  register_time     TIME        NULL
);

-- Synthetic_Data (Excel sheet: "Synthetic_Data")
CREATE TABLE IF NOT EXISTS synthetic_data (
  input_id              VARCHAR(64) PRIMARY KEY,
  application           VARCHAR(64),
  test_type             VARCHAR(64),
  functionality         VARCHAR(128),
  scenario              VARCHAR(256),
  test_case             VARCHAR(256),
  category              VARCHAR(64),
  sub_category          VARCHAR(64),
  product_title         VARCHAR(128),
  country               VARCHAR(64),
  state                 VARCHAR(64),
  zip                   VARCHAR(32),
  billing_first_name    VARCHAR(64),
  billing_last_name     VARCHAR(64),
  email                 VARCHAR(128),
  city                  VARCHAR(64),
  address1              VARCHAR(256),
  phone                 VARCHAR(32)
);

-- Transactional_Data (Excel sheet: "Transactional_Data")
CREATE TABLE IF NOT EXISTS transactional_data (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  application      VARCHAR(64),
  test_type        VARCHAR(64),
  functionality    VARCHAR(128),
  scenario         VARCHAR(256),
  test_case        VARCHAR(256),
  run_id           VARCHAR(32) UNIQUE,
  execution_date   DATE,
  execution_time   TIME,
  execution_status VARCHAR(16),
  order_id         VARCHAR(32) NULL,
  order_date       DATE        NULL,
  failure_reason   TEXT        NULL,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- E2E Binding (Excel sheet: "E2E Binding")
CREATE TABLE IF NOT EXISTS e2e_binding (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  application            VARCHAR(64),
  test_type              VARCHAR(64),
  functionality          VARCHAR(128),
  scenario               VARCHAR(256),
  binding_test_case_name VARCHAR(256),
  run_id                 VARCHAR(32),
  execution_date         DATE,
  execution_time         TIME,
  execution_status       VARCHAR(16),
  order_id               VARCHAR(32),
  order_date             DATE,
  failure_reason         TEXT,
  UNIQUE KEY uk_binding (run_id, order_id, order_date)
);
