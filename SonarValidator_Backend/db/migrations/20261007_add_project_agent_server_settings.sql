ALTER TABLE project
    ADD COLUMN IF NOT EXISTS management_server_ip varchar(255);

ALTER TABLE project
    ADD COLUMN IF NOT EXISTS management_server_port integer;
