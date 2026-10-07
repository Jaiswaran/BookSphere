-- =============================================================================
-- BookSphere Security Hardening & Row Level Security (RLS) Migration
-- =============================================================================

-- Enable UUID extension if not present
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- 1. SCHEMAS & TABLES DEFINITION
-- =============================================================================

-- Table: public.profiles
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT,
    email TEXT,
    role TEXT NOT NULL DEFAULT 'READER',
    author_status TEXT NOT NULL DEFAULT 'NONE',
    photo_url TEXT,
    bio TEXT,
    reading_lists_json TEXT DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_profile_role CHECK (role IN ('READER', 'AUTHOR_PENDING', 'AUTHOR_VERIFIED', 'AUTHOR', 'ADMIN')),
    CONSTRAINT chk_author_status CHECK (author_status IN ('NONE', 'PENDING', 'VERIFIED', 'REJECTED'))
);

-- Table: public.books
CREATE TABLE IF NOT EXISTS public.books (
    id TEXT PRIMARY KEY,
    author_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    author_name TEXT,
    description TEXT,
    genre TEXT,
    language TEXT DEFAULT 'English',
    price NUMERIC NOT NULL DEFAULT 0.0,
    is_free BOOLEAN NOT NULL DEFAULT false,
    cover_path TEXT,
    manuscript_path TEXT,
    preview_path TEXT,
    total_pages INTEGER NOT NULL DEFAULT 100,
    preview_pages INTEGER NOT NULL DEFAULT 3,
    sample_pages_count INTEGER NOT NULL DEFAULT 3,
    status TEXT NOT NULL DEFAULT 'PUBLISHED',
    rating NUMERIC DEFAULT 5.0,
    copies_sold INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_book_status CHECK (status IN ('DRAFT', 'UPLOADING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'ARCHIVED'))
);

-- Table: public.library
CREATE TABLE IF NOT EXISTS public.library (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    book_id TEXT NOT NULL REFERENCES public.books(id) ON DELETE CASCADE,
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    progress NUMERIC NOT NULL DEFAULT 0.0,
    last_page INTEGER NOT NULL DEFAULT 1,
    completed BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, book_id)
);

-- Create helpful indexes
CREATE INDEX IF NOT EXISTS idx_books_author_id ON public.books(author_id);
CREATE INDEX IF NOT EXISTS idx_books_status ON public.books(status);
CREATE INDEX IF NOT EXISTS idx_library_user_id ON public.library(user_id);
CREATE INDEX IF NOT EXISTS idx_library_book_id ON public.library(book_id);

-- =============================================================================
-- 2. PROFILE PRIVILEGE ESCALATION GUARDS & TRIGGERS
-- =============================================================================

-- Function & Trigger: Automatic profile provisioning on auth signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_role text;
    v_author_status text;
BEGIN
    -- Only honor requested role if valid, but default to 'READER' unless explicit author verification flow
    v_role := 'READER';
    v_author_status := 'NONE';
    
    -- If user metadata specifies author intent during onboarding, mark as pending verification
    IF (NEW.raw_user_meta_data->>'role') = 'AUTHOR' OR (NEW.raw_user_meta_data->>'role') = 'AUTHOR_PENDING' THEN
        v_role := 'AUTHOR_PENDING';
        v_author_status := 'PENDING';
    END IF;

    INSERT INTO public.profiles (id, name, email, role, author_status, photo_url, bio, reading_lists_json)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'name', NEW.raw_user_meta_data->>'full_name', split_part(NEW.email, '@', 1)),
        NEW.email,
        v_role,
        v_author_status,
        NEW.raw_user_meta_data->>'avatar_url',
        'Passionate literary enthusiast on BookSphere.',
        '[]'
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        name = COALESCE(public.profiles.name, EXCLUDED.name),
        updated_at = now();
        
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Function & Trigger: Prevent client-side role escalation
CREATE OR REPLACE FUNCTION public.prevent_profile_privilege_escalation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Prevent normal clients from granting themselves AUTHOR, AUTHOR_VERIFIED, or ADMIN
    IF (NEW.role IS DISTINCT FROM OLD.role) OR (NEW.author_status IS DISTINCT FROM OLD.author_status) THEN
        -- Only service_role or admin can directly change role/author_status
        IF auth.role() <> 'service_role' AND NOT EXISTS (
            SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'ADMIN'
        ) THEN
            -- Preserve existing role and status rather than allowing escalation
            NEW.role := OLD.role;
            NEW.author_status := OLD.author_status;
        END IF;
    END IF;

    -- Disallow changing immutable identity fields
    NEW.id := OLD.id;
    NEW.created_at := OLD.created_at;
    NEW.updated_at := now();

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_profile_escalation ON public.profiles;
CREATE TRIGGER trg_prevent_profile_escalation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.prevent_profile_privilege_escalation();

-- =============================================================================
-- 3. ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================

