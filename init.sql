-- Drop tables if they exist (for clean restart)
DROP TABLE IF EXISTS auth_tokens CASCADE;
DROP TABLE IF EXISTS rating_likes CASCADE;
DROP TABLE IF EXISTS favorites CASCADE;
DROP TABLE IF EXISTS ratings CASCADE;
DROP TABLE IF EXISTS media_entries CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Enable UUID extension for PostgreSQL (for default UUID generation)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Media entries table
CREATE TABLE media_entries (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('movie', 'series', 'game')),
    release_year INT,
    genres VARCHAR(255), -- comma-separated list
    age_restriction VARCHAR(10),
    creator_id VARCHAR(36) REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Ratings table
CREATE TABLE ratings (
    id VARCHAR(36) PRIMARY KEY,
    media_id VARCHAR(36) REFERENCES media_entries(id) ON DELETE CASCADE,
    user_id VARCHAR(36) REFERENCES users(id),
    stars INT NOT NULL CHECK (stars >= 1 AND stars <= 5),
    comment TEXT,
    is_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(media_id, user_id) -- One rating per user per media
);

-- Rating likes table
CREATE TABLE rating_likes (
    rating_id VARCHAR(36) REFERENCES ratings(id) ON DELETE CASCADE,
    user_id VARCHAR(36) REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY(rating_id, user_id)
);

-- Favorites table
CREATE TABLE favorites (
    user_id VARCHAR(36) REFERENCES users(id),
    media_id VARCHAR(36) REFERENCES media_entries(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY(user_id, media_id)
);

-- Auth tokens table
CREATE TABLE auth_tokens (
    token VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(36) REFERENCES users(id) UNIQUE,
    created_at TIMESTAMP DEFAULT NOW()
);


-- Insert some test data with pre-generated UUIDs
-- User IDs
-- testuser1: 01900000-0000-7000-8000-000000000001
-- testuser2: 01900000-0000-7000-8000-000000000002
-- admin: 01900000-0000-7000-8000-000000000003

INSERT INTO users (id, username, password_hash) VALUES
    ('01900000-0000-7000-8000-000000000001', 'testuser1', '$2a$12$KIXxPfAQKPbPbpPPpPpPpeZZ0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0'), -- password: test123
    ('01900000-0000-7000-8000-000000000002', 'testuser2', '$2a$12$KIXxPfAQKPbPbpPPpPpPpeZZ0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0'), -- password: test123
    ('01900000-0000-7000-8000-000000000003', 'admin', '$2a$12$KIXxPfAQKPbPbpPPpPpPpeZZ0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0'); -- password: test123

-- Media IDs
-- The Matrix: 01900000-0000-7000-8000-000000000101
-- Breaking Bad: 01900000-0000-7000-8000-000000000102
-- The Witcher 3: 01900000-0000-7000-8000-000000000103
-- Inception: 01900000-0000-7000-8000-000000000104
-- The Last of Us: 01900000-0000-7000-8000-000000000105

INSERT INTO media_entries (id, title, description, media_type, release_year, genres, age_restriction, creator_id) VALUES
    ('01900000-0000-7000-8000-000000000101', 'The Matrix', 'A hacker discovers reality is a simulation', 'movie', 1999, 'Sci-Fi,Action', 'R', '01900000-0000-7000-8000-000000000001'),
    ('01900000-0000-7000-8000-000000000102', 'Breaking Bad', 'A chemistry teacher becomes a drug lord', 'series', 2008, 'Drama,Crime', 'TV-MA', '01900000-0000-7000-8000-000000000001'),
    ('01900000-0000-7000-8000-000000000103', 'The Witcher 3', 'Open-world RPG in a fantasy setting', 'game', 2015, 'RPG,Fantasy', 'M', '01900000-0000-7000-8000-000000000002'),
    ('01900000-0000-7000-8000-000000000104', 'Inception', 'A thief who enters dreams', 'movie', 2010, 'Sci-Fi,Thriller', 'PG-13', '01900000-0000-7000-8000-000000000002'),
    ('01900000-0000-7000-8000-000000000105', 'The Last of Us', 'Post-apocalyptic survival game', 'game', 2013, 'Action,Adventure', 'M', '01900000-0000-7000-8000-000000000003');

-- Rating IDs
-- Rating 1: 01900000-0000-7000-8000-000000000201
-- Rating 2: 01900000-0000-7000-8000-000000000202
-- Rating 3: 01900000-0000-7000-8000-000000000203
-- Rating 4: 01900000-0000-7000-8000-000000000204
-- Rating 5: 01900000-0000-7000-8000-000000000205

INSERT INTO ratings (id, media_id, user_id, stars, comment, is_confirmed) VALUES
    ('01900000-0000-7000-8000-000000000201', '01900000-0000-7000-8000-000000000101', '01900000-0000-7000-8000-000000000002', 5, 'Amazing movie, mind-blowing concept!', true),
    ('01900000-0000-7000-8000-000000000202', '01900000-0000-7000-8000-000000000101', '01900000-0000-7000-8000-000000000003', 4, 'Good action scenes', true),
    ('01900000-0000-7000-8000-000000000203', '01900000-0000-7000-8000-000000000102', '01900000-0000-7000-8000-000000000001', 5, 'Best series ever!', true),
    ('01900000-0000-7000-8000-000000000204', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000001', 5, 'Incredible RPG experience', true),
    ('01900000-0000-7000-8000-000000000205', '01900000-0000-7000-8000-000000000104', '01900000-0000-7000-8000-000000000003', 4, 'Complex but fascinating', true);

INSERT INTO rating_likes (rating_id, user_id) VALUES
    ('01900000-0000-7000-8000-000000000201', '01900000-0000-7000-8000-000000000001'),
    ('01900000-0000-7000-8000-000000000201', '01900000-0000-7000-8000-000000000003'),
    ('01900000-0000-7000-8000-000000000203', '01900000-0000-7000-8000-000000000002');

INSERT INTO favorites (user_id, media_id) VALUES
    ('01900000-0000-7000-8000-000000000001', '01900000-0000-7000-8000-000000000101'),
    ('01900000-0000-7000-8000-000000000001', '01900000-0000-7000-8000-000000000103'),
    ('01900000-0000-7000-8000-000000000002', '01900000-0000-7000-8000-000000000102'),
    ('01900000-0000-7000-8000-000000000003', '01900000-0000-7000-8000-000000000104');