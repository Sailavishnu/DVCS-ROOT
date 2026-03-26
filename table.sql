-- Full schema for the 12-table DVCS model
-- Uses standard SQL identity columns and explicit PK/FK constraints

/* Note: This file uses SQL-standard IDENTITY columns. If your
   target RDBMS requires a different auto-increment syntax
   (SERIAL, AUTOINCREMENT, etc.), adjust types accordingly. */

CREATE TABLE Users (
    user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR2(50) NOT NULL UNIQUE,
    name VARCHAR2(25),
    password_hash CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE General_User (
    general_user_id INTEGER PRIMARY KEY,
    storage_quota NUMBER DEFAULT 0,
    files_limit INTEGER DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_general_user_user FOREIGN KEY (general_user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

CREATE TABLE Collaborator (
    collaborator_id INTEGER PRIMARY KEY,
    collab_since TIMESTAMP WITH TIME ZONE,
    collab_status VARCHAR2(50),
    total_files_shared INTEGER,
    CONSTRAINT fk_collaborator_user FOREIGN KEY (collaborator_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

CREATE TABLE Workspace (
    workspace_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_name VARCHAR2(50) NOT NULL,
    drive VARCHAR2(50),
    directory VARCHAR2(50),
    folder_name VARCHAR2(50),
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workspace_user FOREIGN KEY (created_by) REFERENCES Users(user_id) ON DELETE SET NULL
);

CREATE TABLE Workspace_Machine (
    machine_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id INTEGER NOT NULL,
    machine_name VARCHAR2(50),
    ip_address VARCHAR2(45),
    registered_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_machine_workspace FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id) ON DELETE CASCADE
);

CREATE TABLE Folders (
    folder_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id INTEGER NOT NULL,
    folder_name VARCHAR2(50) NOT NULL,
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_folder_workspace FOREIGN KEY (workspace_id) REFERENCES Workspace(workspace_id) ON DELETE CASCADE,
    CONSTRAINT fk_folder_user FOREIGN KEY (created_by) REFERENCES Users(user_id) ON DELETE SET NULL
);

CREATE TABLE Files (
    file_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folder_id INTEGER NOT NULL,
    filename VARCHAR2(50) NOT NULL,
    extension VARCHAR2(32),
    created_by INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_locked NUMBER(1) DEFAULT 0,
    locked_by INTEGER,
    locked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_file_folder FOREIGN KEY (folder_id) REFERENCES Folders(folder_id) ON DELETE CASCADE,
    CONSTRAINT fk_file_created_by FOREIGN KEY (created_by) REFERENCES Users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_file_locked_by FOREIGN KEY (locked_by) REFERENCES Users(user_id) ON DELETE SET NULL
);

-- Multi-valued attribute: file tags (one row per tag)
CREATE TABLE File_Tags (
    file_id INTEGER NOT NULL,
    tag VARCHAR2(50) NOT NULL,
    PRIMARY KEY (file_id, tag),
    CONSTRAINT fk_filetags_file FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE
);

-- Weak entity: File_Snapshots identified by (file_id, snapshot_id)
CREATE TABLE File_Snapshots (
    file_id INTEGER NOT NULL,
    snapshot_id INTEGER NOT NULL,
    content CLOB,
    snapshot_path VARCHAR2(50),
    version_number INTEGER,
    line_count INTEGER,
    word_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id, snapshot_id),
    CONSTRAINT fk_snapshot_file FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE
);

CREATE TABLE Commits (
    commit_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id INTEGER NOT NULL,
    snapshot_id INTEGER NOT NULL,
    committed_by INTEGER,
    commit_message CLOB,
    committed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_commit_snapshot FOREIGN KEY (file_id, snapshot_id) REFERENCES File_Snapshots(file_id, snapshot_id) ON DELETE CASCADE,
    CONSTRAINT fk_commit_user FOREIGN KEY (committed_by) REFERENCES Users(user_id) ON DELETE SET NULL
);

CREATE TABLE Colab_Request (
    request_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id INTEGER NOT NULL,
    requested_by INTEGER,
    requested_to INTEGER,
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR2(50),
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_colab_file FOREIGN KEY (file_id) REFERENCES Files(file_id) ON DELETE CASCADE,
    CONSTRAINT fk_colab_requested_by FOREIGN KEY (requested_by) REFERENCES Users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_colab_requested_to FOREIGN KEY (requested_to) REFERENCES Users(user_id) ON DELETE SET NULL
);

CREATE TABLE Folder_Permission (
    permission_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folder_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    permission_type VARCHAR2(50) NOT NULL,
    granted_by INTEGER,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_perm_folder FOREIGN KEY (folder_id) REFERENCES Folders(folder_id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_granted_by FOREIGN KEY (granted_by) REFERENCES Users(user_id) ON DELETE SET NULL
);

CREATE TABLE Audit_Log (
    log_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id INTEGER,
    action VARCHAR2(50) NOT NULL,
    entity_affected VARCHAR2(50),
    entity_id VARCHAR2(50),
    logged_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    machine_id INTEGER,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_machine FOREIGN KEY (machine_id) REFERENCES Workspace_Machine(machine_id) ON DELETE SET NULL
);

-- Indexes for common lookups
CREATE INDEX idx_files_folder ON Files(folder_id);
CREATE INDEX idx_folders_workspace ON Folders(workspace_id);
CREATE INDEX idx_commits_file ON Commits(file_id);

-- End of schema
