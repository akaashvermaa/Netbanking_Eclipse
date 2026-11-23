USE netbanking;

ALTER TABLE customer_profiles
    ADD COLUMN address_line VARCHAR(160),
    ADD COLUMN city VARCHAR(80),
    ADD COLUMN region VARCHAR(80),
    ADD COLUMN postal_code VARCHAR(12);

-- Only known fictional fixtures with their matching example.test email receive sample contact data.
INSERT INTO customer_profiles(customer_id, full_name, phone, address_line, city, region, postal_code)
SELECT u.id, fixture.full_name, CONCAT('+1 202 555 ', LPAD(99 + fixture.fixture_number, 4, '0')),
       CONCAT('Flat ', 10 + fixture.fixture_number, ', Demo Residency'), 'Pune', 'Maharashtra', '411001'
FROM JSON_TABLE(
    '["Aditi Menon", "Rahul Kumar", "Ananya Iyer", "Vikram Reddy", "Priya Nair",
      "Arjun Malhotra", "Farah Khan", "Sanjay Mehta", "Sneha Rao", "Karan Patel",
      "Aarav Shah", "Diya Nair", "Arjun Rao", "Ananya Sen", "Vivaan Mehta",
      "Isha Kapoor", "Aditya Joshi", "Sara Khan", "Rohan Das", "Meera Iyer",
      "Kabir Verma", "Riya Patel", "Dev Menon", "Tara Bose", "Neel Reddy"]',
    '$[*]' COLUMNS(fixture_number FOR ORDINALITY, full_name VARCHAR(100) PATH '$')
) AS fixture
JOIN users u ON u.email=CONCAT(REPLACE(LOWER(fixture.full_name), ' ', '.'), '@example.test')
    AND u.username IN (REPLACE(LOWER(fixture.full_name), ' ', '.'), CONCAT('demo.', REPLACE(LOWER(fixture.full_name), ' ', '.')))
    AND u.role='CUSTOMER'
ON DUPLICATE KEY UPDATE
    phone=COALESCE(NULLIF(customer_profiles.phone, ''), VALUES(phone)),
    address_line=COALESCE(NULLIF(customer_profiles.address_line, ''), VALUES(address_line)),
    city=COALESCE(NULLIF(customer_profiles.city, ''), VALUES(city)),
    region=COALESCE(NULLIF(customer_profiles.region, ''), VALUES(region)),
    postal_code=COALESCE(NULLIF(customer_profiles.postal_code, ''), VALUES(postal_code));
