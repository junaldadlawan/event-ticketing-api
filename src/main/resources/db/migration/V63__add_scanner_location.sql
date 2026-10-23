-- BR-CHECKIN: where a scanner device stands (e.g. "Gate A"), and where each scan was made.
ALTER TABLE scanner_devices ADD COLUMN location VARCHAR(100);

-- A snapshot at scan time: moving a device to another gate later never rewrites past records.
ALTER TABLE check_in_records ADD COLUMN scanner_location VARCHAR(100);
