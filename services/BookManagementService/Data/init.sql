-- BookManagementService Database Setup
-- Run this script to initialize the database

-- Create extension for vector support
CREATE EXTENSION IF NOT EXISTS vector;

-- Create database (run as superuser)
-- CREATE DATABASE bookdb;

-- Connect to bookdb and run:
CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    cover_embedding vector(768),
    metadata jsonb,
    created_at timestamp DEFAULT NOW(),
    updated_at timestamp DEFAULT NOW()
);

-- Create IVFFlat index for cosine similarity search
-- This significantly speeds up similarity queries on large datasets
CREATE INDEX IF NOT EXISTS idx_books_cover_embedding
ON books USING ivfflat (cover_embedding vector_cosine_ops) WITH (lists = 100);

-- Example queries:

-- Register a new book (embedding would be provided by the service)
-- INSERT INTO books (title, cover_embedding, metadata)
-- VALUES ('Book Title', '[0.1, 0.2, ...]'::vector, '{"author": "Author Name"}');

-- Find similar books using cosine similarity
-- SELECT id, title, 1 - (cover_embedding <=> '[query_embedding]'::vector) AS similarity
-- FROM books
-- WHERE cover_embedding IS NOT NULL
-- ORDER BY cover_embedding <=> '[query_embedding]'::vector
-- LIMIT 5;
