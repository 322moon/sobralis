CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_email_normalized CHECK (email = lower(trim(email)))
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    city VARCHAR(100) NOT NULL,
    public_location VARCHAR(300) NOT NULL,
    private_address VARCHAR(500),
    starts_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE participations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT participation_unique UNIQUE (event_id, user_id)
);

CREATE INDEX events_upcoming_idx ON events (status, starts_at, id);
CREATE INDEX events_city_idx ON events (lower(city), starts_at);
CREATE INDEX participations_status_idx ON participations (event_id, status);
