-- Date of birth and phone number. Both nullable: accounts created before they were collected have none
-- (registration requires the birth date; the phone number is optional, in international format +<digits>).
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
