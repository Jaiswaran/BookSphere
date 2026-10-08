-- =============================================================================
-- Migration: Fix handle_new_user() trigger function safely
-- =============================================================================
-- Root cause:
-- The trigger function handle_new_user() was created as SECURITY INVOKER (the default).
-- When a user registers, Supabase Auth inserts into auth.users under the supabase_auth_admin role.
-- Because handle_new_user() lacked SECURITY DEFINER, the execution context had no permission
-- to insert into public.profiles, resulting in SQLSTATE 42501 (permission denied for table profiles)
-- and returning HTTP 500 unexpected_failure ("database error saving new user").
--
-- This migration:
-- 1. Drops and recreates public.handle_new_user() with SECURITY DEFINER and SET search_path = public.
-- 2. Sets the function owner to postgres so it executes with table owner privileges.
-- 3. Inserts id, name, email, role, and photo_url into public.profiles, safely mapping
--    the role metadata to public.user_role enum ('READER', 'AUTHOR', 'ADMIN').
-- 4. Recreates the AFTER INSERT trigger on auth.users.
-- 5. Hardens Row Level Security (RLS) on public.profiles without granting open INSERT to anon/authenticated.
-- =============================================================================

-- Ensure enum user_role exists with required values
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE public.user_role AS ENUM ('READER', 'AUTHOR', 'ADMIN');
    END IF;
END$$;

-- Ensure public.profiles table exists matching the live schema
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT,
    email TEXT,
    role public.user_role NOT NULL DEFAULT 'READER'::public.user_role,
    photo_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fix trigger function
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_role public.user_role;
    v_role_text text;
    v_name text;
    v_photo_url text;
BEGIN
    -- Determine role from user metadata (case-insensitive check for AUTHOR or ADMIN)
    v_role_text := UPPER(COALESCE(NEW.raw_user_meta_data->>'role', 'READER'));
    IF v_role_text = 'AUTHOR' THEN
        v_role := 'AUTHOR'::public.user_role;
    ELSIF v_role_text = 'ADMIN' THEN
        v_role := 'ADMIN'::public.user_role;
    ELSE
        v_role := 'READER'::public.user_role;
    END IF;

    -- Determine display name
    v_name := COALESCE(
        NEW.raw_user_meta_data->>'name',
        NEW.raw_user_meta_data->>'full_name',
        split_part(NEW.email, '@', 1)
    );

    -- Determine avatar/photo url
    v_photo_url := COALESCE(
        NEW.raw_user_meta_data->>'photo_url',
        NEW.raw_user_meta_data->>'avatar_url'
    );

    -- Insert into public.profiles
    INSERT INTO public.profiles (id, name, email, role, photo_url, created_at, updated_at)
    VALUES (
        NEW.id,
        v_name,
        NEW.email,
        v_role,
        v_photo_url,
        now(),
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        name = COALESCE(public.profiles.name, EXCLUDED.name),
        photo_url = COALESCE(public.profiles.photo_url, EXCLUDED.photo_url),
        role = EXCLUDED.role,
        updated_at = now();

    RETURN NEW;
END;
$$;

-- Keep function owned by postgres
ALTER FUNCTION public.handle_new_user() OWNER TO postgres;

-- Recreate trigger on auth.users
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Maintain strict Row Level Security (RLS) on public.profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to view profiles
DROP POLICY IF EXISTS "Profiles are readable by authenticated users" ON public.profiles;
CREATE POLICY "Profiles are readable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

-- Allow anonymous users to view profiles (for published book authors in catalog)
DROP POLICY IF EXISTS "Public can view profiles" ON public.profiles;
CREATE POLICY "Public can view profiles"
    ON public.profiles FOR SELECT
    TO anon
    USING (true);

-- Allow authenticated users to update ONLY their own profile record
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Note: No INSERT policy is granted to anon or authenticated roles.
-- Profile insertion is exclusively performed through the SECURITY DEFINER trigger.
