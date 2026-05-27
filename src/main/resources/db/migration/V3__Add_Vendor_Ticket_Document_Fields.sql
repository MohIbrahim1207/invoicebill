-- Add vendor ticket document fields for Cloudinary-backed uploads
ALTER TABLE vendor_ticket
    ADD COLUMN document_url VARCHAR(1000),
    ADD COLUMN document_public_id VARCHAR(255);

