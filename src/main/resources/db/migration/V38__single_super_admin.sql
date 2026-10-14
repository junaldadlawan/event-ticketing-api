-- At most one SUPER_ADMIN account, ever (the highest user). A partial unique index on the role value: a second row
-- with role 'SUPER_ADMIN' is refused by the database whatever the application does. The account is not creatable or
-- assignable through the API; it is inserted by hand (see db/seed/demo-data.sql for a demo one).
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_single_super_admin ON users (role) WHERE role = 'SUPER_ADMIN';
