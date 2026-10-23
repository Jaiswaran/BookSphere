-- =============================================================================
-- Allow authenticated readers to publish their own books safely.
--
-- The application supports reader publishing, but the older Storage policies
-- allowed manuscript/preview uploads only for profiles marked as authors.
-- This caused a publication to fail at the Storage stage even when the book
-- draft belonged to the authenticated user.
--
-- These policies require a matching UPLOADING/FAILED book owned by auth.uid()
-- and a storage path beginning with auth.uid()/book_id/. They do not grant
-- access to upload into another user's book folder or read private manuscripts.
-- =============================================================================

DROP POLICY IF EXISTS "Authors can upload own manuscripts" ON storage.objects;
DROP POLICY IF EXISTS "Book owners can upload own manuscripts" ON storage.objects;
CREATE POLICY "Book owners can upload own manuscripts"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'manuscripts'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    );

DROP POLICY IF EXISTS "Authors can update own manuscripts" ON storage.objects;
DROP POLICY IF EXISTS "Book owners can update own manuscripts" ON storage.objects;
CREATE POLICY "Book owners can update own manuscripts"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'manuscripts'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    )
    WITH CHECK (
        bucket_id = 'manuscripts'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    );

DROP POLICY IF EXISTS "Authors can upload own preview PDFs" ON storage.objects;
DROP POLICY IF EXISTS "Book owners can upload own preview PDFs" ON storage.objects;
CREATE POLICY "Book owners can upload own preview PDFs"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'previews'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    );

DROP POLICY IF EXISTS "Authors can update own preview PDFs" ON storage.objects;
DROP POLICY IF EXISTS "Book owners can update own preview PDFs" ON storage.objects;
CREATE POLICY "Book owners can update own preview PDFs"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'previews'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    )
    WITH CHECK (
        bucket_id = 'previews'
        AND (storage.foldername(name))[1] = auth.uid()::text
        AND EXISTS (
            SELECT 1
            FROM public.books b
            WHERE b.id = (storage.foldername(name))[2]
              AND b.author_id = auth.uid()
              AND b.status IN ('UPLOADING', 'FAILED')
        )
    );
