-- =============================================================================
-- Migration: Allow Readers to Publish Books and Access Author Studio
-- =============================================================================
-- This migration updates PostgreSQL Row Level Security (RLS) policies on
-- public.books and the draft publication RPC to allow any authenticated user
-- (both READER and AUTHOR roles) to create and manage their own publications.
--
-- Security guarantees:
-- 1. Users can ONLY create, update, or delete books where auth.uid() = author_id.
-- 2. Users CANNOT modify or delete books belonging to any other user.
-- 3. Published books remain readable by everyone; drafts are owner-only.
-- 4. No service_role key exposure, no broad grants, RLS remains strictly enforced.
-- =============================================================================

-- 1. Update RLS policies on public.books
ALTER TABLE public.books ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Verified authors can insert own books" ON public.books;
DROP POLICY IF EXISTS "Users can insert own books" ON public.books;
CREATE POLICY "Users can insert own books"
    ON public.books FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Authors can update own books" ON public.books;
DROP POLICY IF EXISTS "Users can update own books" ON public.books;
CREATE POLICY "Users can update own books"
    ON public.books FOR UPDATE
    TO authenticated
    USING (auth.uid() = author_id)
    WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Authors can delete own books" ON public.books;
DROP POLICY IF EXISTS "Users can delete own books" ON public.books;
CREATE POLICY "Users can delete own books"
    ON public.books FOR DELETE
    TO authenticated
    USING (auth.uid() = author_id);

-- 2. Update RPC function to allow readers to publish books
CREATE OR REPLACE FUNCTION public.create_or_update_draft_publication(
    p_book_id TEXT,
    p_title TEXT,
    p_author_name TEXT,
    p_description TEXT,
    p_genre TEXT,
    p_language TEXT,
    p_price NUMERIC,
    p_is_free BOOLEAN,
    p_total_pages INTEGER,
    p_preview_pages INTEGER
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
    v_profile RECORD;
    v_book RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    -- Retrieve user profile (both readers and authors are allowed)
    SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;

    -- Upsert book record in DRAFT / UPLOADING state
    INSERT INTO public.books (
        id, author_id, title, author_name, description, genre, language,
        price, is_free, total_pages, preview_pages, sample_pages_count,
        status, copies_sold, rating, created_at, updated_at
    )
    VALUES (
        p_book_id,
        v_user_id,
        p_title,
        COALESCE(NULLIF(p_author_name, ''), v_profile.name, 'Independent Author'),
        p_description,
        p_genre,
        COALESCE(NULLIF(p_language, ''), 'English'),
        CASE WHEN p_is_free THEN 0.0 ELSE GREATEST(0.0, p_price) END,
        p_is_free,
        p_total_pages,
        p_preview_pages,
        p_preview_pages,
        'UPLOADING',
        0,
        5.0,
        now(),
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        title = EXCLUDED.title,
        author_name = EXCLUDED.author_name,
        description = EXCLUDED.description,
        genre = EXCLUDED.genre,
        language = EXCLUDED.language,
        price = EXCLUDED.price,
        is_free = EXCLUDED.is_free,
        total_pages = EXCLUDED.total_pages,
        preview_pages = EXCLUDED.preview_pages,
        sample_pages_count = EXCLUDED.sample_pages_count,
        status = 'UPLOADING',
        updated_at = now()
    WHERE public.books.author_id = v_user_id
    RETURNING * INTO v_book;

    RETURN jsonb_build_object(
        'success', true,
        'book_id', v_book.id,
        'status', v_book.status
    );
END;
$$;

ALTER FUNCTION public.create_or_update_draft_publication(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, NUMERIC, BOOLEAN, INTEGER, INTEGER) OWNER TO postgres;