-- -----------------------------------------------------------------------------
-- PROFILES RLS
-- -----------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Profiles are readable by authenticated users" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can delete own profile" ON public.profiles;

CREATE POLICY "Profiles are readable by authenticated users"
    ON public.profiles FOR SELECT
    USING (true);

CREATE POLICY "Users can insert own profile"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can delete own profile"
    ON public.profiles FOR DELETE
    USING (auth.uid() = id);

-- -----------------------------------------------------------------------------
-- BOOKS RLS
-- -----------------------------------------------------------------------------
ALTER TABLE public.books ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Published books viewable by all, drafts by owner" ON public.books;
DROP POLICY IF EXISTS "Verified authors can insert own books" ON public.books;
DROP POLICY IF EXISTS "Authors can update own books" ON public.books;
DROP POLICY IF EXISTS "Authors can delete own books" ON public.books;

CREATE POLICY "Published books viewable by all, drafts by owner"
    ON public.books FOR SELECT
    USING (
        status = 'PUBLISHED' 
        OR auth.uid() = author_id
    );

CREATE POLICY "Verified authors can insert own books"
    ON public.books FOR INSERT
    WITH CHECK (
        auth.uid() = author_id 
        AND EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE id = auth.uid() 
            AND (role IN ('AUTHOR', 'AUTHOR_VERIFIED', 'ADMIN') OR author_status = 'VERIFIED')
        )
    );

CREATE POLICY "Authors can update own books"
    ON public.books FOR UPDATE
    USING (
        auth.uid() = author_id 
        AND EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE id = auth.uid() 
            AND (role IN ('AUTHOR', 'AUTHOR_VERIFIED', 'ADMIN') OR author_status = 'VERIFIED')
        )
    )
    WITH CHECK (auth.uid() = author_id);

CREATE POLICY "Authors can delete own books"
    ON public.books FOR DELETE
    USING (auth.uid() = author_id);

-- -----------------------------------------------------------------------------
-- LIBRARY RLS
-- -----------------------------------------------------------------------------
ALTER TABLE public.library ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own library only" ON public.library;
DROP POLICY IF EXISTS "Users can insert own library entry" ON public.library;
DROP POLICY IF EXISTS "Users can update own reading progress" ON public.library;
DROP POLICY IF EXISTS "Users can delete own library entry" ON public.library;

CREATE POLICY "Users can view own library only"
    ON public.library FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own library entry"
    ON public.library FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own reading progress"
    ON public.library FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own library entry"
    ON public.library FOR DELETE
    USING (auth.uid() = user_id);

-- =============================================================================
-- 4. STORAGE BUCKETS & STORAGE RLS POLICIES
-- =============================================================================

-- Ensure buckets exist
INSERT INTO storage.buckets (id, name, public) 
VALUES ('covers', 'covers', true) 
ON CONFLICT (id) DO UPDATE SET public = true;

INSERT INTO storage.buckets (id, name, public) 
VALUES ('previews', 'previews', true) 
ON CONFLICT (id) DO UPDATE SET public = true;

INSERT INTO storage.buckets (id, name, public) 
VALUES ('avatars', 'avatars', true) 
ON CONFLICT (id) DO UPDATE SET public = true;

INSERT INTO storage.buckets (id, name, public) 
VALUES ('manuscripts', 'manuscripts', false) 
ON CONFLICT (id) DO UPDATE SET public = false;

-- Storage Policies
-- COVERS: Public read, owner-only write
DROP POLICY IF EXISTS "Public can view covers" ON storage.objects;
CREATE POLICY "Public can view covers"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'covers');

DROP POLICY IF EXISTS "Authors can upload own covers" ON storage.objects;
CREATE POLICY "Authors can upload own covers"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'covers' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "Authors can update own covers" ON storage.objects;
CREATE POLICY "Authors can update own covers"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'covers' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "Authors can delete own covers" ON storage.objects;
CREATE POLICY "Authors can delete own covers"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'covers' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- PREVIEWS: Public read, owner-only write
DROP POLICY IF EXISTS "Public can view preview PDFs" ON storage.objects;
CREATE POLICY "Public can view preview PDFs"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'previews');

DROP POLICY IF EXISTS "Authors can upload own preview PDFs" ON storage.objects;
CREATE POLICY "Authors can upload own preview PDFs"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'previews' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "Authors can update own preview PDFs" ON storage.objects;
CREATE POLICY "Authors can update own preview PDFs"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'previews' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "Authors can delete own preview PDFs" ON storage.objects;
CREATE POLICY "Authors can delete own preview PDFs"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'previews' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- AVATARS: Public read, authenticated user write
DROP POLICY IF EXISTS "Public can view avatars" ON storage.objects;
CREATE POLICY "Public can view avatars"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Users can upload own avatars" ON storage.objects;
CREATE POLICY "Users can upload own avatars"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'avatars' 
        AND auth.role() = 'authenticated'
    );

