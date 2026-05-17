-- Add line_id column to users table for LINE OAuth2 integration
ALTER TABLE users ADD COLUMN line_Id VARCHAR(255) DEFAULT NULL;

-- Make password column nullable for OAuth2 users who don't have passwords
ALTER TABLE users MODIFY COLUMN password VARCHAR(255) DEFAULT NULL;
