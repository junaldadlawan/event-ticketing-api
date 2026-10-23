-- The scanner app (Android APK) the check-in staff install. Admins upload new versions; the file itself lives in the file storage (local disk in dev,
-- R2 in prod) under storage_key, this table keeps the history. Rows are soft-deleted (deleted_at): the history stays, the file is removed.
CREATE TABLE scanner_app_releases (
    id            UUID          PRIMARY KEY,
    version_name  VARCHAR(50)   NOT NULL,
    version_code  INTEGER       NOT NULL CHECK (version_code > 0),
    release_notes VARCHAR(2000),
    storage_key   VARCHAR(200)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    sha256        VARCHAR(64)   NOT NULL,
    created_by    VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_by    VARCHAR(255),
    updated_at    TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ
);

-- one live upload per version code: the app compares version codes to decide whether it is outdated
CREATE UNIQUE INDEX uq_scanner_app_releases_version_code ON scanner_app_releases (version_code) WHERE deleted_at IS NULL;