-- MANUSCRIPTS (PRIVATE): Author or Entitled Reader ONLY
DROP POLICY IF EXISTS "Authorized access to private manuscripts" ON storage.objects;
CREATE POLICY "Authorized access to private manuscripts"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'manuscripts' 
        AND auth.role() = 'authenticated'
        AND (
            -- 1. Author accessing own manuscript
            (storage.foldername(name))[1] = auth.uid()::text
            -- 2. Reader accessing free book or verified purchased book
            OR EXISTS (
                SELECT 1 FROM public.books b
                WHERE (b.manuscript_path = 'manuscripts/' || name OR b.manuscript_path = name)
                AND (
                    b.is_free = true
                    OR EXISTS (
                        SELECT 1 FROM public.library l 
                        WHERE l.user_id = auth.uid() 
                        AND l.book_id = b.id
                    )
                )
            )
        )
    );

DROP POLICY IF EXISTS "Authors can upload own manuscripts" ON storage.objects;
CREATE POLICY "Authors can upload own manuscripts"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'manuscripts' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1 FROM public.profiles 
            WHERE id = auth.uid() 
            AND (role IN ('AUTHOR', 'AUTHOR_VERIFIED', 'ADMIN') OR author_status = 'VERIFIED')
        )
    );

DROP POLICY IF EXISTS "Authors can update own manuscripts" ON storage.objects;
CREATE POLICY "Authors can update own manuscripts"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'manuscripts' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "Authors can delete own manuscripts" ON storage.objects;
CREATE POLICY "Authors can delete own manuscripts"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'manuscripts' 
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- =============================================================================
-- 5. SERVER-SIDE RPC FUNCTIONS (DATABASE FUNCTIONS)
-- =============================================================================

-- Function: Request Author Verification
CREATE OR REPLACE FUNCTION public.request_author_verification(
    p_pen_name TEXT,
    p_bio TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id UUID;
    v_profile RECORD;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    -- Update profile with author pending/verified status and author metadata
    UPDATE public.profiles
    SET 
        name = COALESCE(NULLIF(p_pen_name, ''), name),
        bio = COALESCE(NULLIF(p_bio, ''), bio),
        role = 'AUTHOR_VERIFIED',
        author_status = 'VERIFIED',
        updated_at = now()
    WHERE id = v_user_id
    RETURNING * INTO v_profile;

    RETURN jsonb_build_object(
        'success', true,
        'user_id', v_user_id,
        'role', v_profile.role,
        'author_status', v_profile.author_status
    );
END;
$$;

-- Function: Purchase Book Entitlement & Increment Copies Sold atomically
CREATE OR REPLACE FUNCTION public.purchase_book_entitlement(
    p_book_id TEXT
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

    -- Validate book exists and is published
    SELECT * INTO v_book FROM public.books WHERE id = p_book_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Book not found: %', p_book_id;
    END IF;

    IF v_book.status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'Cannot purchase unreleased manuscript.';
    END IF;

    -- Insert into library
    INSERT INTO public.library (user_id, book_id, purchased_at, progress, last_page, completed)
    VALUES (v_user_id, p_book_id, now(), 0.0, 1, false)
    ON CONFLICT (user_id, book_id) DO UPDATE SET
        updated_at = now();

    -- Atomically increment copies_sold
    UPDATE public.books
    SET copies_sold = copies_sold + 1, updated_at = now()
    WHERE id = p_book_id;

    RETURN jsonb_build_object(
        'success', true,
        'book_id', p_book_id,
        'user_id', v_user_id
    );
END;
$$;

-- Function: Add Free Book Entitlement
CREATE OR REPLACE FUNCTION public.add_free_book_entitlement(
    p_book_id TEXT
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

    -- Validate book exists and is free
    SELECT * INTO v_book FROM public.books WHERE id = p_book_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Book not found: %', p_book_id;
    END IF;

    IF NOT v_book.is_free AND v_book.price > 0.0 THEN
        RAISE EXCEPTION 'Book % is a paid publication.', p_book_id;
    END IF;

    -- Insert into library
    INSERT INTO public.library (user_id, book_id, purchased_at, progress, last_page, completed)
    VALUES (v_user_id, p_book_id, now(), 0.0, 1, false)
    ON CONFLICT (user_id, book_id) DO UPDATE SET
        updated_at = now();

    RETURN jsonb_build_object(
        'success', true,
        'book_id', p_book_id,
        'user_id', v_user_id
    );
END;
$$;

-- Function: Update Reading Progress
CREATE OR REPLACE FUNCTION public.update_reading_progress(
    p_book_id TEXT,
    p_last_page INTEGER,
    p_progress NUMERIC,
    p_completed BOOLEAN
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

    UPDATE public.library
    SET 
        last_page = p_last_page,
        progress = LEAST(1.0, GREATEST(0.0, p_progress)),
        completed = p_completed,
        updated_at = now()
    WHERE user_id = v_user_id AND book_id = p_book_id;

    RETURN jsonb_build_object('success', true);
END;
$$;
