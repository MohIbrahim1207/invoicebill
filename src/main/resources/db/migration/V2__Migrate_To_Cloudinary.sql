-- Migration script to support Cloudinary URLs instead of file paths
-- Created on May 26, 2026 for InvoiceHub Cloudinary migration

-- Add new URL columns to Invoice table
ALTER TABLE invoice ADD COLUMN file_url VARCHAR(1000);

-- Add new URL columns to VendorTicket table
ALTER TABLE vendor_ticket
ADD COLUMN invoice_file_url VARCHAR(1000),
ADD COLUMN supporting_document_url VARCHAR(1000),
ADD COLUMN tax_document_url VARCHAR(1000),
ADD COLUMN po_copy_url VARCHAR(1000),
ADD COLUMN delivery_note_url VARCHAR(1000),
ADD COLUMN other_document_url VARCHAR(1000);

-- Add new URL columns to AppUser table
ALTER TABLE app_user
ADD COLUMN gst_document_url VARCHAR(1000),
ADD COLUMN company_document_url VARCHAR(1000),
ADD COLUMN supporting_document_url VARCHAR(1000),
ADD COLUMN profile_image_url VARCHAR(1000),
ADD COLUMN company_logo_url VARCHAR(1000);

-- Create indexes on URL columns for faster lookups
CREATE INDEX idx_invoice_file_url ON invoice(file_url);
CREATE INDEX idx_vendor_ticket_invoice_url ON vendor_ticket(invoice_file_url);
CREATE INDEX idx_app_user_profile_image ON app_user(profile_image_url);

