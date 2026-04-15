-- BookManagementService Database Setup
-- Run this script to initialize the database

-- Create extension for vector support
CREATE EXTENSION IF NOT EXISTS vector;

-- Books table
CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    cover_embedding vector(768),
    metadata jsonb,
    created_at timestamp DEFAULT NOW(),
    updated_at timestamp DEFAULT NOW()
);

-- Pages table (one book can have many pages)
CREATE TABLE IF NOT EXISTS pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL,
    page_embedding vector(768),
    has_text BOOLEAN DEFAULT false,
    created_at timestamp DEFAULT NOW(),
    UNIQUE(book_id, page_number)
);

-- IVFFlat index for book cover similarity search
CREATE INDEX IF NOT EXISTS idx_books_cover_embedding
ON books USING ivfflat (cover_embedding vector_cosine_ops) WITH (lists = 100);

-- IVFFlat index for page similarity search
CREATE INDEX IF NOT EXISTS idx_pages_page_embedding
ON pages USING ivfflat (page_embedding vector_cosine_ops) WITH (lists = 100);

-- Index for book_id lookups
CREATE INDEX IF NOT EXISTS idx_pages_book_id ON pages(book_id);

-- Example queries:

-- Register a new book
-- INSERT INTO books (id, title, cover_embedding, metadata)
-- VALUES ('uuid', 'Book Title', '[0.1, 0.2, ...]'::vector, '{"author": "Author Name"}');

-- Add a page to a book
-- INSERT INTO pages (id, book_id, page_number, page_embedding, has_text)
-- VALUES ('uuid', 'book_uuid', 1, '[0.1, 0.2, ...]'::vector, true);

-- Find similar books
-- SELECT id, title, 1 - (cover_embedding <=> '[query]'::vector) AS similarity
-- FROM books WHERE cover_embedding IS NOT NULL
-- ORDER BY cover_embedding <=> '[query]'::vector LIMIT 5;

-- Find similar pages within a book
-- SELECT id, page_number, 1 - (page_embedding <=> '[query]'::vector) AS similarity
-- FROM pages WHERE book_id = 'book_uuid'
-- ORDER BY page_embedding <=> '[query]'::vector LIMIT 5;
