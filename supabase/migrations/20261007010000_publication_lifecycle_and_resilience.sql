-- =============================================================================
-- BookSphere Migration: Publication Lifecycle, Resilience & Safe Retries
-- =============================================================================

-- 1. Update status constraint on public.books to support the full publication state model
ALTER TABLE public.books DROP CONSTRAINT IF EXISTS chk_book_status;
ALTER TABLE public.books ADD CONSTRAINT chk_book_status 
    CHECK (status IN ('DRAFT', 'UPLOADING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'ARCHIVED'));

-- 2. Function: Create or update draft publication safely before asset uploads
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

    -- Verify author permissions
    SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
    IF NOT FOUND OR (v_profile.role NOT IN ('AUTHOR', 'AUTHOR_VERIFIED', 'ADMIN') AND v_profile.author_status <> 'VERIFIED') THEN
        RAISE EXCEPTION 'Permission denied: Only verified authors can create publications.';
    END IF;

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

-- 3. Function: Finalize publication once all assets are safely uploaded & verified
CREATE OR REPLACE FUNCTION public.finalize_book_publication(
    p_book_id TEXT,
    p_cover_path TEXT,
    p_manuscript_path TEXT,
    p_preview_path TEXT,
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
    v_book RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    -- Update and transition to PUBLISHED
    UPDATE public.books
    SET
        cover_path = p_cover_path,
        manuscript_path = p_manuscript_path,
        preview_path = p_preview_path,
        total_pages = p_total_pages,
        preview_pages = p_preview_pages,
        sample_pages_count = p_preview_pages,
        status = 'PUBLISHED',
        updated_at = now()
    WHERE id = p_book_id AND author_id = v_user_id
    RETURNING * INTO v_book;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Book not found or not owned by caller: %', p_book_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'book_id', v_book.id,
        'status', v_book.status
    );
END;
$$;

-- 4. Function: Mark publication failed safely
CREATE OR REPLACE FUNCTION public.mark_publication_failed(
    p_book_id TEXT,
    p_error_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    UPDATE public.books
    SET status = 'FAILED', updated_at = now()
    WHERE id = p_book_id AND author_id = v_user_id;

    RETURN jsonb_build_object('success', true, 'book_id', p_book_id, 'status', 'FAILED');
END;
$$;
